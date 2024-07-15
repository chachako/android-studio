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

import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.leakcanarylib.data.LeakType

/**
 * Utility class designed to parse raw memory leak traces into structured 'Leak' objects.
 */
class LeakParser {

    companion object {
        /**
         * Parses a raw leak trace text into a list of `Leak` objects.
         *
         * @param lines The raw leak trace text.
         * @param type The type of leak.
         * @return A list of `Leak` objects, each representing a single parsed leak.
         */
        fun fromString(lines: String, type: LeakType): List<Leak> {
            val leaks = mutableListOf<Leak>()
            val singleLeaks = lines.split("\n\n")
                .filter { s: String ->
                    !s.contains("A Library Leak is a leak caused by a known bug")
                    && !s.contains("See https://square.github.io/leakcanary")
                    && !s.contains("References underlined with")
                    && !s.contains("Learn more at")
                }
            singleLeaks.forEach { singleLeak -> leaks.add(parseLeak(singleLeak.lines(), type)) }
            return leaks
        }

        private fun parseLeak(lines: List<String>, type: LeakType): Leak {
            val bytesRetained = extractBytesRetained(lines)
            val signature = extractSignature(lines)
            val (displayedLeakTrace, leakTraceCount) = LeakTraceParser.parseLeakTraces(lines)

            return Leak(
                leakTraceCount = leakTraceCount,
                displayedLeakTrace = displayedLeakTrace,
                type = type,
                retainedByteSize = bytesRetained,
                signature = signature
            )
        }

        /**
         * Extracts the retained byte size from the lines.
         *
         * @param lines List of lines from the leak trace.
         * @return Extracted byte size, or 0 if not found.
         */
        private fun extractBytesRetained(lines: List<String>): Int {
            return lines.firstOrNull { it.contains("bytes retained") }
                ?.split(" ")
                ?.firstOrNull()
                ?.toIntOrNull() ?: 0
        }

        /**
         * Extracts the signature from the lines.
         *
         * @param lines List of lines from the leak trace.
         * @return Extracted signature, or an empty string if not found.
         */
        private fun extractSignature(lines: List<String>): String {
            return lines.firstOrNull { it.contains("Signature:") }
                ?.replace("Signature: ", "")
                ?.trim() ?: ""
        }
    }
}
