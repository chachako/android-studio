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
package com.android.tools.leakcanarylib.data

/**
 * Represents a field referenced within a heap analysis.
 * This class captures details about the field's name, type, likelihood of being a root cause in a particular scenario, and additional
 * reference details.
 */
data class ReferencingField(
    var className: String,
    var type: ReferencingFieldType,
    var isLikelyCause: Boolean,
    var referenceName: String
) {

    /**
     * Enum representing the different types of field references.
     */
    enum class ReferencingFieldType {
        INSTANCE_FIELD,
        STATIC_FIELD,
        LOCAL,
        ARRAY_ENTRY,
    }

    private val displayClassName: String get() = className.substringAfterLast('.')

    private val displayReference: String
        get() = when (type) {
            ReferencingFieldType.ARRAY_ENTRY -> "[$referenceName]"
            ReferencingFieldType.STATIC_FIELD, ReferencingFieldType.INSTANCE_FIELD -> referenceName
            ReferencingFieldType.LOCAL -> "<Java Local>"
        }

    override fun toString(): String {
        val static = if (this.type == ReferencingFieldType.STATIC_FIELD) " static" else ""

        val referenceLinePrefix = "    ↓$static ${this.displayClassName.removeSuffix("[]")}" +
                when (this.type) {
                    ReferencingFieldType.STATIC_FIELD, ReferencingFieldType.INSTANCE_FIELD -> "."
                    else -> ""
                }

        val referenceLine = referenceLinePrefix + this.displayReference

        return if (this.isLikelyCause) {
            val spaces = " ".repeat(referenceLinePrefix.length)
            val underline = "~".repeat(displayReference.length)
            "\n│$referenceLine\n│$spaces$underline"
        } else {
            "\n│$referenceLine"
        }
    }
}
