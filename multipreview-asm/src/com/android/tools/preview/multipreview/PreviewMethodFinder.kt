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

    /**
     * Finds all methods with Preview annotations.
     */
    fun findAllPreviewMethods(): Set<PreviewMethod> {
        val previewMethods: MutableSet<PreviewMethod> = mutableSetOf()

        for (dir in screenshotTestDirectory) {
            if (!dir.exists() || !dir.isDirectory) {
                continue
            }
            dir.walkTopDown().forEach { classFile ->
                if (classFile.isFile &&
                    classFile.exists() &&
                    classFile.name.endsWith(".class", ignoreCase = true)) {
                    processTestClass(ClassReader(classFile.readBytes()), previewMethods::add)
                }
            }
        }

        for (jar in screenshotTestJars) {
            if (!jar.exists() || !jar.isFile || !jar.name.endsWith(".jar")) {
                continue
            }
            ZipFile(jar).use { zipFile ->
                zipFile.stream().filter { it.name.endsWith(".class", ignoreCase = true) }.forEach {
                    zipFile.getInputStream(it).use { stream ->
                        processTestClass(ClassReader(stream.readAllBytes()), previewMethods::add)
                    }
                }
            }
        }

        return previewMethods
    }

    private fun processTestClass(
        classToProcess: ClassReader, onPreviewMethodFound: (PreviewMethod) -> Unit) {
        classToProcess.accept(object: ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int,
                methodName: String,
                methodDescriptor: String,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor {
                val methodNode = MethodNode(Opcodes.ASM9,
                    access, methodName, methodDescriptor, signature, exceptions)
                return object: MethodVisitor(Opcodes.ASM9, methodNode) {
                    override fun visitEnd() {
                        super.visitEnd()
                        processMethod(methodNode) { previewAnnotations, methodPreviewParameters ->
                            onPreviewMethodFound(PreviewMethod(
                                MethodRepresentation(
                                    "${classToProcess.className.classPathToName}.${methodNode.name}",
                                    methodPreviewParameters),
                                previewAnnotations
                            ))
                        }
                    }
                }
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    }

    private fun processMethod(
        methodNodeToProcess: MethodNode,
        onPreviewFound: (Set<BaseAnnotationRepresentation>, List<ParameterRepresentation>) -> Unit) {
        var isComposableMethod = false
        methodNodeToProcess.accept(object : MethodVisitor(Opcodes.ASM9) {
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
            return
        }

        val previewAnnotations = mutableSetOf<BaseAnnotationRepresentation>()
        val methodPreviewParameters = mutableListOf<ParameterRepresentation>()

        methodNodeToProcess.accept(object : MethodVisitor(Opcodes.ASM9) {
            override fun visitAnnotation(
                descriptor: String,
                visible: Boolean
            ): AnnotationVisitor? = annotationResolver.findAllPreviewAnnotations(
                descriptor, previewAnnotations::addAll
            )

            override fun visitParameterAnnotation(
                parameter: Int,
                descriptor: String,
                visible: Boolean,
            ): AnnotationVisitor? = when (descriptor) {
                "Landroidx/compose/ui/tooling/preview/PreviewParameter;" -> {
                    object : AnnotationVisitor(Opcodes.ASM9) {
                        val annotationParams = mutableMapOf<String, Any>()

                        override fun visit(name: String, value: Any) {
                            annotationParams[name] = value
                        }

                        override fun visitEnd() {
                            methodPreviewParameters.add(
                                ParameterRepresentation(annotationParams)
                            )
                        }
                    }
                }

                else -> null
            }
        })

        if (previewAnnotations.isNotEmpty()) {
            onPreviewFound(previewAnnotations, methodPreviewParameters)
        }
    }
}
