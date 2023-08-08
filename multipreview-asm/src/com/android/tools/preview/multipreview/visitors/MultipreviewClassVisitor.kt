/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.preview.multipreview.visitors

import com.android.tools.preview.multipreview.AnnotationRecorder
import com.android.tools.preview.multipreview.DerivedAnnotationRepresentation
import com.android.tools.preview.multipreview.Graph
import com.android.tools.preview.multipreview.MultipreviewSettings
import com.android.tools.preview.multipreview.classPathToName
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * [ClassVisitor] that processes all the classes that might be related to the multipreview.
 * Currently, it has 2 main purposes:
 *
 * 1) For every method it delegates the job to the [AnnotatedMethodVisitor]
 * 2) For every class that is annotation class it records the class and annotations it is annotated
 * with. Those are potential derived annotations.
 */
internal class MultipreviewClassVisitor(
    private val settings: MultipreviewSettings,
    private val className: String,
    private val graph: Graph
) : ClassVisitor(Opcodes.ASM8) {
    private var isAnnotationClass: Boolean = false
    private var annotationRecorder: AnnotationRecorder? = null

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        // If this is annotation class it might be a derived annotation.
        isAnnotationClass = (access and Opcodes.ACC_ANNOTATION) != 0
        if (isAnnotationClass && name != null) {
            annotationRecorder = graph.addAnnotationNode(DerivedAnnotationRepresentation(name.classPathToName))
        }
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor? {
        return if (name == null || descriptor == null)
            super.visitMethod(access, name, descriptor, signature, exceptions)
        else
            AnnotatedMethodVisitor(settings, graph, "$className.$name")
    }

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        return annotationRecorder?.createAnnotationVisitor(descriptor, settings.baseAnnotation) ?: super.visitAnnotation(descriptor, visible)
    }
}
