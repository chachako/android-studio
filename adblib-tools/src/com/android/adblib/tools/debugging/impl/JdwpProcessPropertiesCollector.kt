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

import com.android.adblib.AdbSession
import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.ConnectedDevice
import com.android.adblib.property
import com.android.adblib.serialNumber
import com.android.adblib.thisLogger
import com.android.adblib.tools.AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_DEFAULT
import com.android.adblib.tools.AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_SHORT
import com.android.adblib.tools.AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT
import com.android.adblib.tools.AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT
import com.android.adblib.tools.AdbLibToolsProperties.PROCESS_PROPERTIES_RETRY_DURATION
import com.android.adblib.tools.AdbLibToolsProperties.SUPPORT_STAG_PACKETS
import com.android.adblib.tools.debugging.AtomicStateFlow
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_HEADER_LENGTH
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkType
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CMD
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CMD_SET
import com.android.adblib.tools.debugging.packets.ddms.MutableDdmsChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.AppStage
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsApnmChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsFeatChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsHeloChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsStagChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsWaitChunk
import com.android.adblib.tools.debugging.packets.ddms.clone
import com.android.adblib.tools.debugging.packets.ddms.ddmsChunks
import com.android.adblib.tools.debugging.packets.ddms.writeToChannel
import com.android.adblib.tools.debugging.utils.ReferenceCountedResource
import com.android.adblib.tools.debugging.utils.withResource
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.withPrefix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.EOFException
import java.nio.ByteBuffer

/**
 * Reads [JdwpProcessProperties] from a JDWP connection.
 */
internal class JdwpProcessPropertiesCollector(
    private val device: ConnectedDevice,
    private val processScope: CoroutineScope,
    private val pid: Int,
    private val jdwpSessionRef: ReferenceCountedResource<SharedJdwpSession>
) {

    private val session: AdbSession
        get() = device.session

    private val logger =
        thisLogger(session).withPrefix("device='${device.serialNumber}' pid=$pid: ")

    /**
     * Collects [JdwpProcessProperties] for the process [pid] emits them to [stateFlow],
     * retrying as many times as necessary if there is contention on acquiring JDWP sessions
     * to the process.
     */
    suspend fun execute(stateFlow: AtomicStateFlow<JdwpProcessProperties>) {
        // Delay opening the JDWP session for a small amount of time in case
        // another instance wants to go first.
        delay(
            if (session.property(PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT)) {
                session.property(PROCESS_PROPERTIES_COLLECTOR_DELAY_SHORT).toMillis()
            } else {
                session.property(PROCESS_PROPERTIES_COLLECTOR_DELAY_DEFAULT).toMillis()
            }
        )

        // Opens a JDWP session to the process, and collect as many properties as we
        // can for a short period of time, then close the session and try again
        // later if needed.
        while (true) {

            val collectState = CollectState(stateFlow)
            try {
                withTimeout(session.property(PROCESS_PROPERTIES_READ_TIMEOUT).toMillis()) {
                    collect(collectState)
                }

                // We reached EOF, which can happen for at least 2 common cases:
                // 1. On Android 27 and earlier, Android VM terminates a JDWP connection
                //    right away if there is already an active JDWP connection. On API 28 and later,
                //    Android VM queues JDWP connections if there is already an active one.
                // 2. If the process terminates before the timeout expires. This is sort of a race
                //    condition between us trying to retrieve data about a process, and the process
                //    terminating.
                logger.debug { "EOF while receiving JDWP packet" }
                throw EOFException("JDWP session ended prematurely")
            } catch (throwable: Throwable) {
                val exceptionToRecord = when (throwable) {
                    is TimeoutCancellationException -> {
                        // On API 28+, we get a timeout if there is an active JDWP connection to the
                        // process **or** if the collector did not collect all the data it wanted
                        // (which is common in case there is no WAIT DDMS packet).
                        logger.debug { "Timeout exceeded while collecting process properties" }
                        null // Don't record timeout, it is "normal" termination
                    }

                    is CancellationException -> {
                        logger.debug { "Cancellation while collecting process properties" }
                        throw throwable // don't retry, this is a real cancellation
                    }

                    is EOFException -> {
                        null // Don't record EOF, it is "normal" termination
                    }

                    else -> {
                        logger.info(throwable) {
                            "Exception while collecting process " +
                                    "'${stateFlow.value.processName}' properties"
                        }
                        throwable // Record any other unexpected exception
                    }
                }

                if (collectState.shouldRetryCollecting) {
                    // Delay and retry if we did not collect all properties we want
                    delay(session.property(PROCESS_PROPERTIES_RETRY_DURATION).toMillis())
                    logger.info {
                        "Retrying JDWP process '${stateFlow.value.processName}' properties " +
                                "collection, because previous attempt failed " +
                                "with an error ('${throwable.message}')"
                    }
                } else {
                    collectState.propertiesFlow.update {
                        it.copy(
                            completed = true,
                            exception = exceptionToRecord
                        )
                    }
                    logger.debug { "Successfully retrieved JDWP process properties: ${stateFlow.value}" }
                    break
                }
            }
        }
        assert(stateFlow.value.completed) {
            "Properties flow should have been set to `completed`"
        }
    }

    /**
     * Collects [JdwpProcessProperties] from a new JDWP session to the process [pid]
     * and emits them to [CollectState.propertiesFlow].
     */
    private suspend fun collect(collectState: CollectState) {
        jdwpSessionRef.withResource { jdwpSession ->
            collectWithSession(jdwpSession, collectState)

            if (collectState.hasCollectedEverything) {
                if (collectState.propertiesFlow.value.isWaitingForDebugger) {
                    // See b/271466829: We need to keep the JDWP session open until an external
                    // debugger attaches to the Android process, because a JDWP session in a
                    // `WAIT` state needs to remain open until at least one "real" JDWP command
                    // is sent to eh Android debugger.
                    launchJdwpSessionHolder(collectState.propertiesFlow)
                }
            }
        }
    }

    private suspend fun collectWithSession(
        jdwpSession: SharedJdwpSession,
        collectState: CollectState
    ) {
        // Send a few ddms commands to collect process information
        val commands = IntroCommands(
            heloCommand = createHeloPacket(jdwpSession),
            featCommand = createFeatPacket(jdwpSession),
        )

        val workBuffer = ResizableBuffer()
        jdwpSession.newPacketReceiver()
            .withName("properties collector")
            .onActivation {
                // Send packets to JDWP session
                with(commands) {
                    jdwpSession.sendPacket(heloCommand)
                    jdwpSession.sendPacket(featCommand)
                }
            }.flow()
            .takeWhile { packet ->
                logger.debug { "Processing JDWP packet: $packet" }
                // Any DDMS command is a packet sent from the Android VM that we should
                // replay in case we connect later on again (e.g. for a retry)
                if (packet.isCommand(DDMS_CMD_SET, DDMS_CMD)) {
                    jdwpSession.addReplayPacket(packet)
                }
                processReceivedPacket(packet, collectState, commands, workBuffer)

                // "Take while we have not collected everything"
                !collectState.hasCollectedEverything
            }.collect()
    }

    private suspend fun processReceivedPacket(
        jdwpPacket: JdwpPacketView,
        collectState: CollectState,
        commands: IntroCommands,
        workBuffer: ResizableBuffer
    ) {
        // Here is the situation of DDMS packets we receive from an Android VM:
        // * Sometimes (i.e. depending on the Android API level) we receive both "HELO"
        //   and "APNM", where "HELO" *or* "APNM" contain a "fake" process name, i.e. the
        //   "<pre-initialized>" string or the "" string. The order is non-deterministic,
        //   i.e. it looks like there is a race condition somewhere in the Android VM.
        //   So, basically, we need to ignore any instance of this "fake" name and hope we
        //   get a valid one at some point.
        // * Sometimes we consistently receive "HELO" with all the data correct. This seems
        //   to happen when re-attaching to the same process a 2nd time, for example.
        // * We have seen case where both "HELO" and "APNM" messages contain no data at all.
        //   This seems to be how the Android VM signals that the corresponding command
        //   packet were invalid. This should only happen if there is a bug in the code
        //   sending DDMS packet, i.e. if the code in this source file sends malformed
        //   packets (e.g. incorrect length).
        // * Wrt to the "WAIT" packet, we only receive it if the Android VM is waiting for
        //   a debugger to attach. The definition of "attaching a debugger" according to the
        //   "Android VM" is "receiving any valid JDWP command packet". Unfortunately, there
        //   is no "negative" version of the "WAIT" packet, meaning if the process is not
        //   in the "waiting for a debugger" state, there is no packet sent.
        // * Starting API 34 we know the stage of the app boot progress. It provides info such as
        //   whether the process is waiting for debugger, or if the process is up and running.

        // `HELO` packet is a reply to the `HELO` command we sent to the VM
        if (jdwpPacket.isReply && jdwpPacket.id == commands.heloCommand.id) {
            collectState.heloReplyReceived = true
            processHeloReply(collectState, jdwpPacket, workBuffer)
        }

        // `FEAT` packet is a reply to the `FEAT` command we sent to the VM
        if (jdwpPacket.isReply && jdwpPacket.id == commands.featCommand.id) {
            collectState.featReplyReceived = true
            processFeatReply(collectState, jdwpPacket, workBuffer)
        }

        // `WAIT`, `APNM`, and `STAG` are DDMS chunks embedded in a JDWP command packet sent from
        // the VM to us
        if (jdwpPacket.isCommand(DDMS_CMD_SET, DDMS_CMD)) {
            // For completeness, we process all chunks embedded in a JDWP packet, even
            // though in practice there is always one and only one DDMS chunk per JDWP
            // packet.
            jdwpPacket.ddmsChunks().collect { chunk ->
                when (chunk.type) {
                    DdmsChunkType.WAIT -> {
                        collectState.waitReceived = true
                        processWaitCommand(collectState, chunk.clone(), workBuffer)
                    }

                    DdmsChunkType.APNM -> {
                        processApnmCommand(collectState, chunk.clone(), workBuffer)
                    }

                    DdmsChunkType.STAG -> {
                        processStagCommand(collectState, chunk.clone(), workBuffer)
                    }

                    else -> {
                        logger.debug { "Skipping unexpected chunk: $chunk" }
                    }
                }
            }
        }
    }

    private suspend fun processHeloReply(
        collectState: CollectState,
        packet: JdwpPacketView,
        workBuffer: ResizableBuffer
    ) {
        val heloChunk = processJdwpPacket(packet, "HELO") {
            val heloChunkView = packet.ddmsChunks().first().clone()
            DdmsHeloChunk.parse(heloChunkView, workBuffer)
        }
        logger.debug { "`HELO` reply: $heloChunk" }
        if (heloChunk.stage != null && !session.property(SUPPORT_STAG_PACKETS)) {
            logger.debug { "Not using STAG value since it's disabled by a 'SUPPORT_STAG_PACKETS' property" }
        }
        collectState.propertiesFlow.update {
            it.copy(
                processName = filterFakeName(heloChunk.processName),
                userId = heloChunk.userId,
                packageName = filterFakeName(heloChunk.packageName),
                vmIdentifier = heloChunk.vmIdentifier,
                abi = heloChunk.abi,
                jvmFlags = heloChunk.jvmFlags,
                isNativeDebuggable = heloChunk.isNativeDebuggable,
                stage = if (session.property(SUPPORT_STAG_PACKETS)) heloChunk.stage else null,
            )
        }
    }

    private suspend fun processFeatReply(
        collectState: CollectState,
        packet: JdwpPacketView,
        workBuffer: ResizableBuffer
    ) {
        val featChunk = processJdwpPacket(packet, "FEAT") {
            val featChunkView = packet.ddmsChunks().first().clone()
            DdmsFeatChunk.parse(featChunkView, workBuffer)
        }
        logger.debug { "`FEAT` reply: $featChunk" }
        collectState.propertiesFlow.update { it.copy(features = featChunk.features) }
    }

    private suspend fun processWaitCommand(
        collectState: CollectState,
        chunkCopy: DdmsChunkView,
        workBuffer: ResizableBuffer
    ) {
        val waitChunk = processDdmsChunk(chunkCopy, "WAIT") {
            DdmsWaitChunk.parse(chunkCopy, workBuffer)
        }
        logger.debug { "`WAIT` command: $waitChunk" }
        collectState.propertiesFlow.update { it.copy(waitCommandReceived = true) }
    }

    private suspend fun processApnmCommand(
        collectState: CollectState,
        chunkCopy: DdmsChunkView,
        workBuffer: ResizableBuffer
    ) {
        val apnmChunk = processDdmsChunk(chunkCopy, "APNM") {
            DdmsApnmChunk.parse(chunkCopy, workBuffer)
        }
        logger.debug { "`APNM` command: $apnmChunk" }
        collectState.propertiesFlow.update {
            it.copy(
                processName = filterFakeName(apnmChunk.processName),
                userId = apnmChunk.userId,
                packageName = filterFakeName(apnmChunk.packageName)
            )
        }
    }

    private suspend fun processStagCommand(
        collectState: CollectState,
        chunkCopy: DdmsChunkView,
        workBuffer: ResizableBuffer
    ) {
        val stagChunk = processDdmsChunk(chunkCopy, "STAG") {
            DdmsStagChunk.parse(chunkCopy, workBuffer)
        }
        logger.debug { "`STAG` command: $stagChunk" }
        if(!session.property(SUPPORT_STAG_PACKETS)) {
            logger.debug { "Discarding STAG reply since it's disabled by a 'SUPPORT_STAG_PACKETS' property" }
            return
        }
        collectState.propertiesFlow.update {
            it.copy(
                stage = stagChunk.stage,
            )
        }
    }

    private suspend fun createHeloPacket(jdwpSession: SharedJdwpSession): JdwpPacketView {
        // Prepare chunk payload buffer
        val payload = ResizableBuffer().order(DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER)
        payload.appendInt(DdmsHeloChunk.SERVER_PROTOCOL_VERSION)

        // Return is as a packet
        return createDdmsChunkPacket(
            jdwpSession.nextPacketId(),
            DdmsChunkType.HELO,
            payload.forChannelWrite()
        )
    }

    private suspend fun createFeatPacket(jdwpSession: SharedJdwpSession): JdwpPacketView {
        // Prepare chunk payload buffer
        val payload = ResizableBuffer().order(DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER)

        // Return is as a packet
        return createDdmsChunkPacket(
            jdwpSession.nextPacketId(),
            DdmsChunkType.FEAT,
            payload.forChannelWrite()
        )
    }

    private suspend fun createDdmsChunkPacket(
        packetId: Int,
        chunkType: DdmsChunkType,
        chunkData: ByteBuffer
    ): JdwpPacketView {
        val chunk = MutableDdmsChunk()
        chunk.type = chunkType
        chunk.length = chunkData.remaining()
        chunk.payload = AdbBufferedInputChannel.forByteBuffer(chunkData)
        val workBuffer = ResizableBuffer()
        val outputChannel = ByteBufferAdbOutputChannel(workBuffer)
        chunk.writeToChannel(outputChannel)
        val serializedChunk = workBuffer.forChannelWrite()

        val packet = MutableJdwpPacket()
        packet.id = packetId
        packet.length = PACKET_HEADER_LENGTH + serializedChunk.remaining()
        packet.cmdSet = DDMS_CMD_SET
        packet.cmd = DDMS_CMD
        packet.payload = AdbBufferedInputChannel.forByteBuffer(serializedChunk)

        logger.debug { "Preparing to send $chunk" }
        return packet
    }

    private fun filterFakeName(processOrPackageName: String?): String? {
        return if (EARLY_PROCESS_NAMES.contains(processOrPackageName)) {
            return null
        } else {
            processOrPackageName
        }
    }

    private suspend fun launchJdwpSessionHolder(propertiesFlow: AtomicStateFlow<JdwpProcessProperties>) {
        logger.debug { "JDWP session holder: launching coroutine" }
        val deferred = CompletableDeferred<Unit>()
        processScope.launch {
            jdwpSessionRef.withResource {
                logger.debug { "JDWP session holder: JDWP session has been acquired" }

                // Ok to exit function now, as session has been acquired
                deferred.complete(Unit)

                // Wait until a debugger is attached, then keep session open as long as
                // debugger is attached. This gives time for the external debugger
                // to acquire its own SharedJdwpSession and keep it open as long as
                // it is active. If/when the external debugger detaches from the process,
                // we also release the SharedJdwpSession in case another debug session
                // needs to be started later on.
                with(propertiesFlow.asStateFlow()) {
                    waitUntil { jdwpSessionProxyStatus.isExternalDebuggerAttached }
                    waitWhile { jdwpSessionProxyStatus.isExternalDebuggerAttached }
                }

                logger.debug { "JDWP session holder: JDWP session about to be released as debugger has detached" }
            }
        }

        // Wait until session has been acquired
        deferred.await()
        logger.debug { "JDWP session holder: successfully started" }
    }

    private suspend fun <R> processJdwpPacket(
        packet: JdwpPacketView,
        packetName: String,
        block: suspend (JdwpPacketView) -> R
    ): R {
        if (packet.isEmpty) {
            throw JdwpProtocolErrorException("Invalid '$packetName' packet: packet is empty")
        }
        return try {
            block(packet)
        } catch (t: Throwable) {
            throw JdwpProtocolErrorException("Invalid '$packetName' packet: unsupported format", t)
        }
    }

    private suspend fun <R> processDdmsChunk(
        packet: DdmsChunkView,
        packetName: String,
        block: suspend (DdmsChunkView) -> R
    ): R {
        return try {
            block(packet)
        } catch (t: Throwable) {
            throw JdwpProtocolErrorException("Invalid '$packetName' ddms packet: unsupported format", t)
        }
    }

    private suspend fun <T> StateFlow<T>.waitUntil(predicate: T.() -> Boolean) {
        if (!value.predicate()) {
            first { it.predicate() }
        }
    }

    private suspend fun <T> StateFlow<T>.waitWhile(predicate: T.() -> Boolean) {
        takeWhile { it.predicate() }.collect()
    }

    /**
     * List of DDMS requests sent to the Android VM.
     */
    data class IntroCommands(
        val heloCommand: JdwpPacketView,
        val featCommand: JdwpPacketView,
    )

    class CollectState(val propertiesFlow: AtomicStateFlow<JdwpProcessProperties>) {

        var heloReplyReceived = false
        var featReplyReceived = false
        var waitReceived = false

        /**
         * Whether we collected all the data we need
         */
        val hasCollectedEverything: Boolean
            get() {
                if (!isDone) {
                    return false
                }

                // For Api 34+ use boot stage data
                if (propertiesFlow.value.stage != null) {
                    return propertiesFlow.value.stage == AppStage.DEBG
                            || propertiesFlow.value.stage == AppStage.A_GO
                }

                // For Api <= 33 we can only say we collected everything we need
                // if we received the `WAIT` packet (because there is
                // no such thing as a `NO_WAIT` packet).
                return waitReceived
            }

        /**
         * Whether there is a need to retry collecting process properties
         */
        val shouldRetryCollecting: Boolean
            get() = !isDone

        private val isDone: Boolean
            get() {
                return heloReplyReceived &&
                        featReplyReceived &&
                        propertiesFlow.value.processName != null
            }
    }

    companion object {

        /**
         * The process name (and package name) can be set to this value when the process is not yet fully
         * initialized. We should ignore this value to make sure we only return "valid" process/package name.
         * Note that sometimes the process name (or package name) can also be empty.
         */
        private val EARLY_PROCESS_NAMES = arrayOf("<pre-initialized>", "")
    }
}

