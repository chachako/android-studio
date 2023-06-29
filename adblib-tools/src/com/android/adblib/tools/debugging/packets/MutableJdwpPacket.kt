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
package com.android.adblib.tools.debugging.packets

import com.android.adblib.AdbInputChannel
import com.android.adblib.tools.debugging.impl.EphemeralJdwpPacket
import com.android.adblib.tools.debugging.packets.JdwpPacketView.Companion.FlagsAndWord
import com.android.adblib.utils.ResizableBuffer
import java.nio.ByteBuffer

/**
 * A mutable version of [JdwpPacketView], to be used for creating JDWP packets
 * or re-using the same instance for multiple views over time for performance reason.
 */
internal class MutableJdwpPacket : JdwpPacketView {

    private var flagsAndWord = FlagsAndWord()

    override var length: Int = 0
        set(value) {
            if (value < JdwpPacketConstants.PACKET_HEADER_LENGTH) {
                throw IllegalArgumentException(
                    "Packet length should always be greater or equal " +
                            "to ${JdwpPacketConstants.PACKET_HEADER_LENGTH}"
                )
            }
            field = value
        }

    override var id: Int = 0

    override var flags: Int
        get() = flagsAndWord.flags
        set(value) {
            flagsAndWord = flagsAndWord.withFlags(value)
        }

    override var isReply: Boolean
        get() = flagsAndWord.isReply
        set(value) {
            flagsAndWord = flagsAndWord.withIsReply(value)
        }

    override var isCommand: Boolean
        get() = flagsAndWord.isCommand
        set(value) {
            flagsAndWord = flagsAndWord.withIsCommand(value)
        }

    override suspend fun toOffline(workBuffer: ResizableBuffer): JdwpPacketView {
        // Note: We go through 2 instances here to get to a "thread-safe" implementation,
        // which is sub-optimal, but this class (i.e. `MutableJdwpPacket`) is mostly only
        // used in test and will eventually be removed entirely.
        return EphemeralJdwpPacket
            .fromPacket(this, payloadProvider)
            .toOffline(workBuffer)
    }

    override var cmdSet: Int
        get() = flagsAndWord.cmdSet
        set(value) {
            flagsAndWord = flagsAndWord.withCmdSet(value)
        }

    override var cmd: Int
        get() = flagsAndWord.cmd
        set(value) {
            flagsAndWord = flagsAndWord.withCmd(value)
        }

    override var errorCode: Int
        get() = flagsAndWord.errorCode
        set(value) {
            flagsAndWord = flagsAndWord.withErrorCode(value)
        }

    var payloadProvider: PayloadProvider = PayloadProvider.emptyPayload()

    override suspend fun acquirePayload(): AdbInputChannel {
        return payloadProvider.acquirePayload()
    }

    override fun releasePayload() {
        payloadProvider.releasePayload()
    }

    override fun toString(): String {
        return toStringImpl()
    }

    fun setCommand(cmdSet: Int, cmd: Int) {
        flagsAndWord = flagsAndWord.withCommand(cmdSet, cmd)
    }

    fun setReply(errorCode: Int) {
        flagsAndWord = flagsAndWord.withErrorCode(errorCode)
    }

    companion object {

        /**
         * Creates a [MutableJdwpPacket] command packet that wraps the ByteBuffer
         */
        fun createCommandPacket(
            packetId: Int,
            cmdSet: Int,
            cmd: Int,
            payload: ByteBuffer
        ): MutableJdwpPacket {
            return MutableJdwpPacket().apply {
                this.id = packetId
                this.length = JdwpPacketConstants.PACKET_HEADER_LENGTH + payload.remaining()
                this.setCommand(cmdSet, cmd)
                this.payloadProvider = PayloadProvider.forByteBuffer(payload)
            }
        }
    }
}
