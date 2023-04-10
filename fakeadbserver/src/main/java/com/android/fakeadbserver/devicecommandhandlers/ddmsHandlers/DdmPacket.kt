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
package com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers

import java.nio.ByteBuffer

class DdmPacket private constructor(
    val id: Int,
    val errorCode: Short,
    val chunkType: Int,
    val payload: ByteArray,
    val isResponse: Boolean = false
) {

    fun write(jdwpHandlerOutput: JdwpHandlerOutput) {
        JdwpPacket(
            id,
            isResponse,
            errorCode,
            getDdmPayload(chunkType, payload),
            DDMS_CMD_SET,
            DDMS_CMD
        ).write(jdwpHandlerOutput)
    }

    companion object {

        const val DDMS_CMD_SET = 0xc7

        const val DDMS_CMD = 0x01

        @JvmStatic
        fun fromJdwpPacket(packet: JdwpPacket): DdmPacket {
            assert(packet.cmdSet == DDMS_CMD_SET && packet.cmd == DDMS_CMD)
            val buffer = ByteBuffer.wrap(packet.payload)
            val chunkType = buffer.int
            val chunkLength = buffer.int
            val payloadBytes = ByteArray(buffer.remaining())
            buffer.get(payloadBytes)
            return DdmPacket(packet.id, packet.errorCode, chunkType, payloadBytes)
        }

        @JvmStatic
        fun createResponse(id: Int, chunkType: Int, payload: ByteArray) =
            DdmPacket(id, 0.toShort(), chunkType, payload, isResponse = true)

        @JvmStatic
        fun createCommand(id: Int, chunkType: Int, payload: ByteArray) =
            DdmPacket(id, errorCode = 0.toShort(), chunkType, payload, isResponse = false)

        @JvmStatic
        fun isDdmPacket(packet: JdwpPacket): Boolean {
            return packet.cmdSet == DDMS_CMD_SET && packet.cmd == DDMS_CMD
        }

        @JvmStatic
        fun encodeChunkType(typeName: String): Int {
            assert(typeName.length == 4)
            var value = 0
            for (i in 0..3) {
                value = value shl 8
                value = value or typeName[i].code.toByte().toInt()
            }
            return value
        }

        /**
         * Convert an integer type to a 4-character string.
         */
        @JvmStatic
        fun chunkTypeToString(type: Int): String {
            val ascii = ByteArray(4)
            ascii[0] = (type shr 24 and 0xff).toByte()
            ascii[1] = (type shr 16 and 0xff).toByte()
            ascii[2] = (type shr 8 and 0xff).toByte()
            ascii[3] = (type and 0xff).toByte()
            return String(ascii, Charsets.US_ASCII)
        }

        private fun getDdmPayload(chunkType: Int, payload: ByteArray): ByteArray {
            val fullPayload = ByteArray(8 + payload.size) // 9 for chunkType and chunkLength
            val responseBuffer = ByteBuffer.wrap(fullPayload)
            responseBuffer.putInt(chunkType)
            responseBuffer.putInt(payload.size)
            responseBuffer.put(payload)
            return responseBuffer.array()
        }
    }
}
