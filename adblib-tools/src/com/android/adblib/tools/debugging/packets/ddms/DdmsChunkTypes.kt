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

@JvmInline
value class DdmsChunkTypes(val value: Int) {

    val text: String
        get() = chunkTypeToString(this)

    override fun toString(): String {
        return text
    }

    @Suppress("SpellCheckingInspection")
    companion object {
        val NULL = DdmsChunkTypes(0)

        val FAIL = chunkTypeFromString("FAIL")

        val HELO = chunkTypeFromString("HELO")

        val FEAT = chunkTypeFromString("FEAT")

        /**
         * "REAE: REcent Allocation Enable"
         */
        val REAE = chunkTypeFromString("REAE")

        /**
         * "REAQ: REcent Allocation Query"
         */
        val REAQ = chunkTypeFromString("REAQ")

        /**
         * "REAL: REcent Allocation List"
         */
        val REAL = chunkTypeFromString("REAL")

        val APNM = chunkTypeFromString("APNM")

        val WAIT = chunkTypeFromString("WAIT")

        val EXIT = chunkTypeFromString("EXIT")

        /**
         * Requests a `Method Profiling Streaming Start`
         */
        val MPSS = chunkTypeFromString("MPSS")

        /**
         * Requests a `Method Profiling Streaming End`
         */
        val MPSE = chunkTypeFromString("MPSE")

        /**
         * Requests a `Method PRofiling Start`
         */
        val MPRS = chunkTypeFromString("MPRS")

        /**
         * Requests a `Method PRofiling End`
         */
        val MPRE = chunkTypeFromString("MPRE")

        /**
         * Requests a `Method PRofiling Query`
         */
        val MPRQ = chunkTypeFromString("MPRQ")

        /**
         * Requests a `Sampling Profiling Streaming Start`
         */
        val SPSS = chunkTypeFromString("SPSS")

        /**
         * Requests a `Sampling Profiling Streaming End`
         */
        val SPSE = chunkTypeFromString("SPSE")

        /**
         * List `ViewRootImpl`'s of this process
         */
        val VULW = chunkTypeFromString("VULW")

        /**
         * Operation on view root, first parameter in packet should be one of VURT_* constants
         */
        val VURT = chunkTypeFromString("VURT")

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
        val VUOP = chunkTypeFromString("VUOP")

        /**
         * Requests a Gabage Collection of a process (`HeaP Gabage Collect`)
         */
        val HPGC = chunkTypeFromString("HPGC")

        enum class VUOPOpCode(val value: Int) {

            /** Capture View.  */
            VUOP_CAPTURE_VIEW(1);
        }

        /**
         * Convert a 4-character string to a 32-bit chunk type.
         */
        private fun chunkTypeFromString(type: String): DdmsChunkTypes {
            var result = 0
            check(type.length == 4) { "Type name must be 4 letter long" }
            for (i in 0..3) {
                result = result shl 8
                result = result or type[i].code.toByte().toInt()
            }
            return DdmsChunkTypes(result)
        }

        /**
         * Convert an integer type to a 4-character string.
         */
        private fun chunkTypeToString(type: DdmsChunkTypes): String {
            val ascii = ByteArray(4)
            ascii[0] = (type.value shr 24 and 0xff).toByte()
            ascii[1] = (type.value shr 16 and 0xff).toByte()
            ascii[2] = (type.value shr 8 and 0xff).toByte()
            ascii[3] = (type.value and 0xff).toByte()
            return String(ascii, Charsets.US_ASCII)
        }
    }
}
