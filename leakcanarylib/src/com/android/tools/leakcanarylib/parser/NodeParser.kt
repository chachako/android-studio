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

import com.android.tools.leakcanarylib.data.LeakTraceNodeType
import com.android.tools.leakcanarylib.data.LeakingStatus
import com.android.tools.leakcanarylib.data.Node

/**
 * Utility class for parsing the string representation of leak trace nodes into structured `Node` objects.
 */
class NodeParser {

    companion object {
        private val leakingStatusRegex =
            Regex("""Leaking: (YES|NO|UNKNOWN)(?: \((.*)\))?""")
        private val leakingStatusMultiLineRegex =
            Regex("""Leaking:\s*(YES|NO|UNKNOWN)\s*\((.*)""")
        private val retainingRegex =
            Regex("""Retaining (\d+(?:\.\d+)?)\s*(\w+)?B? in (\d+) objects""")

        /**
         * Parses a list of strings representing a leak trace node and its associated reference line (if present). This method first
         * separates the lines describing the leaking object itself from the reference lines, then parses each part separately and
         * combines them into a single `Node`.
         *
         * @param inputLines: The list of lines to parse.
         * @param firstLinePrefix: Prefix expected on the first line of the leaking object's description.
         * @param additionalLinesPrefix: Prefix expected on subsequent lines of the leaking object's description.
         * @param referenceLinePrefix: Prefix expected on reference lines (if present).
         *
         * @return A `Node` object representing the parsed leak trace node.
         */
        fun parse(
            inputLines: List<String>,
            firstLinePrefix: String,
            additionalLinesPrefix: String,
            referenceLinePrefix: String
        ): Node {
            val lines = inputLines.map { it.trim() }
            val firstReferenceLineIndex =
                lines.indexOfFirst { it.startsWith(referenceLinePrefix) }

            val (objectLines, referenceLines) = if (firstReferenceLineIndex == -1) {
                Pair(lines, emptyList())
            } else {
                Pair(
                    lines.subList(0, firstReferenceLineIndex),
                    lines.subList(firstReferenceLineIndex, lines.size)
                )
            }

            val leakingNode =
                parseLeakNode(objectLines, firstLinePrefix, additionalLinesPrefix)
            leakingNode.referencingField =
                ReferencingFieldParser.parseReferencingField(referenceLines, additionalLinesPrefix)
            return leakingNode
        }

        /**
         * Parses a list of strings representing the core information of a leak trace node (excluding reference lines). This method
         * extracts details like the class name, type, leaking status, retained memory information, and any additional notes.
         *
         * @param inputLines: The list of lines to parse.
         * @param firstLinePrefix: Prefix expected on the first line.
         * @param additionalLinesPrefix: Prefix expected on subsequent lines.
         *
         * @return A `Node` object representing the parsed leak trace node.
         * @throws IllegalArgumentException: If the input lines are malformed or missing essential information.
         */
        fun parseLeakNode(
            inputLines: List<String>,
            firstLinePrefix: String, additionalLinesPrefix: String
        ): Node {
            var className: String? = null
            var type: LeakTraceNodeType? = null
            var leakingStatus: LeakingStatus? = null
            var leakingStatusReason = ""
            var retainedHeapByteSize: Int? = null
            var retainedObjectCount: Int? = null
            var leakingMultiLine = false
            val leakingStatusReasonBuilder = StringBuilder()
            val notes = mutableListOf<String>()

            for (line in inputLines) {
                when {
                    line.startsWith(firstLinePrefix) -> {
                        val parts = line.removePrefix(firstLinePrefix).split(" ")
                        className = parts.getOrNull(0)
                        type = parts.getOrNull(1)?.let { LeakTraceNodeType.valueOf(it.uppercase()) }
                    }

                    leakingStatusRegex.matches(line.removePrefix(additionalLinesPrefix)) -> {
                        val matchResult =
                            leakingStatusRegex.find(line.removePrefix(additionalLinesPrefix))!!
                        leakingStatus = LeakingStatus.fromString(matchResult.groupValues[1])
                        leakingStatusReason = matchResult.groupValues[2]
                    }

                    leakingStatusMultiLineRegex.matches(line.removePrefix(additionalLinesPrefix)) -> {
                        val matchResult =
                            leakingStatusMultiLineRegex.find(line.removePrefix(additionalLinesPrefix))!!
                        leakingStatus = LeakingStatus.fromString(matchResult.groupValues[1])
                        leakingStatusReasonBuilder.append(matchResult.groupValues[2])
                        leakingMultiLine = true
                    }

                    leakingMultiLine -> {
                        leakingStatusReasonBuilder.append(" ")
                            .append(line.removePrefix(additionalLinesPrefix))
                        leakingStatusReason =
                            leakingStatusReasonBuilder.toString().trimEnd().dropLast(1)
                        leakingMultiLine = false
                    }

                    retainingRegex.matches(line.removePrefix(additionalLinesPrefix)) -> {
                        val matchResult =
                            retainingRegex.find(line.removePrefix(additionalLinesPrefix))!!
                        val (sizeString, unit, countString) = matchResult.destructured
                        retainedHeapByteSize = getByteSize(sizeString.toDouble(), unit).toInt()
                        retainedObjectCount = countString.toInt()
                    }

                    line.startsWith(additionalLinesPrefix) -> {
                        notes.add(line.removePrefix(additionalLinesPrefix))
                    }
                }
            }

            requireNotNull(type) { "Invalid object type" }
            requireNotNull(className) { "Class name not found" }
            requireNotNull(leakingStatus) { "Leaking status not found" }

            return Node(
                type,
                className,
                leakingStatus,
                leakingStatusReason,
                retainedHeapByteSize,
                retainedObjectCount,
                notes,
                null
            )
        }

        /**
         * Converts a size value and its associated unit into a byte count.
         */
        private fun getByteSize(size: Double, unit: String): Double {
            return size * when (unit.uppercase()) {
                // To be consistent with LeakCanary, using unit as 1000
                "KB" -> 1e3
                "MB" -> 1e6
                "GB" -> 1e9
                "TB" -> 1e12
                "PB" -> 1e15
                else -> 1.0
            }
        }
    }
}
