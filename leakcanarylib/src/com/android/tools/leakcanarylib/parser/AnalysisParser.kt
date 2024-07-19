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

import com.android.tools.leakcanarylib.data.AnalysisFailure
import com.android.tools.leakcanarylib.data.AnalysisSuccess
import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.leakcanarylib.data.LeakType
import java.io.File

/**
 * A utility class for parsing the string representation of heap analysis results into corresponding `AnalysisSuccess` or
 * `AnalysisFailure` objects.
 */
class AnalysisParser {

    companion object {
        private const val SEPARATOR: String = "===================================="

        /**
         * Parses a string representation of a successful heap analysis into an `AnalysisSuccess` object.
         *
         * @param text The string representation of the analysis result.
         * @return An `AnalysisSuccess` object containing the parsed data.
         */
        fun analysisSuccessFromString(text: String): AnalysisSuccess {
            val metadataSection = extractSection(text, "METADATA")
            val libraryLeakSection = extractSection(text, "LIBRARY LEAKS")
            val applicationLeakSection = extractSection(text, "APPLICATION LEAKS")

            // Extract heap dump file from metadata section.
            val heapDumpFile = extractFileFromTexts(metadataSection.trim().split("\n"))

            // Parse heap dump timestamp from metadata section.
            val heapDumpTimestamp = extractHeapDumpTimestamp(metadataSection.trim().split("\n"))

            // Parse dump duration from metadata section.
            val heapDumpDuration = extractHeapDumpDuration(metadataSection.trim().split("\n"))

            // Parse analysis duration from metadata section.
            val heapAnalysisDuration = extractHeapAnalysisDuration(metadataSection.trim().split("\n"))

            // Parse metadata from metadata section.
            val metadata = parseMetadataMap(metadataSection.trim().split("\n"))

            val applicationLeaks = Leak.fromString(applicationLeakSection, LeakType.APPLICATION_LEAKS)
            val libraryLeaks = Leak.fromString(libraryLeakSection, LeakType.LIBRARY_LEAKS)
            val overallLeaks = applicationLeaks + libraryLeaks

            return AnalysisSuccess(
                heapDumpFile = heapDumpFile,
                createdAtTimeMillis = heapDumpTimestamp,
                dumpDurationMillis = heapDumpDuration,
                metadata = metadata,
                leaks = overallLeaks,
                analysisDurationMillis = heapAnalysisDuration
            )
        }

        /**
         * Parses a string representation of a failed heap analysis into an `AnalysisFailure` object.
         *
         * @param text The string representation of the analysis result.
         * @return An `AnalysisFailure` object containing the parsed data and the exception.
         */
        fun analysisFailureFromString(text: String): AnalysisFailure {
            val metadataSection = extractSection(text, "METADATA")
            val stacktraceSection = extractSection(text, "STACKTRACE")

            // Extract heap dump file from metadata section.
            val heapDumpFile = extractFileFromTexts(metadataSection.lines())

            // Parse heap dump timestamp from metadata section.
            val heapDumpTimestamp = extractHeapDumpTimestamp(metadataSection.lines())

            // Parse dump duration from metadata section.
            val heapDumpDuration = extractHeapDumpDuration(metadataSection.lines())

            // Parse analysis duration from metadata section.
            val heapAnalysisDuration = extractHeapAnalysisDuration(metadataSection.lines())

            // Convert stacktrace section to a HeapAnalysisException object.
            val exception = Throwable(findRootCauseForException(stacktraceSection))

            // Return a new HeapAnalysisFailure object with the parsed data and exception.
            return AnalysisFailure(
                heapDumpFile,
                heapDumpTimestamp,
                heapDumpDuration,
                heapAnalysisDuration,
                exception
            )
        }

        private fun extractHeapDumpDuration(lines: List<String>): Long {
            val durationMillisLine = lines.firstOrNull { it.startsWith("Heap dump duration:") }
            return durationMillisLine?.substringAfter(": ")
                ?.trim()
                ?.removeSuffix(" ms")
                ?.toLongOrNull()
                ?: -1
        }

        private fun extractHeapDumpTimestamp(lines: List<String>): Long {
            val timeLine = lines.first { it.startsWith("Heap dump timestamp:") }
            return timeLine.substringAfter(": ").trim().toLong()
        }

        private fun extractFileFromTexts(lines: List<String>): File {
            val dumpFileLine = lines.first { it.startsWith("Heap dump file path:") }
            val dumpFilePath = dumpFileLine.substringAfter(": ").trim()
            return File(dumpFilePath)
        }

        private fun parseMetadataMap(lines: List<String>): Map<String, String> {
            val startIndex = lines.indexOfFirst { it.startsWith("METADATA") }
            val endIndex = lines.indexOfFirst { it.startsWith("Analysis duration") }
            val metadataLines = lines.subList(startIndex + 1, endIndex)
            val metadata = mutableMapOf<String, String>()
            for (line in metadataLines) {
                val parts = line.split(":")
                if (parts.size == 2 && parts[0].trim() != "") {
                    metadata[parts[0].trim()] = parts[1].trim()
                }
            }
            return metadata
        }

        private fun extractHeapAnalysisDuration(lines: List<String>): Long {
            val durationLine = lines.first { it.startsWith("Analysis duration:") }
            return durationLine.substringAfter(": ")
                .trim()
                .removeSuffix(" ms")
                .toLong()
        }

        private fun extractSection(text: String, sectionName: String): String {
            val section = text.substring(text.indexOf(sectionName) + 1, text.length)
            val sectionSubList = section.substring(0, section.indexOf(SEPARATOR))
                .trim().trim().split("\n")
            return sectionSubList.subList(1, sectionSubList.size).joinToString("\n")
        }

        private fun findRootCauseForException(stackTrace: String): Throwable {
            val trace = stackTrace.replace("\n", "")
            val firstLine = trace.trim().lines().firstOrNull()
            return RuntimeException(firstLine ?: "Unknown error during heap analysis")
        }
    }
}
