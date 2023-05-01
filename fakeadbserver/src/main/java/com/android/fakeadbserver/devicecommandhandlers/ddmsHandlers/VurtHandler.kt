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

import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.CoroutineScope
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VurtHandler : DdmPacketHandler {

    override fun handlePacket(
        device: DeviceState,
        client: ClientState,
        packet: DdmPacket,
        jdwpHandlerOutput: JdwpHandlerOutput,
        socketScope: CoroutineScope
    ): Boolean {
        // We only support "capture" view, which is
        // Opcode: 4 bytes
        // view root: length prefixed UTF16 string
        // skipChildren: 4-byte boolean
        // includeProperties: 4-byte boolean
        // useV2: 4-byte boolean
        val payload = ByteBuffer.wrap(packet.payload).order(ByteOrder.BIG_ENDIAN)
        val opCode = payload.readInt()
        if (opCode != VURT_DUMP_HIERARCHY) {
            replyDdmFail(jdwpHandlerOutput, packet.id)
            return true // Keep JDWP connection open
        }
        val viewRoot = payload.readLengthPrefixedString()
        val skipChildren = payload.readBooleanInt()
        val includeProperties = payload.readBooleanInt()
        val useV2 = payload.readBooleanInt()

        client.viewsState.viewHierarchyData(viewRoot, skipChildren, includeProperties, useV2)?.also {
            val responsePacket = DdmPacket.createResponse(packet.id, CHUNK_TYPE, it.array())
            responsePacket.write(jdwpHandlerOutput)
        } ?: run {
            replyDdmFail(jdwpHandlerOutput, packet.id)
        }

        return true // Keep JDWP connection open
    }

    companion object {
        val CHUNK_TYPE = DdmPacket.encodeChunkType("VURT")

        const val VURT_DUMP_HIERARCHY = 1
    }
}
