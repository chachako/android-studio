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

import com.android.tools.preview.multipreview.AnnotationReferencesRecorder
import com.android.tools.preview.multipreview.Graph
import com.android.tools.preview.multipreview.MethodRepresentation
import com.android.tools.preview.multipreview.MultipreviewSettings
import com.android.tools.preview.multipreview.ParameterRepresentation
import com.android.tools.preview.multipreview.descriptorToFqcn
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * [MethodVisitor] that adds method to the [Graph] if the method is annotated and therefore can
 * potentially be annotated by base or derived annotations.
 */
internal class AnnotatedMethodVisitor(
    private val settings: MultipreviewSettings,
    private val graph: Graph,
    private val methodFqn: String,
) : MethodVisitor(Opcodes.ASM9) {
    private val parameters = mutableListOf<ParameterRepresentation>()
    private val annotationRecorder = AnnotationReferencesRecorder()

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        return annotationRecorder.createAnnotationVisitor(descriptor, settings.baseAnnotation)
            ?: super.visitAnnotation(descriptor, visible)
    }

    override fun visitParameterAnnotation(
        parameter: Int,
        descriptor: String?,
        visible: Boolean,
    ): AnnotationVisitor? {
        return if (descriptorToFqcn(descriptor) == settings.parameterAnnotation && parameters.size == parameter) {
            PreviewParameterAnnotationVisitor(parameters)
        } else {
            super.visitParameterAnnotation(parameter, descriptor, visible)
        }
    }

    override fun visitEnd() {
        // We do not record methods with no annotations
        if (annotationRecorder.baseAnnotations.isEmpty() && annotationRecorder.derivedAnnotations.isEmpty()) {
            return
        }

        graph.addMethodNode(
            MethodRepresentation(methodFqn, parameters),
            annotationRecorder
        )
    }
}
