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
package com.android.tools.leakcanarylib.parser

import com.android.tools.leakcanarylib.data.ReferencingField

class ReferencingFieldParser {
    companion object {

        /**
         * Parses lines from a heap analysis output to construct a `ReferencingField` object.
         *
         * @param referenceLines List of lines from the heap analysis output representing the field reference. The first line typically
         * contains the reference path, and the second line may indicate if the field is a prime suspect (underlined).
         * @param additionalLinesPrefix A prefix to remove from each line before parsing.This is usually used to strip away indentation
         * or common prefixes in the output.
         *
         * @return A `ReferencingField` object representing the parsed field reference.
         * @throws IllegalArgumentException If `referenceLines` is empty.
         */
        fun parseReferencingField(referenceLines: List<String>, additionalLinesPrefix: String): ReferencingField {
            if (referenceLines.isEmpty()) {
                throw IllegalArgumentException("Reference lines cannot be empty")
            }

            val referenceTypeAndDisplayName = parseReferencePath(referenceLines[0].removePrefix(additionalLinesPrefix))
            val referenceType = ReferencingField.ReferencingFieldType.valueOf(referenceTypeAndDisplayName[0])
            val referenceDisplayName = referenceTypeAndDisplayName[1]
            val owningClassName = referenceTypeAndDisplayName[2]
            val isPrimeSuspect = referenceLines.getOrNull(1)?.contains("~") ?: false

            return ReferencingField(
                type = referenceType,
                className = owningClassName,
                referenceName = referenceDisplayName,
                isLikelyCause = isPrimeSuspect
            )
        }

        /**
         * Parses a single line representing a reference path.This method extracts the field reference type, owning class name, and
         * reference display name from the reference path line.
         *
         * @param referencePathLine A single line representing the reference path.
         *
         * @return A list containing three elements, in the following order:
         * [0]- The field reference type as a string (e.g., "STATIC_FIELD", "INSTANCE_FIELD").
         * [1]- The reference display name (e.g., "myField").
         * [2]- The simple name of the owning class.
         */
        private fun parseReferencePath(referencePathLine: String): List<String> {
            var referenceType = ""
            var owningClassSimpleName = ""
            var referenceDisplayName = ""

            val referencePathLineStartingIndex = referencePathLine.indexOf("↓") + 1
            var referencePathLineTrimmed =
                referencePathLine.substring(referencePathLineStartingIndex).trim()

            when {
                referencePathLineTrimmed.startsWith("static") -> {
                    referenceType = "STATIC_FIELD"
                    val parts = referencePathLineTrimmed.removePrefix("static").split(".")
                    owningClassSimpleName = parts[0].trim()
                    referenceDisplayName = parts[1].trim()
                }
                referencePathLineTrimmed.contains("[") -> {
                    referenceType = "ARRAY_ENTRY"
                    val arrayIndex = referencePathLineTrimmed.indexOf("[")
                    val arrayEndIndex = referencePathLineTrimmed.indexOf("]")
                    owningClassSimpleName = referencePathLineTrimmed.substring(0, arrayIndex).trim()
                    referenceDisplayName = referencePathLineTrimmed.substring(arrayIndex + 1, arrayEndIndex).trim()
                }
                referencePathLineTrimmed.contains("<Java Local>") -> {
                    referenceType = "LOCAL"
                    referenceDisplayName = "<Java Local>"
                    owningClassSimpleName = referencePathLineTrimmed.removeSuffix("<Java Local>").trim()
                }
                else -> {
                    referenceType = "INSTANCE_FIELD"
                    val parts = referencePathLineTrimmed.split(".")
                    owningClassSimpleName = parts[0].trim()
                    referenceDisplayName = parts[1].trim()
                }
            }

            return listOf(referenceType, referenceDisplayName, owningClassSimpleName)
        }
    }
}
