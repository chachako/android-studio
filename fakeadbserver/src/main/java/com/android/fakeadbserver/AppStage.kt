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
package com.android.fakeadbserver

@JvmInline
value class AppStage(val value: Int) {

    val text: String
        get() = stageToString(this)

    override fun toString(): String {
        return text
    }

    companion object {

        /**
         * From zygote to attach
         */
        val BOOT = stageFromString("BOOT")

        /**
         * From attach to handleBindApplication
         */
        val ATCH = stageFromString("ATCH")

        /**
         * When handleBindApplication is finally reached
         */
        val BIND = stageFromString("BIND")

        /**
         * When the actual package name is known (not the early "<preinitalized>" value).
         */
        val NAMD = stageFromString("NAMD")

        /**
         * Can be skipped if the app is not debugged.
         */
        val DEBG = stageFromString("DEBG")

        /**
         * App is in RunLoop
         */
        val A_GO = stageFromString("A_GO")

        /**
         * Convert a 4-character string to a 32-bit chunk type.
         */
        private fun stageFromString(label: String): AppStage {
            var result = 0
            for (i in 0..3) {
                result = result shl 8 or (label[i].code and 0xff)
            }
            return AppStage(result)
        }

        private fun stageToString(stage: AppStage): String {
            val ascii = ByteArray(4)
            ascii[0] = (stage.value shr 24 and 0xff).toByte()
            ascii[1] = (stage.value shr 16 and 0xff).toByte()
            ascii[2] = (stage.value shr 8 and 0xff).toByte()
            ascii[3] = (stage.value and 0xff).toByte()
            return String(ascii, Charsets.US_ASCII)
        }
    }
}
