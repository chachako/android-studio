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

import com.android.adblib.AdbInputChannel
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.packets.JdwpCommands
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes.Companion.VURTOpCode
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes.Companion.chunkTypeToString
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants
import com.android.adblib.tools.debugging.packets.ddms.JdwpPacketFactory
import com.android.adblib.tools.debugging.packets.ddms.clone
import com.android.adblib.tools.debugging.packets.ddms.ddmsChunks
import com.android.adblib.tools.debugging.packets.ddms.throwFailException
import com.android.adblib.tools.debugging.packets.payloadLength
import com.android.adblib.withPrefix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.nio.ByteBuffer

/**
 * Progress reporting when executing a DDMS command.
 */
interface DdmsCommandProgress {

    suspend fun beforeSend(packet: JdwpPacketView) {}
    suspend fun afterSend(packet: JdwpPacketView) {}
    suspend fun onReply(packet: JdwpPacketView) {}
}

/**
 * Sends a [DDMS EXIT][DdmsChunkTypes.EXIT] [packet][DdmsChunkView] to
 * the JDWP process corresponding to this [JDWP session][SharedJdwpSession].
 */
suspend fun SharedJdwpSession.sendDdmsExit(status: Int) {
    val buffer = allocateDdmsPayload(4) // [pos = 0, limit =4]
    buffer.putInt(status) // [pos = 4, limit =4]
    buffer.flip()  // [pos = 0, limit =4]
    val packet = createDdmsPacket(DdmsChunkTypes.EXIT, buffer)
    sendPacket(packet)
}

/**
 * Sends a [DDMS HPGC][DdmsChunkTypes.HPGC] [packet][DdmsChunkView] to
 * the JDWP process corresponding to this [JDWP session][SharedJdwpSession].
 */
suspend fun SharedJdwpSession.handleDdmsHPGC(progress: DdmsCommandProgress? = null) {
    val logger = thisLogger(session)

    //   Debugger        |  AndroidVM
    //   ----------------+-----------------------------------------
    //   HPGC command => |
    //                   | (Invokes GC
    //                   )
    //                   | <=  Success: JDWP reply (empty payload)
    //                   | OR
    //                   | <=  Error: JDWP reply with FAIL chunk
    logger.debug { "Invoking HPGC (garbage collect) on process $pid" }

    val requestPacket = createDdmsPacket(DdmsChunkTypes.HPGC, emptyByteBuffer)
    newPacketReceiver()
        .withName("handleDdmsHPGC")
        .onActivation {
            progress?.beforeSend(requestPacket)
            sendPacket(requestPacket)
            progress?.afterSend(requestPacket)
        }
        .flow()
        .first { packet ->
            logger.verbose { "Receiving packet: $packet" }

            // Stop when we receive the reply to our MPSS command
            val isReply = (packet.isReply && packet.id == requestPacket.id)
            if (isReply) {
                logger.debug { "Received reply to HPGC (garbage collect) for process $pid" }
                progress?.onReply(packet)
                handleDdmsReplyPacket(packet, DdmsChunkTypes.HPGC)
            }
            isReply
        }
}

/**
 * Handles a reply from DDMS command.
 * Either we received an empty JDWP reply (for success), or we receive
 * JDWP reply containing a [DdmsChunkTypes.FAIL] [DdmsChunkView] in case of error.
 *
 * See [VMDebug.java](https://cs.android.com/android/platform/superproject/+/c26d2480913a2afda0e87cf978a10beb3109980a:libcore/dalvik/src/main/java/dalvik/system/VMDebug.java;l=313)
 * for an example of error, and see [DdmHandleProfiling.java](https://cs.android.com/android/platform/superproject/+/c26d2480913a2afda0e87cf978a10beb3109980a:frameworks/base/core/java/android/ddm/DdmHandleProfiling.java;l=119)
 * for an example on how this error is returned as a FAIL chunk.
 */
suspend fun SharedJdwpSession.handleDdmsReplyPacket(packet: JdwpPacketView, chunkType: Int) {
    val logger = thisLogger(session)

    // Error: FAIL packet
    packet.getDdmsFail()?.also { failChunk ->
        // An example of failure:
        // The 'MPSS' command can fail with "buffer size < 1024: xxx"
        // See https://cs.android.com/android/platform/superproject/+/4794e479f4b485be2680e83993e3cf93f0f42d03:libcore/dalvik/src/main/java/dalvik/system/VMDebug.java;l=318
        logger.info { "DDMS command '${chunkTypeToString(chunkType)} ' failed ($failChunk)" }
        failChunk.throwFailException()
    }

    // Success: Most (all?) DDMS commands send an empty reply when successful
    if (packet.isEmptyDdmsPacket) {
        // Nothing to do
        logger.debug { "DDMS command '${chunkTypeToString(chunkType)}' succeeded ($packet)" }
    } else {
        // Unexpected reply format
        logger.debug { "Format of reply to DDMS command '${chunkTypeToString(chunkType)}' is not supported ($packet)" }
        throw DdmsCommandException("Format of reply to DDMS command '${chunkTypeToString(chunkType)}' is not supported")
    }
}

private val JdwpPacketView.isEmptyDdmsPacket
    get() = (payloadLength == 0)

private suspend fun JdwpPacketView.getDdmsFail(): DdmsChunkView? {
    return ddmsChunks()
        .filter {
            it.type == DdmsChunkTypes.FAIL
        }.map {
            it.clone()
        }.firstOrNull()
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
        val packet = createDdmsPacket(chunkType, payload)
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
    val logger = thisLogger(this.session)

    if (!commandPacket.isCommand) {
        throw IllegalArgumentException("JDWP packet is not a command packet")
    }

    return newPacketReceiver()
        .withName("JDWP Command: $commandPacket")
        .onActivation {
            logger.debug { "Sending command packet and waiting for reply: $commandPacket" }
            sendPacket(commandPacket)
        }
        .flow()
        .filter { replyPacket ->
            logger.verbose { "Filtering packet from session: $replyPacket" }
            val isReply = replyPacket.isReply && replyPacket.id == commandPacket.id
            if (isReply) {
                logger.debug { "Received reply '$replyPacket' for command packet '$commandPacket'" }
            }
            isReply
        }
        .map { replyPacket ->
            // Note: The packet "payload" is still connected to the underlying
            // socket at this point. We don't clone it in memory so that the
            // caller can decide how to handle potentially large data sets.
            replyHandler(replyPacket)
        }.first() // There is only one reply packet
}

suspend fun AdbInputChannel.toByteBuffer(size: Int): ByteBuffer {
    val result = ByteBuffer.allocate(size) // [0, size]
    readExactly(result) // [size, size]
    result.flip() // [0, size]
    return result
}

fun SharedJdwpSession.createDdmsPacket(chunkType: Int, chunkPayload: ByteBuffer): JdwpPacketView {
    return JdwpPacketFactory.createDdmsPacket(nextPacketId(), chunkType, chunkPayload)
        .also { packet ->
            val logger = thisLogger(session)
            logger.verbose { "Sending DDMS command '${chunkTypeToString(chunkType)}' in JDWP packet $packet" }
        }
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
