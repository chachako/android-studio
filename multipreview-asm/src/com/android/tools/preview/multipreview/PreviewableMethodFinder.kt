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
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.ZipFile

/**
 * Provides functions to find all preview-able methods for a given class paths.
 */
class PreviewableMethodFinder(
    private val screenshotTestDirectory: List<File>,
    private val screenshotTestJars: List<File>,
    mainDirectory: List<File>,
    mainJars: List<File>,
    dependencyJars: List<File>,
) {
    private val annotationResolver = MultipreviewAnnotationResolver(
        screenshotTestDirectory, screenshotTestJars, mainDirectory, mainJars, dependencyJars)

    private val previewMethods: MutableSet<PreviewMethod> = mutableSetOf()

    /**
     * Finds all preview-able methods.
     */
    fun findAllPreviewableMethods(): Set<PreviewMethod> {
        for (dir in screenshotTestDirectory) {
            if (!dir.exists() || !dir.isDirectory) {
                continue
            }
            dir.walkTopDown().forEach { classFile ->
                if (classFile.isFile &&
                    classFile.exists() &&
                    classFile.name.endsWith(".class", ignoreCase = true)) {
                    processTestClass(ClassReader(classFile.readBytes()))
                }
            }
        }

        for (jar in screenshotTestJars) {
            if (!jar.exists() || !jar.isFile || !jar.name.endsWith(".jar")) {
                continue
            }
            ZipFile(jar).use { zipFile ->
                zipFile.stream().filter { it.name.endsWith(".class") }.forEach {
                    zipFile.getInputStream(it).use { stream ->
                        processTestClass(ClassReader(stream.readAllBytes()))
                    }
                }
            }
        }
        return previewMethods
    }

    private fun processTestClass(classToProcess: ClassReader) {
        classToProcess.accept(object: ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int,
                methodName: String,
                methodDescriptor: String,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor {
                return object: MethodVisitor(Opcodes.ASM9) {

                    private var hasComposableAnnotation: Boolean = false
                    private var isPreviewableMethod: Boolean = false
                    private val methodPreviewParameters = mutableListOf<ParameterRepresentation>()
                    private val multipreview = MultipreviewAnnotationResolver.AnnotationDetails.MultiPreviewAnnotation()

                    override fun visitAnnotation(
                        descriptor: String,
                        visible: Boolean
                    ): AnnotationVisitor? {
                        when (descriptor) {
                            "Landroidx/compose/runtime/Composable;" -> hasComposableAnnotation = true
                            "Landroidx/compose/ui/tooling/preview/Preview;" -> {
                                isPreviewableMethod = true
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
                            "Landroidx/compose/ui/tooling/preview/Preview\$Container;" -> {
                                isPreviewableMethod = true
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
                            else -> annotationResolver.isMultipreviewAnnotation(descriptor).let { details ->
                                if (details is MultipreviewAnnotationResolver.AnnotationDetails.MultiPreviewAnnotation) {
                                    isPreviewableMethod = true
                                    multipreview.addPreviewList(details.previewList)
                                }
                            }
                        }

                        return null
                    }

                    override fun visitParameterAnnotation(
                        parameter: Int,
                        descriptor: String?,
                        visible: Boolean,
                    ): AnnotationVisitor? {
                        if (descriptor == "Landroidx/compose/ui/tooling/preview/PreviewParameter;" && methodPreviewParameters.size == parameter) {
                            return object: AnnotationVisitor(Opcodes.ASM9) {
                                private val annotationParams = mutableMapOf<String, Any?>()

                                override fun visit(name: String?, value: Any?) {
                                    if (name != null) {
                                        annotationParams[name] = value
                                    }
                                }

                                override fun visitEnd() {
                                    methodPreviewParameters.add(ParameterRepresentation(annotationParams))
                                }
                            }
                        }
                        return null
                    }


                    override fun visitEnd() {
                        if (hasComposableAnnotation && isPreviewableMethod) {
                            multipreview.previewList.forEach { previewAnnotation ->
                                previewMethods.add(PreviewMethod(MethodRepresentation("${classToProcess.className.classPathToName}.$methodName", methodPreviewParameters), previewAnnotation))
                            }
                        }
                    }
                }
            }
        }, /*parsingOptions=*/0)
    }
}

data class PreviewMethod(val method: MethodRepresentation, val preview: BaseAnnotationRepresentation)
