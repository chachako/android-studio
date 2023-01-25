/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.tools.debugging.packets.ddms

class DdmsChunkTypes {
    @Suppress("SpellCheckingInspection")
    companion object {
        val FAIL: Int = chunkTypeFromString("FAIL")

        val HELO: Int = chunkTypeFromString("HELO")

        val FEAT: Int = chunkTypeFromString("FEAT")

        /**
         * "REAE: REcent Allocation Enable"
         */
        val REAE: Int = chunkTypeFromString("REAE")

        /**
         * "REAQ: REcent Allocation Query"
         */
        val REAQ: Int = chunkTypeFromString("REAQ")

        /**
         * "REAL: REcent Allocation List"
         */
        val REAL: Int = chunkTypeFromString("REAL")

        val APNM: Int = chunkTypeFromString("APNM")

        val WAIT: Int = chunkTypeFromString("WAIT")

        val EXIT: Int = chunkTypeFromString("EXIT")

        /**
         * Requests a `Method Profiling Streaming Start`
         */
        val MPSS: Int = chunkTypeFromString("MPSS")

        /**
         * Requests a `Method Profiling Streaming End`
         */
        val MPSE: Int = chunkTypeFromString("MPSE")

        /**
         * Requests a `Method PRofiling Start`
         */
        val MPRS: Int = chunkTypeFromString("MPRS")

        /**
         * Requests a `Method PRofiling End`
         */
        val MPRE: Int = chunkTypeFromString("MPRE")

        /**
         * Requests a `Method PRofiling Query`
         */
        val MPRQ: Int = chunkTypeFromString("MPRQ")

        /**
         * Requests a `Sampling Profiling Streaming Start`
         */
        val SPSS: Int = chunkTypeFromString("SPSS")

        /**
         * Requests a `Sampling Profiling Streaming End`
         */
        val SPSE: Int = chunkTypeFromString("SPSE")

        /**
         * List `ViewRootImpl`'s of this process
         */
        val VULW: Int = chunkTypeFromString("VULW")

        /**
         * Operation on view root, first parameter in packet should be one of VURT_* constants
         */
        val VURT: Int = chunkTypeFromString("VURT")

        enum class VURTOpCode(val value: Int) {
            /**
             * Dump view hierarchy
             */
            VURT_DUMP_HIERARCHY(1);
        }

        /**
         * Generic View Operation, first parameter in the packet should be one of the VUOP_* constants
         * below.
         */
        val VUOP: Int = chunkTypeFromString("VUOP")

        /**
         * Requests a Gabage Collection of a process (`HeaP Gabage Collect`)
         */
        val HPGC: Int = chunkTypeFromString("HPGC")

        enum class VUOPOpCode(val value: Int) {

            /** Capture View.  */
            VUOP_CAPTURE_VIEW(1);
        }

        /**
         * Convert a 4-character string to a 32-bit chunk type.
         */
        private fun chunkTypeFromString(type: String): Int {
            var result = 0
            check(type.length == 4) { "Type name must be 4 letter long" }
            for (i in 0..3) {
                result = result shl 8
                result = result or type[i].code.toByte().toInt()
            }
            return result
        }

        /**
         * Convert an integer type to a 4-character string.
         */
        fun chunkTypeToString(type: Int): String {
            val ascii = ByteArray(4)
            ascii[0] = (type shr 24 and 0xff).toByte()
            ascii[1] = (type shr 16 and 0xff).toByte()
            ascii[2] = (type shr 8 and 0xff).toByte()
            ascii[3] = (type and 0xff).toByte()
            return String(ascii, Charsets.US_ASCII)
        }
    }
}
