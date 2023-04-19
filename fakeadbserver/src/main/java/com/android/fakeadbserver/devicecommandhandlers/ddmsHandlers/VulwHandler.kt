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
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.DdmPacket.Companion.createResponse
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.DdmPacket.Companion.encodeChunkType
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VulwHandler : DdmPacketHandler {

    override fun handlePacket(
        device: DeviceState,
        client: ClientState,
        packet: DdmPacket,
        jdwpHandlerOutput: JdwpHandlerOutput
    ): Boolean {
        val viewRoots = client.viewsState.viewRoots()
        val payload = ByteBuffer.allocate(4 + viewRoots.ddmByteCount()).order(ByteOrder.BIG_ENDIAN)
        payload.putInt(viewRoots.size)
        viewRoots.forEach {
            payload.putDdmString(it)
        }
        val responsePacket = createResponse(packet.id, CHUNK_TYPE, payload.array())
        responsePacket.write(jdwpHandlerOutput)

        // Keep JDWP connection open
        return true
    }

    companion object {
        val CHUNK_TYPE = encodeChunkType("VULW")
    }
}
