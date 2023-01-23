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
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FEATHandler : DDMPacketHandler {

    override fun handlePacket(
        device: DeviceState,
        client: ClientState,
        packet: DdmPacket,
        oStream: OutputStream
    ): Boolean {
        val features = client.features
        // 4 = number of features
        // for each feature:
        //   4 = number of UTF-16 characters
        //   2 = number of bytes per UTF-16 character
        val payloadLength = 4 + features.sumOf { 4 + 2 * it.length }
        val payload = ByteBuffer.allocate(payloadLength).order(ByteOrder.BIG_ENDIAN)
        payload.putInt(features.size)
        for (feature in features) {
            payload.putInt(feature.length)
            for (c in feature) {
                payload.putChar(c)
            }
        }

        val responsePacket = createResponse(packet.id, CHUNK_TYPE, payload.array())
        responsePacket.write(oStream)

        // Keep JDWP connection open
        return true
    }

    companion object {

        @JvmField
        val CHUNK_TYPE = encodeChunkType("FEAT")
    }
}
