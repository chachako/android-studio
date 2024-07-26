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

    private val previewableMethods: MutableSet<String> = mutableSetOf()

    /**
     * Finds all preview-able methods.
     */
    fun findAllPreviewableMethods(): Set<String> {
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

        return previewableMethods
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

                    override fun visitAnnotation(
                        descriptor: String,
                        visible: Boolean
                    ): AnnotationVisitor? {
                        if (hasComposableAnnotation && isPreviewableMethod) {
                            return null
                        }

                        if (descriptor == "Landroidx/compose/runtime/Composable;") {
                            hasComposableAnnotation = true
                        } else if (annotationResolver.isMultipreviewAnnotation(descriptor)) {
                            isPreviewableMethod = true
                        }

                        return null
                    }

                    override fun visitEnd() {
                        if (hasComposableAnnotation && isPreviewableMethod) {
                            // TODO(b/345529313): This is a temporary implementation. Replace it with data class.
                            previewableMethods.add("class = ${classToProcess.className}, method = $methodName")
                        }
                    }
                }
            }
        }, /*parsingOptions=*/0)
    }
}
