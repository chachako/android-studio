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

import com.android.tools.leakcanarylib.data.GcRootType
import com.android.tools.leakcanarylib.data.LeakTrace
import com.android.tools.leakcanarylib.data.Node

/**
 * Utility class for parsing leak traces.
 *
 * This class provides functionality to extract structured information about GC roots,reference paths, and leaking objects from the raw
 * text output.
 */
class LeakTraceParser {

    companion object {
        private const val GC_ROOT_PREFIX = "GC Root:"
        private const val REFERENCE_PREFIX = "├─ "
        private const val LEAKING_OBJECT_PREFIX = "Leaking Object"

        /**
         * Parses a single leak trace from a list of lines.
         *
         * @param lines List of raw lines from the leak trace.
         * @param prefixReferencePath Prefix that identifies the start of the reference path.
         * @param prefixLeakingObject Prefix that identifies the start of the leaking object.
         * @return Parsed LeakTrace object.
         * @throws IllegalArgumentException If the leak trace format is invalid.
         */
        fun parseLeakTrace(
            lines: List<String>,
            prefixReferencePath: String,
            prefixLeakingObject: String
        ): LeakTrace {
            val gcRootType = extractGcRootType(lines)
            val nodes = mutableListOf<Node>()

            val referenceLines = extractReferencePathLines(lines, prefixReferencePath, prefixLeakingObject)
            nodes.addAll(parseReferenceNodes(referenceLines, prefixReferencePath))

            val objectLines = extractLeakingObjectLines(lines, prefixLeakingObject)
            nodes.add(Node.fromString(objectLines.joinToString("\n") { it.trim() }))

            return LeakTrace(gcRootType = gcRootType, nodes = nodes)
        }

        /**
         * Parses multiple leak traces from a list of lines.
         * This method is designed to handle cases where a leak analysis tool reports multiple leaks traces with the same
         * signature, consolidating them into a list of `LeakTrace` objects.
         *
         * @param leakLines The raw lines of the leak analysis output, potentially containing multiple leaks.
         * @return A list of `LeakTrace` objects, each representing a parsed leak trace.
         */
        fun parseLeakTraces(leakLines: List<String>): Pair<List<LeakTrace>, Int> {
            val leakTraces = mutableListOf<LeakTrace>()

            val lineCount = leakLines.indices.find {
                leakLines[it].startsWith("Displaying only") }

            var leakTraceCount = if (lineCount == null) 1 else parseTraceCount(leakLines[lineCount])

            val lines = leakLines.subList(leakLines.indices.find {
                leakLines[it].startsWith("Signature:") } ?: 0, leakLines.size)

            if (lines.isEmpty())
                return Pair(leakTraces, leakTraceCount)

            // Assuming only 1 trace is displayed
            val leakTrace = LeakTrace.fromString(lines.subList(1, lines.size).joinToString("\n"))
            leakTraces.add(leakTrace)
            return Pair(leakTraces, leakTraceCount)
        }

        private fun parseTraceCount(line: String): Int {
            val regex = Regex("""Displaying only 1 leak trace out of (\d+) with the same signature""")
            val matchResult = regex.find(line)
            return matchResult?.groups?.get(1)?.value?.toIntOrNull() ?: return 0
        }

        /**
         * Extracts the GC root type from the lines.
         *
         * @param lines List of lines from the leak trace.
         * @return Extracted GcRootType.
         * @throws IllegalArgumentException If the "GC Root" line is not found.
         */
        private fun extractGcRootType(lines: List<String>): GcRootType {
            val gcRootIndex = lines.indexOfFirst { it.trim().contains(GC_ROOT_PREFIX) }
            if (gcRootIndex == -1) {
                throw IllegalArgumentException("Invalid leak trace format: GC Root not found")
            }
            val gcRootDescription = lines[gcRootIndex].substringAfter(":").trim()
            return GcRootType.fromDescription(gcRootDescription)
        }

        /**
         * Extracts reference path lines from the leak trace.
         *
         * @param lines List of lines from the leak trace.
         * @param pathPrefix Prefix that identifies the start of the reference path.
         * @param objectPrefix Prefix that identifies the start of the leaking object.
         * @return List of lines representing the reference path.
         */
        private fun extractReferencePathLines(
            lines: List<String>,
            pathPrefix: String,
            objectPrefix: String
        ): List<String> {
            val startIndex = lines.indexOfFirst { it.trim().contains(pathPrefix) }
            val endIndex = lines.indexOfFirst { it.trim().startsWith(objectPrefix) }
            return if (startIndex in 0 until endIndex) lines.subList(startIndex, endIndex) else emptyList()
        }

        /**
         * Extracts leaking object lines from the leak trace.
         *
         * @param lines List of lines from the leak trace.
         * @param objectPrefix Prefix that identifies the start of the leaking object.
         * @return List of lines representing the leaking object.
         */
        private fun extractLeakingObjectLines(
            lines: List<String>,
            objectPrefix: String
        ): List<String> {
            val referencePathEndIndex = lines.indexOfFirst { it.trim().startsWith(objectPrefix) }
            val linesAfterReferences = lines.subList(referencePathEndIndex, lines.size)
            return linesAfterReferences.takeWhile { line ->
                !line.trim().startsWith(REFERENCE_PREFIX) && !line.trim().startsWith(GC_ROOT_PREFIX)
            }.dropWhile { it.trim().isEmpty() }
        }

        /**
         * Parses reference nodes from the given lines.
         *
         * @param lines List of lines representing the reference path.
         * @param prefix Prefix that identifies the start of a reference.
         * @return List of parsed reference nodes.
         */
        private fun parseReferenceNodes(
            lines: List<String>,
            prefix: String
        ): List<Node> {
            val referenceNodes = mutableListOf<Node>()
            var currentReferenceLines = mutableListOf<String>()

            for (line in lines) {
                if (line.startsWith(prefix)) {
                    if (currentReferenceLines.isNotEmpty()) {
                        referenceNodes.add(Node.fromString(currentReferenceLines.joinToString("\n")))
                        currentReferenceLines.clear()
                    }
                }
                currentReferenceLines.add(line.trimStart())
            }
            if (currentReferenceLines.isNotEmpty()) {
                referenceNodes.add(Node.fromString(currentReferenceLines.joinToString("\n")))
            }
            return referenceNodes
        }
    }
}
