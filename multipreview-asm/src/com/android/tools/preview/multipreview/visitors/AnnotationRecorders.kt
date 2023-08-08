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
import com.android.tools.preview.multipreview.descriptorToFqcn
import org.objectweb.asm.AnnotationVisitor

/**
 * Create annotation visitor based on the annotation [descriptor]. We treat the base annotations
 * (e.g. @Preview for compose) in a special way. If it is a single annotation we use
 * [SinglePreviewAnnotationVisitor], if there are multiple base annotations they are combined into
 * a "container" that should be processed differently with [RepeatedPreviewAnnotationVisitor].
 */
internal fun AnnotationRecorder.createAnnotationVisitor(
    descriptor: String?,
    baseAnnotation: String,
): AnnotationVisitor? {
    val annotationFqcn = descriptorToFqcn(descriptor)
    val repeatedBaseAnnotation = "$baseAnnotation\$Container"
    return when (annotationFqcn) {
        baseAnnotation -> SinglePreviewAnnotationVisitor(this)
        repeatedBaseAnnotation -> RepeatedPreviewAnnotationVisitor(this)
        else -> {
            annotationFqcn?.let { this.recordDerivedAnnotation(DerivedAnnotationRepresentation(it)) }
            null
        }
    }
}
