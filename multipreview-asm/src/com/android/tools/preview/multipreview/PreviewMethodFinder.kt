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
import org.objectweb.asm.tree.AnnotationNode
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

    companion object {
        private const val COMPOSABLE_ANNOTATION = "Landroidx/compose/runtime/Composable;"
        private const val PREV_PARAMS_ANNOTATION = "Landroidx/compose/ui/tooling/preview/PreviewParameter;"
    }

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
            if (!jar.exists() || !jar.isFile || !jar.name.endsWith(".jar", ignoreCase = true)) {
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
        // First, we check if a method has a composable annotation.
        // This test runs very fast and the majority of methods don't have composable annotation.
        // If a method doesn't have composable annotation, no need to check further.
        if (!isComposableMethod(methodNodeToProcess)) {
            return
        }

        // This test is a bit expensive, so you should run it only for methods with composable
        // annotations.
        val previewAnnotations = findAllPreviewAnnotations(methodNodeToProcess)
        if (previewAnnotations.isEmpty()) {
            return
        }

        val methodPreviewParameters = findAllPreviewParameters(methodNodeToProcess)
        onPreviewFound(previewAnnotations, methodPreviewParameters)
    }

    private fun isComposableMethod(method: MethodNode): Boolean {
        return method.invisibleAnnotations.containsComposableAnnotation()
                || method.visibleAnnotations.containsComposableAnnotation()
    }

    private fun List<AnnotationNode>?.containsComposableAnnotation(): Boolean {
        return this?.find { it.desc == COMPOSABLE_ANNOTATION } != null
    }

    private fun findAllPreviewAnnotations(method: MethodNode): Set<BaseAnnotationRepresentation> {
        val previewAnnotations = mutableSetOf<BaseAnnotationRepresentation>()
        method.invisibleAnnotations.findAllPreviewAnnotations(previewAnnotations::addAll)
        method.visibleAnnotations.findAllPreviewAnnotations(previewAnnotations::addAll)
        return previewAnnotations
    }

    private fun List<AnnotationNode>?.findAllPreviewAnnotations(
        onFound: (Set<BaseAnnotationRepresentation>) -> Unit) {
        this?.forEach {
            annotationResolver.findAllPreviewAnnotations(it.desc, onFound)?.let { visitor ->
                it.accept(visitor)
            }
        }
    }

    private fun findAllPreviewParameters(method: MethodNode): List<ParameterRepresentation> {
        val methodPreviewParameters = mutableListOf<ParameterRepresentation>()
        method.invisibleParameterAnnotations.findAllPreviewParameters(methodPreviewParameters::add)
        method.visibleParameterAnnotations.findAllPreviewParameters(methodPreviewParameters::add)
        return methodPreviewParameters
    }

    private fun Array<List<AnnotationNode>?>?.findAllPreviewParameters(
        onFound: (ParameterRepresentation) -> Unit) {
        this?.forEach {
            it?.forEach {
                if (it.desc == PREV_PARAMS_ANNOTATION) {
                    val annotationParams = mutableMapOf<String, Any>()
                    it.accept(object : AnnotationVisitor(Opcodes.ASM9) {
                        override fun visit(name: String, value: Any) {
                            annotationParams[name] = value
                        }
                    })
                    onFound(ParameterRepresentation(annotationParams))
                }
            }
        }
    }
}
