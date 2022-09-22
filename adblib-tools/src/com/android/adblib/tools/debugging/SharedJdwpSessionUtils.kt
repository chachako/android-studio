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
package com.android.adblib.tools.debugging

import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpCommands
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes.Companion.VURTOpCode
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes.Companion.chunkTypeToString
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants
import com.android.adblib.tools.debugging.packets.ddms.JdwpPacketFactory
import com.android.adblib.tools.debugging.packets.ddms.ddmsChunks
import com.android.adblib.withPrefix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.nio.ByteBuffer

/**
 * Sends a [DDMS EXIT][DdmsChunkTypes.EXIT] [packet][DdmsChunkView] to
 * the JDWP process corresponding to this [JDWP session][SharedJdwpSession].
 */
suspend fun SharedJdwpSession.sendDdmsExit(status: Int) {
    val buffer = ByteBuffer.allocate(4) // [pos = 0, limit =4]
    buffer.putInt(status) // [pos = 4, limit =4]
    buffer.flip()  // [pos = 0, limit =4]
    val packet = JdwpPacketFactory.createDdmsPacket(nextPacketId(), DdmsChunkTypes.EXIT, buffer)
    sendPacket(packet)
}

/**
 * Sends a [VM EXIT][JdwpCommands.VmCmd.CMD_VM_EXIT] JDWP command [packet][JdwpPacketView] to
 * the process corresponding to this [JDWP session][SharedJdwpSession].
 */
suspend fun SharedJdwpSession.sendVmExit(status: Int) {
    val buffer = ByteBuffer.allocate(4) // [pos = 0, limit = 4]
    buffer.putInt(status) // [pos = 4, limit = 4]
    buffer.flip()  // [pos = 0, limit = 4]

    val packet = MutableJdwpPacket.createCommandPacket(
        nextPacketId(),
        JdwpCommands.CmdSet.SET_VM.value,
        JdwpCommands.VmCmd.CMD_VM_EXIT.value,
        buffer
    )
    sendPacket(packet)
}

suspend fun <R> SharedJdwpSession.handleDdmsListViewRoots(replyHandler: suspend (DdmsChunkView) -> R): R {
    return handleDdmsCommand(DdmsChunkTypes.VULW, emptyByteBuffer) {
        replyHandler(it)
    }
}

suspend fun <R> SharedJdwpSession.handleDdmsDumpViewHierarchy(
    viewRoot: String,
    skipChildren: Boolean,
    includeProperties: Boolean,
    useV2: Boolean,
    replyHandler: suspend (DdmsChunkView) -> R
): R {
    val chunkBuf = allocateDdmsPayload(
        4 // opcode
        + 4 + viewRoot.length * 2 // view root (length + unicode string)
        + 4 // skip children
        + 4 // include view properties
        + 4 // use Version 2
    )

    // Op code
    chunkBuf.putInt(VURTOpCode.VURT_DUMP_HIERARCHY.value)

    // View Root name
    chunkBuf.putString(viewRoot)

    // Options
    chunkBuf.putBooleanInt(skipChildren)
    chunkBuf.putBooleanInt(includeProperties)
    chunkBuf.putBooleanInt(useV2)

    chunkBuf.flip()
    assert(chunkBuf.position() == 0)
    assert(chunkBuf.limit() == chunkBuf.capacity())

    return handleDdmsCommand(DdmsChunkTypes.VURT, chunkBuf) {
        replyHandler(it)
    }
}

suspend fun <R> SharedJdwpSession.handleDdmsCaptureView(
    viewRoot: String,
    view: String,
    replyHandler: suspend (DdmsChunkView) -> R
): R {
    val chunkBuf = allocateDdmsPayload(
        4 +  // opcode
        4 + viewRoot.length * 2 +  // view root strlen + view root
        4 + view.length * 2 // view strlen + view
    )

    // Op code
    chunkBuf.putInt(DdmsChunkTypes.Companion.VUOPOpCode.VUOP_CAPTURE_VIEW.value)
    chunkBuf.putString(viewRoot)
    chunkBuf.putString(view)

    chunkBuf.flip()
    assert(chunkBuf.position() == 0)
    assert(chunkBuf.limit() == chunkBuf.capacity())

    return handleDdmsCommand(DdmsChunkTypes.VUOP, chunkBuf) {
        replyHandler(it)
    }
}

/**
 * Sends a [DdmsChunkView] command packet to this [SharedJdwpSession], using [chunkType] as
 * the ddms command and [payload] as the command payload, and returns a [DdmsChunkView]
 * corresponding to the reply from the Android VM.
 *
 * @throws DdmsCommandException if the DDMS command packet came back as a failure from
 * the AndroidVM.
 */
suspend fun <R> SharedJdwpSession.handleDdmsCommand(
    chunkType: Int,
    payload: ByteBuffer,
    replyHandler: suspend (DdmsChunkView) -> R
): R {
    return handleMultiDdmsCommand(chunkType, payload)
        .map { replyChunk ->
            if (replyChunk.type != chunkType) {
                throw DdmsCommandException("DDMS reply '${chunkTypeToString(replyChunk.type)}' does not match DDMS command '${chunkTypeToString(chunkType)}'")
            }
            // Note: The chunk "payload" is still connected to the underlying
            // socket at this point. We don't clone it in memory so that the
            // caller can decide how to handle potentially large data sets.
            replyHandler(replyChunk)
        }.firstOrNull()
        ?: run {
            // DDMS command failures come back as empty JDWP reply packets
            throw DdmsCommandException("DDMS command '${chunkTypeToString(chunkType)}' failed on device")
        }
}

class DdmsCommandException(message: String, cause: Exception? = null)  : Exception(message, cause)

/**
 * Sends a [DdmsChunkView] command packet to this [SharedJdwpSession], using [chunkType] as
 * the ddms command and [payload] as the command payload, and returns a [Flow] of [DdmsChunkView]
 * corresponding to the reply from the Android VM. Note that typically (if not always) the
 * returned [Flow] contains only one element.
 */
private fun SharedJdwpSession.handleMultiDdmsCommand(
    chunkType: Int,
    payload: ByteBuffer
): Flow<DdmsChunkView> {
    val logger = thisLogger(session).withPrefix("pid=$pid: handleMultiDdmsCommand - ")

    return flow {
        val packet = JdwpPacketFactory.createDdmsPacket(nextPacketId(), chunkType, payload)
        logger.verbose { "Sending DDMS command '${chunkTypeToString(chunkType)}' in JDWP packet $packet" }
        handleJdwpCommand(packet) { replyPacket ->
            logger.verbose { "Received reply packet: $replyPacket" }
            replyPacket.ddmsChunks().collect { replyChunk ->
                logger.verbose { "Received reply chunk: $replyChunk" }
                // Note: The chunk "payload" is still connected to the underlying
                // socket at this point. We don't clone it in memory so that the
                // caller can decide how to handle potentially large data sets.
                emit(replyChunk)
            }
        }
    }
}

/**
 * Sends a [JDWP command packet][JdwpPacketView] to the process corresponding to this
 * [JDWP session][SharedJdwpSession], and invokes [replyHandler] then the
 * [reply packet][JdwpPacketView] is received.
 */
suspend fun <R> SharedJdwpSession.handleJdwpCommand(
    commandPacket: JdwpPacketView,
    replyHandler: suspend (JdwpPacketView) -> R
): R {
    if (!commandPacket.isCommand) {
        throw IllegalArgumentException("JDWP packet is not a command packet")
    }

    return newPacketReceiver()
        .withName("JDWP Command: $commandPacket")
        .onActivation { sendPacket(commandPacket) }
        .flow()
        .filter { it.isReply && it.id == commandPacket.id }
        .map {
            // Note: The packet "payload" is still connected to the underlying
            // socket at this point. We don't clone it in memory so that the
            // caller can decide how to handle potentially large data sets.
            replyHandler(it)
        }
        .first() // There is only one reply packet
}

suspend fun AdbBufferedInputChannel.toByteBuffer(size: Int): ByteBuffer {
    val result = ByteBuffer.allocate(size) // [0, size]
    readExactly(result) // [size, size]
    result.flip() // [0, size]
    return result
}

private fun allocateDdmsPayload(length: Int): ByteBuffer {
    return ByteBuffer.allocate(length).order(DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER)
}

private val emptyByteBuffer: ByteBuffer = ByteBuffer.allocate(0)

private fun ByteBuffer.putString(value: String) {
    putInt(value.length)
    value.forEach { putChar(it) }
}

private fun ByteBuffer.putBooleanInt(value: Boolean) {
    putInt(if (value) 1 else 0)
}
