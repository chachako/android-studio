/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.base.Charsets
import java.io.OutputStream

abstract class CommandHandler {
    companion object {

        @JvmStatic
        protected fun writeOkay(stream: OutputStream) {
            stream.write("OKAY".toByteArray(Charsets.UTF_8))
        }

        @JvmStatic
        protected fun writeOkayResponse(stream: OutputStream, response: String) {
            writeOkay(stream)
            write4ByteHexIntString(stream, response.length)
            writeString(stream, response)
        }

        @JvmStatic
        protected fun writeFail(stream: OutputStream) {
            stream.write("FAIL".toByteArray(Charsets.UTF_8))
        }

        @JvmStatic
        protected fun writeFailResponse(stream: OutputStream, reason: String) {
            writeFail(stream)
            write4ByteHexIntString(stream, reason.length)
            writeString(stream, reason)
        }

        @JvmStatic
        protected fun write4ByteHexIntString(stream: OutputStream, value: Int) {
            stream.write(String.format("%04x", value).toByteArray(Charsets.UTF_8))
        }

        @JvmStatic
        protected fun writeString(stream: OutputStream, string: String) {
            stream.write(string.toByteArray(Charsets.UTF_8))
        }

        @JvmStatic
        protected fun writeFailMissingDevice(
            stream: OutputStream, service: String
        ) {
            writeFailResponse(stream, "No device found to satisfy $service")
        }
    }
}
