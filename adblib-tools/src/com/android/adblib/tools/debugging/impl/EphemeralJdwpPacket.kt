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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.AdbInputChannel
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_HEADER_LENGTH
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.PayloadProvider
import com.android.adblib.tools.debugging.packets.toStringImpl
import com.android.adblib.tools.debugging.packets.withPayload

/**
 * A [JdwpPacketView] implementation that is immutable and wraps the [JdwpPacketView] payload
 * with an [AdbBufferedInputChannel] so that [JdwpPacketView.withPayload] can be called
 * an arbitrary number of times.
 *
 * Producers of [EphemeralJdwpPacket] instances call [shutdown] when a packet payload is
 * not available anymore (e.g. when the underlying socket data has been consumed), at which
 * point consumers won't have access to the packet payload, i.e. [JdwpPacketView.withPayload]
 * throws an [IllegalStateException].
 *
 * Note: This implementation is *not* thread-safe.
 */
class EphemeralJdwpPacket(
    override val id: Int,
    override val length: Int,
    flags: Int,
    cmdSet: Int,
    cmd: Int,
    errorCode: Int,
    private val payloadProvider: PayloadProvider
) : JdwpPacketView, PayloadProvider by payloadProvider {

    init {
        require(length >= PACKET_HEADER_LENGTH) { "length  value '$flags' should be within greater than $PACKET_HEADER_LENGTH" }
        require(flags in 0..255) { "flags value '$flags' should be within the [0..255] range" }
        require(cmdSet in 0..255) { "cmdSet value '$cmdSet' should be within the [0..255] range" }
        require(cmd in 0..255) { "cmd value '$cmd' should be within the [0..255] range" }
        require(errorCode in 0..65535) { "errorCode value '$errorCode' should be within the [0..65535] range" }
    }

    /**
     * Instead of using 4 integer fields for [flags], [cmdSet], [cmd] and [errorCode], we pack
     * them into the 24 lower bits of this field:
     * * Bits [[16-23]] for [flags], and
     * * Bits [[0-15]] for [cmdSet] (8-bit) and [cmd] (8-bit), or [errorCode] (16-bit)
     *
     * Note: This matches the packing format used in the JDWP protocol.
     */
    private val flagsAndWord: Int = makeFlagsAndWord(flags, cmdSet, cmd, errorCode)

    override val flags: Int
        get() = (flagsAndWord and 0xff0000) shr 16

    override val cmdSet: Int
        get() {
            check(isCommand) { "CmdSet is not available because JDWP packet is a reply packet" }
            return (flagsAndWord and 0xff00) shr 8
        }

    override val cmd: Int
        get() {
            check(isCommand) { "Cmd is not available because JDWP packet is a reply packet" }
            return flagsAndWord and 0x00ff
        }

    override val errorCode: Int
        get() {
            check(isReply) { "ErrorCode is not available because JDWP packet is a command packet" }
            return flagsAndWord and 0xffff
        }

    override fun toString(): String {
        return toStringImpl()
    }

    companion object {

        private fun makeFlagsAndWord(flags: Int, cmdSet: Int, cmd: Int, errorCode: Int): Int {
            return if ((flags and JdwpPacketConstants.REPLY_PACKET_FLAG) == 0) {
                (flags.toUByte().toInt() shl 16) or (cmdSet.toUByte()
                    .toInt() shl 8) or cmd.toUByte().toInt()
            } else {
                (flags.toUByte().toInt() shl 16) or (errorCode.toUShort().toInt())
            }
        }

        @Suppress("FunctionName") // constructor like syntax
        fun Command(id: Int, length: Int, cmdSet: Int, cmd: Int, payloadProvider: PayloadProvider): EphemeralJdwpPacket {
            return EphemeralJdwpPacket(
                id = id,
                length = length,
                flags = 0,
                cmdSet = cmdSet,
                cmd = cmd,
                errorCode = 0,
                payloadProvider = payloadProvider
            )
        }

        @Suppress("FunctionName") // constructor like syntax
        fun Reply(id: Int, length: Int, errorCode: Int, payloadProvider: PayloadProvider): EphemeralJdwpPacket {
            return EphemeralJdwpPacket(
                id = id,
                length = length,
                flags = JdwpPacketConstants.REPLY_PACKET_FLAG,
                cmdSet = 0,
                cmd = 0,
                errorCode = errorCode,
                payloadProvider = payloadProvider
            )
        }

        fun fromPacket(source: JdwpPacketView, payload: AdbInputChannel): EphemeralJdwpPacket {
            return fromPacket(
                source = source,
                payload = AdbBufferedInputChannel.forInputChannel(payload)
            )
        }

        fun fromPacket(source: JdwpPacketView, payload: AdbBufferedInputChannel): EphemeralJdwpPacket {
            return fromPacket(
                source = source,
                payloadProvider = PayloadProvider.forBufferedInputChannel(payload)
            )
        }

        fun fromPacket(source: JdwpPacketView, payloadProvider: PayloadProvider): EphemeralJdwpPacket {
            return if (source.isCommand) {
                Command(
                    id = source.id,
                    length = source.length,
                    cmdSet = source.cmdSet,
                    cmd = source.cmd,
                    payloadProvider = payloadProvider
                )
            } else {

                Reply(
                    id = source.id,
                    length = source.length,
                    errorCode = source.errorCode,
                    payloadProvider = payloadProvider
                )
            }
        }
    }
}
