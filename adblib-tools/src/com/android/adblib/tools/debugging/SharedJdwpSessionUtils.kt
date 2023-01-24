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
import com.android.adblib.tools.debugging.packets.ddms.DdmsFailException
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants
import com.android.adblib.tools.debugging.packets.ddms.JdwpPacketFactory
import com.android.adblib.tools.debugging.packets.ddms.clone
import com.android.adblib.tools.debugging.packets.ddms.ddmsChunks
import com.android.adblib.tools.debugging.packets.ddms.throwFailException
import com.android.adblib.tools.debugging.packets.payloadLength
import com.android.adblib.withPrefix
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.nio.ByteBuffer

/**
 * Progress reporting when executing a JDWP/DDMS command.
 */
interface JdwpCommandProgress {

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
suspend fun SharedJdwpSession.handleDdmsHPGC(progress: JdwpCommandProgress? = null) {
    val requestPacket = createDdmsPacket(DdmsChunkTypes.HPGC, emptyByteBuffer)
    handleEmptyReplyDdmsCommand(requestPacket, DdmsChunkTypes.HPGC, progress)
}

/**
 * ## Allocation tracker
 *
 * Sends a `REAE` (REcent Allocation Enable) request to the client.
 *
 * [Android "N" Source code](https://cs.android.com/android/platform/superproject/+/nougat-cts-release:frameworks/base/core/java/android/ddm/DdmHandleHeap.java;l=230)
 *
 * Note: This API has been deprecated and removed in later versions of Android.
 */
suspend fun SharedJdwpSession.handleDdmsREAE(
    enabled: Boolean,
    progress: JdwpCommandProgress? = null
) {
    val payload = allocateDdmsPayload(1)  // [pos=0, limit=1]
    payload.putBooleanByte(enabled)   // [pos=1, limit=1]
    payload.flip()  // [pos=0, limit=1]

    val requestPacket = createDdmsPacket(DdmsChunkTypes.REAE, payload)
    handleEmptyReplyDdmsCommand(requestPacket, DdmsChunkTypes.REAE, progress)
}

/**
 * ## Allocation tracker
 *
 * Sends a REAQ (REcent Allocation Query) request to the client.
 *
 * [Android "N" Source code](https://cs.android.com/android/platform/superproject/+/nougat-cts-release:frameworks/base/core/java/android/ddm/DdmHandleHeap.java;l=230)
 *
 * Note: This API has been deprecated and removed in later versions of Android.
 */
suspend fun SharedJdwpSession.handleDdmsREAQ(progress: JdwpCommandProgress? = null): Boolean {
    return handleDdmsCommand(DdmsChunkTypes.REAQ, emptyByteBuffer, progress) { chunkReply ->
        val buffer = chunkReply.payload.toByteBuffer(chunkReply.length)
        buffer.get() != 0.toByte()
    }
}

/**
 * ## Allocation tracker
 *
 * Sends a REAL (REcent Allocation List) request to the client.
 *
 * [Android "N" Source code](https://cs.android.com/android/platform/superproject/+/nougat-cts-release:frameworks/base/core/java/android/ddm/DdmHandleHeap.java;l=258)
 *
 * Note: This API has been deprecated and removed in later versions of Android.
 */
suspend fun <R> SharedJdwpSession.handleDdmsREAL(
    progress: JdwpCommandProgress? = null,
    replyHandler: suspend (data: AdbInputChannel, length: Int) -> R
): R {
    return handleDdmsCommand(DdmsChunkTypes.REAL, emptyByteBuffer, progress) {
        replyHandler(it.payload, it.length)
    }
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
 * the ddms command and [payload] as the command payload, and invokes [replyHandler]
 * with the [DdmsChunkView] corresponding to the reply from the Android VM.
 *
 * @throws DdmsFailException if the JDWP reply packet contains a [DdmsChunkTypes.FAIL] chunk
 * @throws DdmsCommandException if there is an unexpected DDMS/JDWP protocol error
 */
suspend fun <R> SharedJdwpSession.handleDdmsCommand(
    chunkType: Int,
    payload: ByteBuffer,
    progress: JdwpCommandProgress? = null,
    replyHandler: suspend (DdmsChunkView) -> R
): R {
    val commandPacket = createDdmsPacket(chunkType, payload)
    return handleJdwpCommand(commandPacket, progress) { replyPacket ->
        handleDdmsReplyPacket(replyPacket, chunkType) {
            replyHandler(it)
        }
    }
}

private suspend fun <R> SharedJdwpSession.handleDdmsReplyPacket(
    packet: JdwpPacketView,
    chunkType: Int,
    block: suspend (packet: DdmsChunkView) -> R
): R {
    val logger = thisLogger(session)
    val chunkTypeString = chunkTypeToString(chunkType)

    // Error: FAIL packet
    packet.getDdmsFail()?.also { failChunk ->
        // An example of failure:
        // The 'MPSS' command can fail with "buffer size < 1024: xxx"
        // See https://cs.android.com/android/platform/superproject/+/4794e479f4b485be2680e83993e3cf93f0f42d03:libcore/dalvik/src/main/java/dalvik/system/VMDebug.java;l=318
        logger.info { "DDMS command '$chunkTypeString' failed ($failChunk)" }
        failChunk.throwFailException()
    }

    return packet.ddmsChunks().map { replyChunk ->
        if (replyChunk.type != chunkType) {
            val message = "DDMS reply '${chunkTypeToString(replyChunk.type)}' " +
                    "does not match DDMS command '$chunkTypeString'"
            logger.warn(message)
            throw DdmsCommandException(message)
        }
        logger.debug { "Received reply to $chunkTypeString for process $pid (payload length=${replyChunk.length})" }
        block(replyChunk)
    }.firstOrNull() ?: run {
        val message = "Unexpected empty reply to DDMS command '$chunkTypeString'"
        logger.warn(message)
        throw DdmsCommandException(message)
    }
}

/**
 * Sends a DDMS command that returns either an empty reply (on success) or a `FAIL` message
 * (on failure).
 *
 * ```
 *   Debugger        |  AndroidVM
 *   ----------------+-----------------------------------------
 *   DDMS command => |
 *                   | (Executes DDMS commands)
 *                   | <=  Success: JDWP reply (empty payload)
 *                   | OR
 *                   | <=  Error: JDWP reply with FAIL chunk
 * ```
 *
 * @throws DdmsFailException if the JDWP reply packet contains a [DdmsChunkTypes.FAIL] chunk
 * @throws DdmsCommandException if there is an unexpected DDMS/JDWP protocol error
 */
suspend fun SharedJdwpSession.handleEmptyReplyDdmsCommand(
    requestPacket: JdwpPacketView,
    chunkType: Int,
    progress: JdwpCommandProgress?
) {
    val logger = thisLogger(session).withPrefix("pid=$pid: ")
    val chunkTypeString = chunkTypeToString(chunkType)

    logger.debug { "Invoking DDMS command $chunkTypeString" }

    newPacketReceiver()
        .withName("handleEmptyReplyDdmsCommand($chunkTypeString)")
        .onActivation {
            progress?.beforeSend(requestPacket)
            sendPacket(requestPacket)
            progress?.afterSend(requestPacket)
        }
        .flow()
        .first { packet ->
            logger.verbose { "Receiving packet: $packet" }

            // Stop when we receive the reply to our DDMS command
            val isReply = (packet.isReply && packet.id == requestPacket.id)
            if (isReply) {
                progress?.onReply(packet)
                handleEmptyDdmsReplyPacket(packet, chunkType)
            }
            isReply
        }
}

private suspend fun SharedJdwpSession.handleEmptyDdmsReplyPacket(packet: JdwpPacketView, chunkType: Int) {
    val logger = thisLogger(session).withPrefix("pid=$pid: ")
    val chunkTypeString = chunkTypeToString(chunkType)

    // Error: FAIL packet
    packet.getDdmsFail()?.also { failChunk ->
        // An example of failure:
        // The 'MPSS' command can fail with "buffer size < 1024: xxx"
        // See https://cs.android.com/android/platform/superproject/+/4794e479f4b485be2680e83993e3cf93f0f42d03:libcore/dalvik/src/main/java/dalvik/system/VMDebug.java;l=318
        logger.info { "DDMS command '$chunkTypeString' failed ($failChunk)" }
        failChunk.throwFailException()
    }

    // Success: Many DDMS commands send an empty JDWP reply when successful
    if (packet.isEmptyDdmsPacket) {
        logger.debug { "DDMS command '$chunkTypeString' succeeded with an empty payload (as expected)" }
        // Nothing to do
    } else {
        // Unexpected reply format
        val message = "The reply to the DDMS command '$chunkTypeString' is expected to be empty, but contained ${packet.payloadLength} bytes instead"
        logger.warn(message)
        throw DdmsCommandException(message)
    }
}

/**
 * Exception thrown when there is an error in the JDWP/DDMS protocol, i.e.
 * a packet reply format is invalid or unexpected.
 */
class DdmsCommandException(message: String, cause: Exception? = null) : Exception(message, cause)

/**
 * Sends a [JDWP command packet][JdwpPacketView] to the process corresponding to this
 * [JDWP session][SharedJdwpSession], and invokes [replyHandler] then the
 * [reply packet][JdwpPacketView] is received.
 */
suspend fun <R> SharedJdwpSession.handleJdwpCommand(
    commandPacket: JdwpPacketView,
    progress: JdwpCommandProgress?,
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
            progress?.beforeSend(commandPacket)
            sendPacket(commandPacket)
            progress?.afterSend(commandPacket)
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
            progress?.onReply(replyPacket)
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

suspend fun AdbInputChannel.toByteArray(size: Int): ByteArray {
    val result = ByteArray(size)
    val buffer = ByteBuffer.wrap(result) // [0, size]
    readExactly(buffer) // [size, size]
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

private fun ByteBuffer.putBooleanByte(value: Boolean) {
    put(if (value) 1 else 0)
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
