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
import com.android.tools.preview.multipreview.BaseAnnotationRepresentation
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes

/** Records a single entry of base preview annotation. */
internal class SinglePreviewAnnotationVisitor(
    private val annotationRecorder: AnnotationRecorder,
) : AnnotationVisitor(Opcodes.ASM9) {
    private val parameters = mutableMapOf<String, Any?>()

    override fun visit(name: String?, value: Any?) {
        if (name != null) {
            parameters[name] = value
        }
    }

    override fun visitEnd() {
        annotationRecorder.recordBaseAnnotation(BaseAnnotationRepresentation( parameters))
    }
}
