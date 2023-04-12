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
package com.android.fakeadbserver

import java.io.IOException
import java.net.Socket
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

// TODO: Should we declare a setSoTimeout of 10s on the socket?
// Otherwise, a bad test fails with timeout which can take a long time.
// If we go this way, we must add a SocketTimeoutException handler in the main loop runner.
class SmartSocket
internal constructor(private val mSocket: SocketChannel) : AutoCloseable {

    val socket: Socket
        get() = mSocket.socket()

    @Throws(IOException::class)
    internal fun readServiceRequest(): ServiceRequest {
        val lengthString = ByteArray(4)
        readFully(lengthString)
        val requestLength = String(lengthString).toInt(16)
        val payloadBytes = ByteArray(requestLength)
        readFully(payloadBytes)
        val payload = String(payloadBytes, StandardCharsets.UTF_8)
        return ServiceRequest(payload)
    }

    @Throws(IOException::class)
    private fun readFully(buffer: ByteArray) {
        var bytesRead = 0
        while (bytesRead < buffer.size) {
            bytesRead += mSocket.socket()
                .getInputStream()
                .read(buffer, bytesRead, buffer.size - bytesRead)
        }
    }

    @Throws(IOException::class)
    fun sendOkay() {
        val stream = mSocket.socket().getOutputStream()
        stream.write("OKAY".toByteArray(StandardCharsets.US_ASCII))
    }

    fun sendFailWithReason(reason: String) {
        try {
            val stream = mSocket.socket().getOutputStream()
            stream.write("FAIL".toByteArray(StandardCharsets.US_ASCII))
            val reasonBytes = reason.toByteArray(StandardCharsets.UTF_8)
            assert(reasonBytes.size < 65536)
            stream.write(
                String.format("%04x", reason.length)
                    .toByteArray(StandardCharsets.US_ASCII)
            )
            stream.write(reasonBytes)
            stream.flush()
        } catch (ignored: IOException) {
        }
    }

    @Throws(Exception::class)
    override fun close() {
        mSocket.close()
    }
}
