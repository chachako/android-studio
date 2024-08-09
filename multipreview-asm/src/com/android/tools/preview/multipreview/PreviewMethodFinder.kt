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
import org.objectweb.asm.tree.MethodNode
import java.io.File
import java.util.zip.ZipFile

/**
 * Provides functions to find all methods with Preview annotations for a given class paths.
 */
class PreviewMethodFinder(
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
     * Finds all methods with Preview annotations.
     */
    fun findAllPreviewMethods(): Set<PreviewMethod> {
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
            val methodNodes = mutableListOf<MethodNode>()
            override fun visitMethod(
                access: Int,
                methodName: String,
                methodDescriptor: String,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor {
                return MethodNode(Opcodes.ASM9,
                    access, methodName, methodDescriptor, signature, exceptions).also {
                    methodNodes += it
                }
            }

            override fun visitEnd() {
                for (methodNode in methodNodes) {
                    var isComposableMethod = false

                    methodNode.accept(object: MethodVisitor(Opcodes.ASM9) {
                        override fun visitAnnotation(
                            descriptor: String,
                            visible: Boolean
                        ): AnnotationVisitor? {
                            isComposableMethod = isComposableMethod ||
                                (descriptor == "Landroidx/compose/runtime/Composable;")
                            return null
                        }
                    })
                    if (!isComposableMethod) {
                        continue
                    }

                    methodNode.accept(object: MethodVisitor(Opcodes.ASM9) {
                        val previewAnnotations = mutableSetOf<BaseAnnotationRepresentation>()
                        val methodPreviewParameters = mutableListOf<ParameterRepresentation>()

                        override fun visitAnnotation(
                            descriptor: String,
                            visible: Boolean
                        ): AnnotationVisitor? = annotationResolver.findAllPreviewAnnotations(
                            descriptor, previewAnnotations::addAll)

                        override fun visitParameterAnnotation(
                            parameter: Int,
                            descriptor: String,
                            visible: Boolean,
                        ): AnnotationVisitor? {
                            return when(descriptor) {
                                "Landroidx/compose/ui/tooling/preview/PreviewParameter;" -> {
                                    object: AnnotationVisitor(Opcodes.ASM9) {
                                        val annotationParams = mutableMapOf<String, Any>()

                                        override fun visit(name: String, value: Any) {
                                            annotationParams[name] = value
                                        }

                                        override fun visitEnd() {
                                            methodPreviewParameters.add(
                                                ParameterRepresentation(annotationParams))
                                        }
                                    }
                                }
                                else -> null
                            }
                        }

                        override fun visitEnd() {
                            if (previewAnnotations.isNotEmpty()) {
                                previewMethods += PreviewMethod(
                                    MethodRepresentation(
                                        "${classToProcess.className.classPathToName}.${methodNode.name}",
                                        methodPreviewParameters),
                                    previewAnnotations
                                )
                            }
                        }
                    })
                }
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    }
}
