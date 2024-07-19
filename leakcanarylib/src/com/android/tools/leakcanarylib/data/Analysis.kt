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

import com.android.tools.leakcanarylib.parser.AnalysisParser
import java.io.File

/**
 * Represents the result of a heap analysis.
 *
 * This is a sealed class with two subclasses:
 * 1. AnalysisSuccess:Indicates a successful heap analysis, containing information about leaks and metadata.
 * 2. AnalysisFailure:Indicates a failed heap analysis, containing information about the exception and metadata.
 */
sealed class Analysis {
    companion object {
        const val DUMP_DURATION_UNKNOWN: Long = -1
        fun fromString(heapAnalysis: String): Analysis {
            return if (heapAnalysis.contains("HEAP ANALYSIS RESULT")) {
                AnalysisParser.analysisSuccessFromString(heapAnalysis)
            } else {
                AnalysisParser.analysisFailureFromString(heapAnalysis)
            }
        }
    }
}

/**
 * Represents a successful heap analysis.
 *
 * @property heapDumpFile The file containing the heap dump.
 * @property createdAtTimeMillis The timestamp when the heap dump was created.
 * @property dumpDurationMillis The duration of the heap dump process.
 * @property analysisDurationMillis The duration of the heap analysis process.
 * @property metadata Additional metadata about the analysis.
 * @property leaks A list of leaks detected in the heap dump.
 */
data class AnalysisSuccess(
    val heapDumpFile: File,
    val createdAtTimeMillis: Long,
    val dumpDurationMillis: Long,
    val analysisDurationMillis: Long,
    val metadata: Map<String, String>,
    val leaks: List<Leak>
) : Analysis() {

    override fun toString(): String {
        val applicationLeaks = leaks.filter { it.type == LeakType.APPLICATION_LEAKS }.toList()
        val libraryLeaks = leaks.filter { it.type == LeakType.LIBRARY_LEAKS }.toList()
        val unreachableObjects = ArrayList<String>()
        return """====================================
HEAP ANALYSIS RESULT
====================================
${applicationLeaks.size} APPLICATION LEAKS

References underlined with "~~~" are likely causes.
Learn more at https://squ.re/leaks.
${
            if (applicationLeaks.isNotEmpty()) "\n" + applicationLeaks.joinToString(
                "\n\n"
            ) + "\n" else ""
        }====================================
${libraryLeaks.size} LIBRARY LEAKS

A Library Leak is a leak caused by a known bug in 3rd party code that you do not have control over.
See https://square.github.io/leakcanary/fundamentals-how-leakcanary-works/#4-categorizing-leaks
${
            if (libraryLeaks.isNotEmpty()) "\n" + libraryLeaks.joinToString(
                "\n\n"
            ) + "\n" else ""
        }====================================
${unreachableObjects.size} UNREACHABLE OBJECTS

An unreachable object is still in memory but LeakCanary could not find a strong reference path
from GC roots.
${
            if (unreachableObjects.isNotEmpty()) "\n" + unreachableObjects.joinToString(
                "\n\n"
            ) + "\n" else ""
        }====================================
METADATA

Please include this in bug reports and Stack Overflow questions.
${
            if (metadata.isNotEmpty()) "\n" + metadata.map { "${it.key}: ${it.value}" }
                .joinToString(
                    "\n"
                ) else ""
        }
Analysis duration: $analysisDurationMillis ms
Heap dump file path: ${heapDumpFile.absolutePath}
Heap dump timestamp: $createdAtTimeMillis
Heap dump duration: ${if (dumpDurationMillis != Analysis.DUMP_DURATION_UNKNOWN) "$dumpDurationMillis ms" else "Unknown"}
===================================="""
    }
}

/**
 * Represents a failed heap analysis.
 *
 * @property heapDumpFile The file containing the heap dump.
 * @property createdAtTimeMillis The timestamp when the heap dump was created.
 * @property dumpDurationMillis The duration of the heap dump process.
 * @property analysisDurationMillis The duration of the heap analysis process.
 * @property exception The exception that caused the analysis to fail.
 */
data class AnalysisFailure(
    val heapDumpFile: File,
    val createdAtTimeMillis: Long,
    val dumpDurationMillis: Long,
    val analysisDurationMillis: Long,
    val exception: Throwable
) : Analysis() {

    override fun toString(): String {
        return """====================================
HEAP ANALYSIS FAILED

You can report this failure at https://github.com/square/leakcanary/issues
Please provide the stacktrace, metadata and the heap dump file.
====================================
STACKTRACE

$exception====================================
METADATA

Analysis duration: $analysisDurationMillis ms
Heap dump file path: ${heapDumpFile.absolutePath}
Heap dump timestamp: $createdAtTimeMillis
===================================="""
    }
}
