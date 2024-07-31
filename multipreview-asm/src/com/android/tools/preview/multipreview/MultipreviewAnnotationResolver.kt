/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.preview.multipreview

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.ZipFile

/**
 * Resolves if a given annotation class has @Preview annotation in its ancestors.
 *
 * Note that this class is *not* thread-safe.
 */
class MultipreviewAnnotationResolver(
    private val screenshotTestDirectory: List<File>,
    private val screenshotTestJars: List<File>,
    private val mainDirectory: List<File>,
    private val mainJars: List<File>,
    private val dependencyJars: List<File>,
) {
    private val resolvedMultipreviewAnnotationClasses= mutableMapOf<String, AnnotationDetails>()

    sealed class AnnotationDetails {
        data class MultiPreviewAnnotation(val previewList: MutableList<BaseAnnotationRepresentation> = mutableListOf()): AnnotationDetails() {
            fun addPreviewList(previewListToAdd: MutableList<BaseAnnotationRepresentation>) {
                previewList.addAll(previewListToAdd)
            }

            fun addPreview(preview: BaseAnnotationRepresentation) {
                previewList.add(preview)
            }
        }
        object NonMultiPreview: AnnotationDetails()
    }

    private val currentlyResolvingAnnotationClasses: MutableSet<String> = mutableSetOf()

    /**
     * Returns true if a given [annotationClassDescriptor] has a Preview annotation
     * in its ancestors, otherwise false.
     */
    fun isMultipreviewAnnotation(annotationClassDescriptor: String): AnnotationDetails? {
        if (currentlyResolvingAnnotationClasses.contains(annotationClassDescriptor)) {
            // This annotation class has a cyclic dependency.
            return AnnotationDetails.NonMultiPreview
        }

        // Note that computeIfAbsent() throws ConcurrentModificationException due to recursion.
        val annotationDetails = resolvedMultipreviewAnnotationClasses[annotationClassDescriptor]
        if (annotationDetails != null) {
            return annotationDetails
        } else {
            val resolvedAnnotationDetails = resolve(annotationClassDescriptor)
            resolvedMultipreviewAnnotationClasses[annotationClassDescriptor] = resolvedAnnotationDetails
            return resolvedAnnotationDetails
        }
    }

    private fun resolve(annotationClassDescriptor: String): AnnotationDetails {
        if (!annotationClassDescriptor.startsWith("L") ||
            !annotationClassDescriptor.endsWith(";")) {
            return AnnotationDetails.NonMultiPreview
        }

        try {
            currentlyResolvingAnnotationClasses += annotationClassDescriptor

            val relativeFilePath =
                annotationClassDescriptor.substring(
                    1,
                    annotationClassDescriptor.length - 1
                ) + ".class"

            for (dir in screenshotTestDirectory) {
                findClassInDirectory(dir, relativeFilePath)?.let {
                    return resolveAnnotationClass(it)
                }
            }

            for (jar in screenshotTestJars) {
                findClassInJar(jar, relativeFilePath)?.let {
                    return resolveAnnotationClass(it)
                }
            }

            for (dir in mainDirectory) {
                findClassInDirectory(dir, relativeFilePath)?.let {
                    return resolveAnnotationClass(it)
                }
            }

            for (jar in mainJars) {
                findClassInJar(jar, relativeFilePath)?.let {
                    return resolveAnnotationClass(it)
                }
            }

            for (jar in dependencyJars) {
                findClassInJar(jar, relativeFilePath)?.let {
                    return resolveAnnotationClass(it)
                }
            }

            return AnnotationDetails.NonMultiPreview
        } finally {
            currentlyResolvingAnnotationClasses -= annotationClassDescriptor
        }
    }

    private fun findClassInDirectory(dir: File, relativeClassPath: String): ClassReader? {
        val classFile = File(dir, relativeClassPath)
        if (classFile.isFile && classFile.exists()) {
            return ClassReader(classFile.readBytes())
        }
        return null
    }

    private fun findClassInJar(jar: File, relativeClassPath: String): ClassReader? {
        if (!jar.exists() || !jar.isFile || !jar.name.endsWith(".jar")) {
            return null
        }
        return ZipFile(jar).use { zipFile ->
            zipFile.getEntry(relativeClassPath)?.let {
                zipFile.getInputStream(it).use { stream ->
                    ClassReader(stream.readAllBytes())
                }
            }
        }
    }

    private fun resolveAnnotationClass(annotationClass: ClassReader): AnnotationDetails {
        var isThisClassMultipreviewAnnotation = false
        val multipreview = AnnotationDetails.MultiPreviewAnnotation()

        annotationClass.accept(object: ClassVisitor(Opcodes.ASM9) {
            override fun visitAnnotation(
                descriptor: String,
                visible: Boolean
            ): AnnotationVisitor? {
                val baseAnnotation = "Landroidx/compose/ui/tooling/preview/Preview;"
                val repeatedBaseAnnotation = "Landroidx/compose/ui/tooling/preview/Preview\$Container;"

                when (descriptor) {
                    baseAnnotation -> {
                        isThisClassMultipreviewAnnotation = true
                        return object: AnnotationVisitor(Opcodes.ASM9) {
                            private val parameters = mutableMapOf<String, Any?>()

                            override fun visit(name: String?, value: Any?) {
                                if (name != null) {
                                    parameters[name] = value
                                }
                            }

                            override fun visitEnd() {
                                multipreview.addPreview(BaseAnnotationRepresentation(parameters))
                            }
                        }
                    }
                    repeatedBaseAnnotation -> {
                        isThisClassMultipreviewAnnotation = true
                        return object: AnnotationVisitor(Opcodes.ASM9) {
                            override fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor {
                                return object: AnnotationVisitor(Opcodes.ASM9) {
                                    private val parameters = mutableMapOf<String, Any?>()

                                    override fun visit(name: String?, value: Any?) {
                                        if (name != null) {
                                            parameters[name] = value
                                        }
                                    }

                                    override fun visitEnd() {
                                        multipreview.addPreview(BaseAnnotationRepresentation(parameters))
                                    }
                                }
                            }

                            override fun visitArray(name: String?): AnnotationVisitor {
                                return this
                            }
                        }
                    }
                    else -> {
                        val annotationDetails = isMultipreviewAnnotation(descriptor)
                        if (annotationDetails is AnnotationDetails.MultiPreviewAnnotation) {
                            multipreview.addPreviewList(annotationDetails.previewList)
                            isThisClassMultipreviewAnnotation = true
                        }
                    }

                }

                return null
            }

        }, /*parsingOptions=*/0)

        return if (isThisClassMultipreviewAnnotation) multipreview else AnnotationDetails.NonMultiPreview
    }
}
