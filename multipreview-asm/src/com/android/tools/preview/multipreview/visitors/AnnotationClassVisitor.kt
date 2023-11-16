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
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

/**
 * [ClassVisitor] that for every annotation class records the class and annotations it is annotated
 * with. Those are potential derived annotations.
 */
internal class AnnotationClassVisitor(
    private val baseAnnotation: String,
    private val annotationRecorder: AnnotationRecorder
) : ClassVisitor(Opcodes.ASM8) {
    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        return annotationRecorder.createAnnotationVisitor(descriptor, baseAnnotation) ?: super.visitAnnotation(descriptor, visible)
    }
}
