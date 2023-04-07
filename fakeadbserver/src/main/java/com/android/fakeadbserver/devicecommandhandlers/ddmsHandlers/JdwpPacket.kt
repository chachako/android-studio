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
package com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers

import com.google.common.io.ByteStreams
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

// Handle a JDWP packet.
class JdwpPacket(
    val id: Int,
    val isResponse: Boolean,
    val errorCode: Short,
    val payload: ByteArray,
    val cmdSet: Int,
    val cmd: Int
) {

    @Throws(IOException::class)
    fun write(oStream: OutputStream) {
        val response = ByteArray(JDWP_HEADER_LENGTH + payload.size)
        val responseBuffer = ByteBuffer.wrap(response)
        responseBuffer.putInt(response.size)
        responseBuffer.putInt(id)
        responseBuffer.put(if (isResponse) IS_RESPONSE_FLAG else 0)
        if (isResponse) {
            responseBuffer.putShort(errorCode)
        } else {
            responseBuffer.put(cmdSet.toByte())
            responseBuffer.put(cmd.toByte())
        }
        responseBuffer.put(payload)
        oStream.write(response)
    }

    companion object {

        private const val JDWP_HEADER_LENGTH = 11
        private const val IS_RESPONSE_FLAG = 0x80.toByte()

        // Reads a packet from a stream
        @Throws(IOException::class)
        fun readFrom(iStream: InputStream): JdwpPacket {
            val packetHeader = ByteArray(JDWP_HEADER_LENGTH)
            ByteStreams.readFully(iStream, packetHeader)
            val headerBuffer = ByteBuffer.wrap(packetHeader)
            val length = headerBuffer.int
            val id = headerBuffer.int
            val flags = headerBuffer.get().toInt() and 0xff
            val commandSet = headerBuffer.get().toInt() and 0xff
            val command = headerBuffer.get().toInt() and 0xff
            val readCount: Int
            val payloadLength = length - JDWP_HEADER_LENGTH
            val payload = ByteArray(payloadLength)
            if (payloadLength > 0) {
                readCount = iStream.read(payload)
                assert(payload.size == readCount)
            }
            assert(length >= JDWP_HEADER_LENGTH)
            assert(flags and IS_RESPONSE_FLAG.toInt().inv() == 0)
            return JdwpPacket(id, isResponse(flags), 0.toShort(), payload, commandSet, command)
        }

        // Create a response packet
        fun createResponse(id: Int, payload: ByteArray, cmdSet: Int, cmd: Int): JdwpPacket {
            return JdwpPacket(id, true, 0.toShort(), payload, cmdSet, cmd)
        }

        // Create a response packet with an empty payload
        fun createEmptyDdmsResponse(id: Int): JdwpPacket {
            return createResponse(
                id, ByteArray(0), DdmPacket.DDMS_CMD_SET, DdmPacket.DDMS_CMD
            )
        }

        // create a non-response packet
        fun create(payload: ByteArray, cmdSet: Int, cmd: Int): JdwpPacket {
            return JdwpPacket(1234, false, 0.toShort(), payload, cmdSet, cmd)
        }

        private fun isResponse(flags: Int): Boolean {
            return flags and IS_RESPONSE_FLAG.toInt() != 0
        }
    }
}
