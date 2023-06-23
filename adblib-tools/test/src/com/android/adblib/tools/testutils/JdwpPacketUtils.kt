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
package com.android.adblib.tools.testutils

import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.PayloadProvider
import com.android.adblib.tools.debugging.packets.appendJdwpHeader
import com.android.adblib.tools.debugging.packets.checkPacketLength
import com.android.adblib.tools.debugging.packets.copy
import com.android.adblib.tools.debugging.packets.parseHeader
import com.android.adblib.tools.debugging.packets.withPayload
import com.android.adblib.tools.debugging.packets.write
import com.android.adblib.utils.ResizableBuffer

/**
 * Returns an in-memory copy of this [JdwpPacketView].
 *
 * @throws IllegalArgumentException if [JdwpPacketView.payload] does not contain exactly
 * [JdwpPacketView.length] minus [JdwpPacketConstants.PACKET_HEADER_LENGTH] bytes
 *
 * @param workBuffer (Optional) The [ResizableBuffer] used to transfer data
 */
internal suspend fun JdwpPacketView.toMutable(
    workBuffer: ResizableBuffer = ResizableBuffer()
): MutableJdwpPacket {

    // Copy header
    workBuffer.clear()
    workBuffer.appendJdwpHeader(this)

    val mutableJdwpPacket = MutableJdwpPacket()
    mutableJdwpPacket.parseHeader(workBuffer.forChannelWrite())

    // Copy payload into our workBuffer
    workBuffer.clear()
    val copyChannel = ByteBufferAdbOutputChannel(workBuffer)
    val byteCount = withPayload { payload ->
        copyChannel.write(payload)
    }
    checkPacketLength(byteCount)

    // Make a copy into our own ByteBuffer
    val bufferCopy = workBuffer.forChannelWrite().copy()

    // Make an input channel for it
    mutableJdwpPacket.payloadProvider = PayloadProvider.forByteBuffer(bufferCopy)

    return mutableJdwpPacket
}
