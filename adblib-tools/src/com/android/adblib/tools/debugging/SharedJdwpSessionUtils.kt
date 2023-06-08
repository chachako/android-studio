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
import com.android.adblib.property
import com.android.adblib.thisLogger
import com.android.adblib.tools.AdbLibToolsProperties.DDMS_REPLY_WAIT_TIMEOUT
import com.android.adblib.tools.debugging.packets.JdwpCommands
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkType
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkType.Companion.MPRQ
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkType.Companion.VURTOpCode
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsFailException
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants
import com.android.adblib.tools.debugging.packets.ddms.JdwpPacketFactory
import com.android.adblib.tools.debugging.packets.ddms.clone
import com.android.adblib.tools.debugging.packets.ddms.ddmsChunks
import com.android.adblib.tools.debugging.packets.ddms.throwFailException
import com.android.adblib.tools.debugging.packets.ddms.withPayload
import com.android.adblib.tools.debugging.packets.payloadLength
import com.android.adblib.withPrefix
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.time.Duration
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * Progress reporting when executing a JDWP/DDMS command.
 */
interface JdwpCommandProgress {

    /**
     * Invoked just before sending the packet on the [SharedJdwpSession]
     */
    suspend fun beforeSend(packet: JdwpPacketView) {}

    /**
     * Invoked just after sending the packet on the [SharedJdwpSession]
     */
    suspend fun afterSend(packet: JdwpPacketView) {}

    /**
     * Invoked when the JDWP command was acknowledged with a [JdwpPacketView] reply packet
     */
    suspend fun onReply(packet: JdwpPacketView) {}

    /**
     * Invoked when there is no reply to be expected from the JDWP command
     * (see [DdmsProtocolKind])
     */
    suspend fun onReplyTimeout() {}
}

/**
 * Flags for [createDdmsMPSS]. These can be ORed together.
 */
enum class MPSSFlags(val flag: Int) {

    /**
     * TRACE_COUNT_ALLOCS adds the results from startAllocCounting to the
     * trace key file.
     */
    @Suppress("unused")
    @Deprecated("Accurate counting is a burden on the runtime and may be removed.")
    TRACE_COUNT_ALLOCS(1)
}

fun SharedJdwpSession.createDdmsMPSS(
    bufferSize: Int,
    flags: EnumSet<MPSSFlags> = EnumSet.noneOf(MPSSFlags::class.java)
): JdwpPacketView {
    val payload = allocateDdmsPayload(2 * 4)
    payload.putInt(bufferSize) // [pos=4, limit=8]
    payload.putInt(flags.fold(0) { acc, value -> acc + value.flag }) // [pos=8, limit=8]
    payload.flip()  // [pos=0, limit=8]
    return createDdmsPacket(DdmsChunkType.MPSS, payload)
}

fun SharedJdwpSession.createDdmsMPSE(): JdwpPacketView {
    return createDdmsPacket(DdmsChunkType.MPSE, emptyByteBuffer)
}

fun SharedJdwpSession.createDdmsSPSS(
    bufferSize: Int,
    samplingInterval: Long,
    samplingIntervalTimeUnits: TimeUnit
): JdwpPacketView {
    val interval = samplingIntervalTimeUnits.toMicros(samplingInterval).toInt()

    val payload = allocateDdmsPayload(4 + 4 + 4) // [pos=0, limit=12]
    payload.putInt(bufferSize) // [pos=4, limit=12]
    payload.putInt(0 /*flags*/) // [pos=8, limit=12]
    payload.putInt(interval) // [pos=12, limit=12]
    payload.flip() // [pos=0, limit=12]

    return createDdmsPacket(DdmsChunkType.SPSS, payload)
}

fun SharedJdwpSession.createDdmsSPSE(): JdwpPacketView {
    return createDdmsPacket(DdmsChunkType.SPSE, emptyByteBuffer)
}

/**
 * Sends a [DDMS EXIT][DdmsChunkType.EXIT] [packet][DdmsChunkView] to
 * the JDWP process corresponding to this [JDWP session][SharedJdwpSession].
 */
suspend fun SharedJdwpSession.sendDdmsExit(status: Int) {
    val buffer = allocateDdmsPayload(4) // [pos = 0, limit =4]
    buffer.putInt(status) // [pos = 4, limit =4]
    buffer.flip()  // [pos = 0, limit =4]
    val packet = createDdmsPacket(DdmsChunkType.EXIT, buffer)

    // Send packet and wait for EOF (i.e. wait for JDWP session to end when process terminates)
    newPacketReceiver()
        .withName("sendDdmsExit")
        .onActivation { sendPacket(packet) }
        .collect { }
}

/**
 * Sends a [DDMS HPGC][DdmsChunkType.HPGC] [packet][DdmsChunkView] to
 * the JDWP process corresponding to this [JDWP session][SharedJdwpSession].
 */
suspend fun SharedJdwpSession.handleDdmsHPGC(progress: JdwpCommandProgress? = null) {
    val requestPacket = createDdmsPacket(DdmsChunkType.HPGC, emptyByteBuffer)
    handleDdmsCommandWithEmptyReply(requestPacket, DdmsChunkType.HPGC, progress)
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

    val requestPacket = createDdmsPacket(DdmsChunkType.REAE, payload)
    handleDdmsCommandWithEmptyReply(requestPacket, DdmsChunkType.REAE, progress)
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
    return handleDdmsCommand(DdmsChunkType.REAQ, emptyByteBuffer, progress) { chunkReply ->
        val buffer = chunkReply.withPayload { it.toByteBuffer(chunkReply.length) }
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
    return handleDdmsCommand(DdmsChunkType.REAL, emptyByteBuffer, progress) {
        it.withPayload { payload ->
            replyHandler(payload, it.length)
        }
    }
}

suspend fun SharedJdwpSession.handleDdmsMPRQ(progress: JdwpCommandProgress? = null): Int {
    return handleDdmsCommand(MPRQ, emptyByteBuffer, progress) { chunkReply ->
        val replyPayload = chunkReply.withPayload { it.toByteBuffer(chunkReply.length) }
        val result = replyPayload.get()
        result.toInt()
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
    return handleDdmsCommand(DdmsChunkType.VULW, emptyByteBuffer) {
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

    return handleDdmsCommand(DdmsChunkType.VURT, chunkBuf) {
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
    chunkBuf.putInt(DdmsChunkType.Companion.VUOPOpCode.VUOP_CAPTURE_VIEW.value)
    chunkBuf.putString(viewRoot)
    chunkBuf.putString(view)

    chunkBuf.flip()
    assert(chunkBuf.position() == 0)
    assert(chunkBuf.limit() == chunkBuf.capacity())

    return handleDdmsCommand(DdmsChunkType.VUOP, chunkBuf) {
        replyHandler(it)
    }
}

/**
 * Sends a [DdmsChunkView] command packet to this [SharedJdwpSession], using [chunkType] as
 * the ddms command and [payload] as the command payload, and invokes [replyHandler]
 * with the [DdmsChunkView] corresponding to the reply from the Android VM.
 *
 * @throws DdmsFailException if the JDWP reply packet contains a [DdmsChunkType.FAIL] chunk
 * @throws DdmsCommandException if there is an unexpected DDMS/JDWP protocol error
 */
suspend fun <R> SharedJdwpSession.handleDdmsCommand(
  chunkType: DdmsChunkType,
  payload: ByteBuffer,
  progress: JdwpCommandProgress? = null,
  replyHandler: suspend (DdmsChunkView) -> R
): R {
    val commandPacket = createDdmsPacket(chunkType, payload)
    return handleJdwpCommand(commandPacket, progress) { replyPacket ->
        processDdmsReplyPacket(replyPacket, chunkType) {
            replyHandler(it)
        }
    }
}

private suspend fun <R> SharedJdwpSession.processDdmsReplyPacket(
  packet: JdwpPacketView,
  chunkType: DdmsChunkType,
  block: suspend (packet: DdmsChunkView) -> R
): R {
    val logger = thisLogger(device.session)
    val chunkTypeString = chunkType.text

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
            val message = "DDMS reply '${replyChunk.type}' " +
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
 * @throws DdmsFailException if the JDWP reply packet contains a [DdmsChunkType.FAIL] chunk
 * @throws DdmsCommandException if there is an unexpected DDMS/JDWP protocol error
 */
suspend fun SharedJdwpSession.handleDdmsCommandWithEmptyReply(
  requestPacket: JdwpPacketView,
  chunkType: DdmsChunkType,
  progress: JdwpCommandProgress?
) {
    val logger = thisLogger(device.session).withPrefix("pid=$pid: ")
    logger.debug { "Invoking DDMS command ${chunkType.text}" }

    return handleDdmsCommandAndReplyProtocol(progress) { signal ->
        handleAlwaysEmptyReplyDdmsCommand(requestPacket, chunkType, progress, signal)
    }
}

private suspend fun SharedJdwpSession.handleAlwaysEmptyReplyDdmsCommand(
  requestPacket: JdwpPacketView,
  chunkType: DdmsChunkType,
  progress: JdwpCommandProgress?,
  signal: Signal<Unit>?
) {
    val logger = thisLogger(device.session).withPrefix("pid=$pid: ")

    newPacketReceiver()
        .withName("handleEmptyReplyDdmsCommand(${chunkType.text})")
        .onActivation {
            progress?.beforeSend(requestPacket)
            sendPacket(requestPacket)
            progress?.afterSend(requestPacket)
            signal?.complete(Unit)
        }
        .flow()
        .first { packet ->
            logger.verbose { "Receiving packet: $packet" }

            // Stop when we receive the reply to our DDMS command
            val isReply = (packet.isReply && packet.id == requestPacket.id)
            if (isReply) {
                progress?.onReply(packet)
                processEmptyDdmsReplyPacket(packet, chunkType)
            }
            isReply
        }
}

suspend fun SharedJdwpSession.processEmptyDdmsReplyPacket(
    packet: JdwpPacketView,
    chunkType: DdmsChunkType
) {
    val logger = thisLogger(device.session).withPrefix("pid=$pid: ")
    val chunkTypeString = chunkType.text

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
    val logger = thisLogger(device.session)

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

fun SharedJdwpSession.createDdmsPacket(chunkType: DdmsChunkType, chunkPayload: ByteBuffer): JdwpPacketView {
    return JdwpPacketFactory.createDdmsPacket(nextPacketId(), chunkType, chunkPayload)
        .also { packet ->
            val logger = thisLogger(device.session)
            logger.verbose { "Sending DDMS command '$chunkType' in JDWP packet $packet" }
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
            it.type == DdmsChunkType.FAIL
        }.map {
            it.clone()
        }.firstOrNull()
}

/**
 * Invokes [block] with the assumption that is executes a `DDMS` command that may return
 * an empty reply packet.
 *
 * This method takes into account the value of [DdmsProtocolKind] of the device
 * of this [SharedJdwpSession]:
 *
 * * For [DdmsProtocolKind.EmptyRepliesAllowed], [block] is invoked "as-is"
 * * For [DdmsProtocolKind.EmptyRepliesDiscarded], [block] is invoked with a timeout
 * of [DDMS_REPLY_WAIT_TIMEOUT], so that [DdmsChunkType.FAIL] replies can be detected
 * even in the absence of "ACK" reply (i.e. empty JDWP packet).
 *
 * See [DdmsProtocolKind] for a more detailed description of this behavior.
 */
internal suspend fun <R> SharedJdwpSession.handleDdmsCommandAndReplyProtocol(
    progress: JdwpCommandProgress?,
    block: suspend (signal: Signal<R>) -> Unit,
): R {
    return when (device.ddmsProtocolKind()) {
        DdmsProtocolKind.EmptyRepliesAllowed -> {
            val signal = Signal<R>()
            block(signal)
            signal.getOrThrow() // This should not throw if "block" behaved as expected
        }

        DdmsProtocolKind.EmptyRepliesDiscarded -> {
            // We will not get an "empty" JDWP packet, but we may get "FAIL" chunk
            // if there was an error. We have no other option that wait for it
            // with a "reasonable" timeout
            var blockSignal: Signal<R>? = null
            try {
                withTimeoutAfterSignal<R>(device.session.property(DDMS_REPLY_WAIT_TIMEOUT)) { signal ->
                    blockSignal = signal
                    block(signal)
                }
            } catch (e: TimeoutCancellationException) {
                progress?.onReplyTimeout()
                blockSignal?.getOrThrow() ?: run {
                    // Rethrow exception if "block" never signalled anything.
                    // Note that in this case the TimeoutCancellationException we get here is
                    // not caused by a `withTimeout` call inside the `withTimeoutAfterSignal`
                    throw e
                }
            }
        }
    }
}

/**
 * Invokes the coroutine [block], allowing it to set a computation result [R] through
 * a [Signal], then waits for that coroutine to terminates within the specified [timeout].
 *
 * @return The value of [Signal] set by [block]
 * @throws TimeoutCancellationException if [block] does not terminate within the timeout
 * @throws Throwable if [block]] throws an exception at any point
 */
internal suspend fun <R> withTimeoutAfterSignal(
    timeout: Duration,
    block: suspend (signal: Signal<R>) -> Unit
): R {
    val signal = Signal<R>()
    return coroutineScope {
        // Run "block" asynchronously
        val blockJob = async { block(signal) }

        // Wait for block to tell us "ok, start the timeout now"
        // If "block" throws an exception or is cancelled, the exception is rethrown here
        val result = signal.await()

        // Start a coroutine for the timeout, waiting for "block" to terminate
        // If "block" terminates before timeout expires, this job terminates successfully
        // If "block" throws an exception or is cancelled, this job rethrows
        // If the timeout expires, "block" is cancelled
        val timeoutJob = async {
            try {
                withTimeout(timeout.toMillis()) {
                    blockJob.await()
                }
            } catch (t: TimeoutCancellationException) {
                blockJob.cancel(t)
            }
        }

        // Wait for both jobs to succeed, ensuring exceptions are rethrown if needed
        awaitAll(blockJob, timeoutJob)

        // The result of this function is the value of the "signal"
        result
    }
}

/**
 * Similar to [CompletableDeferred], except the completion result can be retrieved
 * directly with [getOrThrow]
 */
internal class Signal<R> {
    private val deferred = CompletableDeferred<R>()
    private var result: Result<R>? = null

    fun complete(value: R): Boolean {
        return deferred.complete(value).also { wasCompleted ->
            if (wasCompleted) {
                result = Result.success(value)
            }
        }
    }

    fun completeExceptionally(exception: Throwable): Boolean {
        result = Result.failure(exception)
        return deferred.completeExceptionally(exception).also { wasCompleted ->
            if (wasCompleted) {
                result = Result.failure(exception)
            }
        }
    }

    fun getOrThrow(): R {
        return result?.getOrThrow()
            ?: throw IllegalStateException("A signalled value has not been initialized")
    }

    suspend fun await(): R {
        return deferred.await()
    }
}
