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
package com.android.adblib.tools.debugging.packets.impl

import com.android.adblib.AdbInputChannel
import com.android.adblib.tools.debugging.utils.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_HEADER_LENGTH
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.JdwpPacketView.Companion.FlagsAndWord
import com.android.adblib.tools.debugging.packets.toStringImpl
import com.android.adblib.tools.debugging.packets.withPayload
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.sync.Mutex

/**
 * Base class for [JdwpPacketView] implementation that are immutable and wrap the
 * [JdwpPacketView] `payload` with a [PayloadProvider] instance.
 */
internal abstract class AbstractJdwpPacketView protected constructor(
    final override val id: Int,
    final override val length: Int,
    protected val flagsAndWord: FlagsAndWord,
    protected val payloadProvider: PayloadProvider
) : JdwpPacketView, AutoCloseable {

    override val flags: Int
        get() = flagsAndWord.flags

    override val cmdSet: Int
        get() = flagsAndWord.cmdSet

    override val cmd: Int
        get() = flagsAndWord.cmd

    override val errorCode: Int
        get() = flagsAndWord.errorCode

    override suspend fun acquirePayload(): AdbInputChannel {
        return payloadProvider.acquirePayload()
    }

    override fun releasePayload() {
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
    flagsAndWord: FlagsAndWord,
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
    ): this(id, length, FlagsAndWord(flags, cmdSet, cmd, errorCode), payloadProvider) {
        require(length >= PACKET_HEADER_LENGTH) { "length  value '$flags' should be within greater than $PACKET_HEADER_LENGTH" }
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
        flagsAndWord: FlagsAndWord,
        payloadProvider: PayloadProvider
    ) : AbstractJdwpPacketView(id, length, flagsAndWord, payloadProvider) {

        private val payloadMutex = Mutex()

        override suspend fun acquirePayload(): AdbInputChannel {
            payloadMutex.lock()
            return super.acquirePayload()
        }

        override fun releasePayload() {
            super.releasePayload()
            payloadMutex.unlock()
        }

        override suspend fun toOffline(workBuffer: ResizableBuffer): OfflineJdwpPacket {
            return this
        }
    }
}
