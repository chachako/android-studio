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
package com.android.adblib.tools.debugging.packets

import com.android.adblib.AdbInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_HEADER_LENGTH
import com.android.adblib.tools.debugging.packets.ddms.withPayload
import com.android.adblib.tools.debugging.packets.impl.EphemeralJdwpPacket
import com.android.adblib.tools.debugging.packets.impl.JdwpCommands
import com.android.adblib.tools.debugging.packets.impl.JdwpErrorCode
import com.android.adblib.tools.debugging.packets.impl.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.impl.PayloadProvider
import com.android.adblib.tools.debugging.packets.impl.wrapByteBuffer
import com.android.adblib.tools.debugging.utils.ThreadSafetySupport
import com.android.adblib.utils.ResizableBuffer
import java.nio.ByteBuffer

/**
 * Provides access to various elements of a JDWP packet. A JDWP packet always starts with
 * an 11-byte header, followed by variable size buffer of [length] minus 11 bytes.
 */
interface JdwpPacketView {

    /**
     * The total number of bytes of this JDWP packet, including header (11 bytes) and [withPayload].
     */
    val length: Int

    /**
     * Packet unique identifier (4 bytes) with a given JDWP session
     */
    val id: Int

    /**
     * 8-bit flags of the packet, e.g. [JdwpPacketConstants.REPLY_PACKET_FLAG]
     */
    val flags: Int

    /**
     * The "command set" identifier (1 byte) if the packet is a [isCommand] packet, or
     * throws [IllegalStateException] otherwise.
     */
    val cmdSet: Int

    /**
     * The "command" identifier (1 byte) within the [cmdSet] if the packet is a
     * [isCommand] packet, or throws [IllegalStateException] otherwise.
     */
    val cmd: Int

    /**
     * The "error code" if the packet is a [isReply] packet, or throws [IllegalStateException]
     * otherwise.
     */
    val errorCode: Int

    /**
     * **Note: Do NOT use directly, use [withPayload] instead**
     *
     * Returns the payload of this [JdwpPacketView] as an [AdbInputChannel] instance.
     * [releasePayload] must be called when the returned [AdbInputChannel] is not used anymore.
     *
     * @throws IllegalStateException if the `payload` of this [JdwpPacketView] instance is not
     *  available anymore.
     * @see [PayloadProvider.acquirePayload]
     */
    suspend fun acquirePayload(): AdbInputChannel

    /**
     * **Note: Do NOT use directly, use [withPayload] instead**
     *
     * Releases the [AdbInputChannel] previously returned by [acquirePayload].
     *
     * @see [PayloadProvider.releasePayload]
     */
    fun releasePayload()

    /**
     * Creates an "offline" version of this [JdwpPacketView] that is thread-safe, immutable and has
     * an [withPayload] that is detached from any underlying volatile data source (e.g.
     * a network socket).
     */
    suspend fun toOffline(workBuffer: ResizableBuffer = ResizableBuffer()): JdwpPacketView

    /**
     * Returns `true` is the packet is a "command" packet matching the given [cmdSet] and [cmd]
     * values.
     */
    fun isCommand(cmdSet: Int, cmd: Int): Boolean {
        return isCommand && (this.cmdSet == cmdSet && this.cmd == cmd)
    }

    /**
     * Whether the packet is a "reply" packet (as opposed to a "command" packet)
     */
    val isReply: Boolean
        get() = (flags and JdwpPacketConstants.REPLY_PACKET_FLAG) != 0

    /**
     * Whether the packet is a "command" packet (as opposed to a "reply" packet)
     */
    val isCommand: Boolean
        get() = !isReply

    /**
     * Returns `true` if the packet [withPayload] is empty
     */
    val isEmpty: Boolean
        get() = length == PACKET_HEADER_LENGTH

    companion object {

        @Suppress("FunctionName") // constructor like syntax
        fun Command(
            id: Int,
            length: Int,
            cmdSet: Int,
            cmd: Int,
            payload: AdbInputChannel
        ): JdwpPacketView {
            return EphemeralJdwpPacket.Command(
                id,
                length,
                cmdSet,
                cmd,
                PayloadProvider.forInputChannel(payload)
            )
        }

        @Suppress("FunctionName") // constructor like syntax
        fun Reply(id: Int, length: Int, errorCode: Int, payload: AdbInputChannel): JdwpPacketView {
            return EphemeralJdwpPacket.Reply(
                id,
                length,
                errorCode,
                PayloadProvider.forInputChannel(payload)
            )
        }

        fun fromPacket(source: JdwpPacketView, payload: AdbInputChannel): JdwpPacketView {
            return if (source.isCommand) {
                Command(
                    id = source.id,
                    length = source.length,
                    cmdSet = source.cmdSet,
                    cmd = source.cmd,
                    payload
                )
            } else {
                Reply(
                    id = source.id,
                    length = source.length,
                    errorCode = source.errorCode,
                    payload
                )
            }
        }

        fun wrapByteBuffer(buffer: ByteBuffer): JdwpPacketView {
            return MutableJdwpPacket().also {
                it.wrapByteBuffer(buffer)
            }
        }

        /**
         * Inline value class that efficiently wraps the [JdwpPacketView.flags],
         * [JdwpPacketView.cmdSet], [JdwpPacketView.cmd] and [JdwpPacketView.errorCode] properties
         * into a single 32-bit integer value.
         */
        @Suppress("NOTHING_TO_INLINE")
        @JvmInline
        internal value class FlagsAndWord private constructor(
            /**
             * Pack [flags], [cmdSet], [cmd] and [errorCode] into the 24 lower bits of a 32-bit integer:
             * * Bits [[16-23]] for [flags], and
             * * Bits [[0-15]] for [cmdSet] (8-bit) and [cmd] (8-bit), or [errorCode] (16-bit)
             *
             * Note: This matches the packing format used in the JDWP protocol.
             */
            private val rawValue: Int
        ) {

            constructor() : this(0)

            constructor(flags: Int, cmdSet: Int, cmd: Int, errorCode: Int)
             : this (buildRawValue(flags, cmdSet, cmd, errorCode))

            private inline fun rawFlags(): Int = (rawValue and 0xff0000) shr 16

            private inline fun rawWord(): Int = (rawValue and 0xffff)

            inline val isCommand: Boolean
                get() = (rawFlags() and JdwpPacketConstants.REPLY_PACKET_FLAG) == 0

            inline val isReply: Boolean
                get() = !isCommand

            inline val flags: Int
                get() = rawFlags()

            inline val cmdSet: Int
                get() {
                    check(isCommand) { "CmdSet is not available because JDWP packet is a reply packet" }
                    return (rawWord() and 0xff00) shr 8
                }

            inline val cmd: Int
                get() {
                    check(isCommand) { "Cmd is not available because JDWP packet is a reply packet" }
                    return rawWord() and 0x00ff
                }

            inline val errorCode: Int
                get() {
                    check(isReply) { "ErrorCode is not available because JDWP packet is a command packet" }
                    return rawWord()
                }

            fun withFlags(flags: Int): FlagsAndWord {
                require(flags in 0..255) { "Flags value '$flags' should be within the [0..255] range" }
                return fromRawValues(flags, rawWord())
            }

            fun withIsCommand(isCommand: Boolean): FlagsAndWord {
                val newFlags = if (isCommand) {
                    rawFlags() and JdwpPacketConstants.REPLY_PACKET_FLAG.inv()
                } else {
                    rawFlags() or JdwpPacketConstants.REPLY_PACKET_FLAG
                }
                return fromRawValues(newFlags, rawWord())
            }

            fun withIsReply(isReply: Boolean): FlagsAndWord {
                return withIsCommand(!isReply)
            }

            fun withCommand(cmdSet: Int, cmd: Int): FlagsAndWord {
                return withCmdSet(cmdSet).withCmd(cmd)
            }

            fun withCmdSet(cmdSet: Int): FlagsAndWord {
                require(cmdSet in 0..255) { "CmdSet value '$cmdSet' should be within the [0..255] range" }
                return fromRawValues(
                    rawFlags = rawFlags() and JdwpPacketConstants.REPLY_PACKET_FLAG.inv(),
                    rawWord = (rawWord() and 0x00ff) or (cmdSet shl 8)
                )
            }

            fun withCmd(cmd: Int): FlagsAndWord {
                require(cmd in 0..255) { "Cmd value '$cmd' should be within the [0..255] range" }
                return fromRawValues(
                    rawFlags = rawFlags() and JdwpPacketConstants.REPLY_PACKET_FLAG.inv(),
                    rawWord = (rawWord() and 0xff00) or cmd
                )
            }

            fun withErrorCode(errorCode: Int): FlagsAndWord {
                require(errorCode in 0..65535) { "ErrorCode value '$errorCode' should be within the [0..65535] range" }
                return fromRawValues(
                    rawFlags = rawFlags() or JdwpPacketConstants.REPLY_PACKET_FLAG,
                    rawWord = errorCode
                )
            }

            companion object {

                private inline fun fromRawValues(rawFlags: Int, rawWord: Int): FlagsAndWord {
                    assert(rawFlags in 0..255)
                    assert(rawWord in 0..65535)
                    return FlagsAndWord((rawFlags shl 16) or rawWord)
                }

                private fun buildRawValue(flags: Int, cmdSet: Int, cmd: Int, errorCode: Int): Int {
                    require(flags in 0..255) {
                        "Flags value '$flags' should be within the [0..255] range"
                    }
                    require(cmdSet in 0..255) {
                        "CmdSet value '$cmdSet' should be within the [0..255] range"
                    }
                    require(cmd in 0..255) {
                        "Cmd value '$cmd' should be within the [0..255] range"
                    }
                    require(errorCode in 0..65535) {
                        "ErrorCode value '$errorCode' should be within the [0..65535] range"
                    }
                    return if ((flags and JdwpPacketConstants.REPLY_PACKET_FLAG) == 0) {
                        (flags shl 16) or (cmdSet shl 8) or cmd
                    } else {
                        (flags shl 16) or (errorCode)
                    }
                }
            }
        }
    }
}

val JdwpPacketView.payloadLength
    get() = length - PACKET_HEADER_LENGTH

/**
 * Invokes [block] with payload of this [JdwpPacketView]. The payload is passed to [block]
 * as an [AdbInputChannel] instance that is valid only during the [block] invocation.
 *
 * @throws IllegalStateException if the `payload` of this [JdwpPacketView] instance is not
 *  available anymore.
 */
suspend inline fun <R> JdwpPacketView.withPayload(block: (AdbInputChannel) -> R): R {
    val payload = acquirePayload()
    return try {
        block(payload)
    } finally {
        releasePayload()
    }
}

/**
 * Returns whether this [JdwpPacketView] is thread-safe and immutable, meaning it can be
 * safely shared across threads and coroutines.
 *
 * @see ThreadSafetySupport.isThreadSafeAndImmutable
 */
internal val JdwpPacketView.isThreadSafeAndImmutable: Boolean
    get() {
        return when (this) {
            is ThreadSafetySupport -> {
                isThreadSafeAndImmutable
            }

            else -> {
                false
            }
        }
    }

/**
 * Helper method for implementations for [JdwpPacketView]
 */
internal fun JdwpPacketView.toStringImpl(): String {
    return "%s(id=%d, length=%d, flags=0x%02X, %s)".format(
        this::class.simpleName,
        id,
        length,
        flags,
        if (isReply) {
            "isReply=true, errorCode=%s[%d]".format(
                JdwpErrorCode.errorName(errorCode),
                errorCode
            )
        } else {
            "isCommand=true, cmdSet=%s[%d], cmd=%s[%d]".format(
                JdwpCommands.cmdSetToString(cmdSet),
                cmdSet,
                JdwpCommands.cmdToString(cmdSet, cmd),
                cmd
            )
        }
    )
}
