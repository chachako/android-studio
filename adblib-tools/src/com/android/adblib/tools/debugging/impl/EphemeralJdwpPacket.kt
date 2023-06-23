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
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Base class for [JdwpPacketView] implementation that are immutable and wrap the
 * [JdwpPacketView] `payload` with a [PayloadProvider] instance.
 */
internal abstract class AbstractJdwpPacketView protected constructor(
    final override val id: Int,
    final override val length: Int,
    /**
     * Instead of using 4 integer fields for [flags], [cmdSet], [cmd] and [errorCode], we pack
     * them into the 24 lower bits of this field:
     * * Bits [[16-23]] for [flags], and
     * * Bits [[0-15]] for [cmdSet] (8-bit) and [cmd] (8-bit), or [errorCode] (16-bit)
     *
     * Note: This matches the packing format used in the JDWP protocol.
     */
    protected val flagsAndWord: Int,
    protected val payloadProvider: PayloadProvider
) : JdwpPacketView, AutoCloseable {

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

    override suspend fun acquirePayload(): AdbInputChannel {
        return payloadProvider.acquirePayload()
    }

    override suspend fun releasePayload() {
        payloadProvider.releasePayload()
    }

    abstract override suspend fun toOffline(workBuffer: ResizableBuffer): JdwpPacketView

    override fun close() {
        payloadProvider.close()
    }

    override fun toString(): String {
        return toStringImpl()
    }
}

/**
 * A [JdwpPacketView] implementation that is immutable and wraps the [JdwpPacketView] `payload`
 * with a [PayloadProvider] instance.
 *
 * Producers of [EphemeralJdwpPacket] instances call [shutdown] when a packet payload is
 * not available anymore (e.g. when the underlying socket data has been consumed), at which
 * point consumers won't have access to the packet payload, i.e. [JdwpPacketView.withPayload]
 * throws an [IllegalStateException].
 *
 * Note: This implementation is *not* thread-safe.
 */
internal open class EphemeralJdwpPacket private constructor(
    id: Int,
    length: Int,
    flagsAndWord: Int,
    payloadProvider: PayloadProvider
) : AbstractJdwpPacketView(id, length, flagsAndWord, payloadProvider), AutoCloseable {

    private constructor(
        id: Int,
        length: Int,
        flags: Int,
        cmdSet: Int,
        cmd: Int,
        errorCode: Int,
        payloadProvider: PayloadProvider
    ): this(id, length, makeFlagsAndWord(flags, cmdSet, cmd, errorCode), payloadProvider) {
        require(length >= PACKET_HEADER_LENGTH) { "length  value '$flags' should be within greater than $PACKET_HEADER_LENGTH" }
        require(flags in 0..255) { "flags value '$flags' should be within the [0..255] range" }
        require(cmdSet in 0..255) { "cmdSet value '$cmdSet' should be within the [0..255] range" }
        require(cmd in 0..255) { "cmd value '$cmd' should be within the [0..255] range" }
        require(errorCode in 0..65535) { "errorCode value '$errorCode' should be within the [0..65535] range" }
    }

    override suspend fun toOffline(workBuffer: ResizableBuffer): JdwpPacketView {
        // Returns an offline, thread-safe version of this instance
        return OfflineJdwpPacket(
            id = id,
            length = length,
            flagsAndWord = flagsAndWord,
            payloadProvider = payloadProvider.toOffline(workBuffer)
        )
    }

    open suspend fun shutdown(workBuffer: ResizableBuffer = ResizableBuffer()) {
        payloadProvider.shutdown(workBuffer)
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
                payloadProvider = PayloadProvider.forInputChannel(payload)
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

    /**
     * An implementation of [JdwpPacketView] that is immutable and offer thread-safe access to
     * [withPayload].
     */
    private class OfflineJdwpPacket(
        id: Int,
        length: Int,
        flagsAndWord: Int,
        payloadProvider: PayloadProvider
    ) : AbstractJdwpPacketView(id, length, flagsAndWord, payloadProvider) {

        private val payloadMutex = Mutex()

        override suspend fun acquirePayload(): AdbInputChannel {
            return payloadMutex.withLock {
                super.acquirePayload()
            }
        }

        override suspend fun releasePayload() {
            return payloadMutex.withLock {
                super.releasePayload()
            }
        }

        override suspend fun toOffline(workBuffer: ResizableBuffer): OfflineJdwpPacket {
            return this
        }
    }
}
