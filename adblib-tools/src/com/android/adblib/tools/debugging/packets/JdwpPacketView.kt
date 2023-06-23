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
import com.android.adblib.tools.debugging.impl.EphemeralJdwpPacket
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_HEADER_LENGTH
import com.android.adblib.tools.debugging.packets.ddms.withPayload
import com.android.adblib.utils.ResizableBuffer

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
    suspend fun releasePayload()

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
