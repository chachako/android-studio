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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.scope
import com.android.adblib.serialNumber
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.JdwpPacketReceiver
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSessionFilter
import com.android.adblib.tools.debugging.SharedJdwpSessionMonitor
import com.android.adblib.tools.debugging.impl.SharedJdwpSessionImpl.ScopedPayloadProvider.ScopedAdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.PayloadProvider
import com.android.adblib.tools.debugging.packets.clone
import com.android.adblib.tools.debugging.packets.withPayload
import com.android.adblib.tools.debugging.rethrowCancellation
import com.android.adblib.tools.debugging.sharedJdwpSessionMonitorFactoryList
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.createChildScope
import com.android.adblib.utils.withReentrantLock
import com.android.adblib.withPrefix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Implementation of [SharedJdwpSession]
 */
internal class SharedJdwpSessionImpl(
    private val jdwpSession: JdwpSession,
    override val pid: Int
) : SharedJdwpSession {

    private val session: AdbSession
        get() = device.session

    override val device: ConnectedDevice
        get() = jdwpSession.device

    private val logger =
        thisLogger(session).withPrefix("device='${device.serialNumber}' pid=$pid: ")

    /**
     * The scope used to run coroutines for sending and receiving packets
     */
    private val scope = device.scope.createChildScope(isSupervisor = true)

    /**
     * The class that ensures [sendPacket] is thread-safe and also safe
     * from coroutine cancellation.
     */
    private val packetSender = PacketSender(scope, this)

    /**
     * The list of registered [ActiveReceiver], i.e. [JdwpPacketReceiver] that
     * have an active flow.
     */
    private val activeReceivers = CopyOnWriteArrayList<ActiveReceiver>()

    /**
     * The list of [JdwpPacketView] to replay to each new [JdwpPacketReceiver]
     */
    private val replayPackets = CopyOnWriteArrayList<JdwpPacketView>()

    /**
     * The [StateFlow] use to signal the [sendReceivedPackets] coroutines it needs
     * to resume (or pause) reading packets from the underlying [JdwpSession].
     */
    private val hasReceiversStateFlow = MutableStateFlow(false)

    /**
     * If the underlying [JdwpSession] has ended, this contains the [Throwable]
     * corresponding to the termination. [EOFException] implies "normal" termination.
     */
    private val sessionEndResultStateFlow = MutableStateFlow<Throwable?>(null)

    /**
     * The [MutableSharedFlow] used to wake up the [sendReplayPackets] when a
     * new [JdwpPacketReceiver] has been registered.
     */
    private val receiverAddedSharedFlow = MutableSharedFlow<ActiveReceiver>()

    /**
     * The [SharedJdwpSessionMonitor] to invoke when sending or receiving [JdwpPacketView] packets,
     * or `null` if there are no active monitors from [AdbSession.sharedJdwpSessionMonitorFactoryList].
     *
     * Note: We aggregate all monitors so that we can have an efficient `null` check in the common
     * case where there are no active [SharedJdwpSessionMonitor].
     */
    private val jdwpMonitor = createAggregateJdwpMonitor(
        session.sharedJdwpSessionMonitorFactoryList.mapNotNull { factory ->
            factory.create(this)
        })

    private val jdwpFilter = SharedJdwpSessionFilterEngine(this)

    init {
        scope.launch {
            sendReceivedPackets()
        }
        scope.launch {
            sendReplayPackets()
        }
    }

    override suspend fun sendPacket(packet: JdwpPacketView) {
        packetSender.sendPacket(packet)
    }

    override suspend fun newPacketReceiver(): JdwpPacketReceiver {
        return JdwpPacketReceiverImpl(this)
    }

    override fun nextPacketId(): Int {
        return jdwpSession.nextPacketId()
    }

    override suspend fun addReplayPacket(packet: JdwpPacketView) {
        logger.verbose { "Adding JDWP replay packet '$packet'" }
        val clone = packet.clone()
        replayPackets.add(clone)
    }

    override suspend fun shutdown() {
        jdwpSession.shutdown()
    }

    override fun close() {
        logger.debug { "Closing session" }
        val exception = CancellationException("Shared JDWP session closed")
        sessionEndResultStateFlow.value = exception
        scope.cancel(exception)
        jdwpSession.close()
        jdwpMonitor?.close()
        jdwpFilter.close()
    }

    private suspend fun addActiveReceiver(name: String): ActiveReceiver {
        val receiver = ActiveReceiver(this, name)
        logger.debug { "Adding active receiver '${receiver.name}'" }
        activeReceivers.add(receiver)

        // Wake up job that sends replay packets to new receivers
        receiverAddedSharedFlow.emit(receiver)

        // Wake up job that reads packets from the underlying JDWP session (if this is
        // the first receiver, packed reading starts)
        hasReceiversStateFlow.value = activeReceivers.isNotEmpty()

        // If the JDWP session has ended, notify receiver right away
        if (!scope.isActive) {
            session.scope.launch {
                val error = sessionEndResultStateFlow.filterNotNull().first()
                sendPacketResultToReceiver(receiver, Result.failure(error))
            }
        }

        return receiver
    }

    private fun removeActiveReceiver(receiver: ActiveReceiver) {
        logger.debug { "Removing active receiver '${receiver.name}'" }
        activeReceivers.remove(receiver)

        // If this was the last receiver, pause job that reads packets from the underlying
        // JDWP session. The reader job will be resumed when a new receiver is added.
        hasReceiversStateFlow.value = activeReceivers.isNotEmpty()
    }

    private suspend fun sendReplayPackets() {
        withContext(CoroutineName("Shared JDWP Session: Send replay packets job")) {
            while (true) {
                receiverAddedSharedFlow.collect { newReceiver ->
                    sendReplayPlacketsToReceiver(newReceiver)
                }
            }
        }
    }

    private suspend fun sendReceivedPackets() {
        withContext(CoroutineName("Shared JDWP Session: Send received packets job")) {
            val workBuffer = ResizableBuffer()
            while (true) {

                // Wait until "isActive" is `true`
                hasReceiversStateFlow.waitUntil(true)
                sendReplayPlacketsToReceivers()

                logger.verbose { "Waiting for next JDWP packet from session" }
                val sessionPacket = try {
                    jdwpSession.receivePacket()
                } catch (t: Throwable) {
                    // Cancellation cancels the jdwp session scope, which cancel the scope
                    // of all receivers, so they will terminate with cancellation too.
                    t.rethrowCancellation()

                    // Reached EOF, flow terminates
                    if (t is EOFException) {
                        logger.debug { "JDWP session has ended with EOF" }
                    }
                    sessionEndResultStateFlow.value = t
                    activeReceivers.forEach { receiver ->
                        sendPacketResultToReceiver(receiver, Result.failure(t))
                    }
                    sendFailureUntilCancelled(t)
                    break
                }

                // "Emit" a thread-safe version of "sessionPacket" to all receivers
                sessionPacket.withPayload { sessionPacketPayload ->
                    // Note: "sessionPacketPayload" is an input channel directly connected to the
                    // underlying connection.
                    // We create a scoped payload channel so that
                    // 1) the payload is thread-safe for all receivers, and
                    // 2) cancellation of any receiver does not close the underlying socket
                    // 3) "shutdown" does not close the underlying PayloadProvider (that is taken
                    // care of by the JdwpSession class)
                    val payloadProvider = ScopedPayloadProvider(scope, sessionPacketPayload)
                    EphemeralJdwpPacket.fromPacket(sessionPacket, payloadProvider).use { packet ->

                            // Send to each receiver
                            sendReplayPlacketsToReceivers()
                            jdwpMonitor?.onReceivePacket(packet)
                            activeReceivers.forEach { receiver ->
                                logger.verbose { "Emitting session packet $packet to receiver '${receiver.name}'" }
                                sendPacketResultToReceiver(receiver, Result.success(packet))
                            }
                            jdwpFilter.afterReceivePacket(packet)

                            // Packet should not be used after this, because payload of the jdwp packet
                            // from the underlying jdwp session is about to become invalid.
                            packet.shutdown(workBuffer)
                        }
                }
            }
        }
    }

    /**
     * Once the underlying [JdwpSession] has ended with an exception, keep sending
     * that exception to any new or existing receiver.
     */
    private suspend fun sendFailureUntilCancelled(t: Throwable) {
        while (true) {
            scope.ensureActive()

            // Wait until "isActive" is `true`
            hasReceiversStateFlow.waitUntil(true)
            sendReplayPlacketsToReceivers()

            logger.verbose { "Sending failure to active receivers ($t)" }
            activeReceivers.forEach { receiver ->
                sendPacketResultToReceiver(receiver, Result.failure(t))
            }
        }
    }

    private suspend fun <T> StateFlow<T>.waitUntil(value: T) {
        this.first { it == value }
    }

    private suspend fun sendReplayPlacketsToReceivers() {
        activeReceivers.forEach { receiver ->
            sendReplayPlacketsToReceiver(receiver)
        }
    }

    private suspend fun sendReplayPlacketsToReceiver(receiver: ActiveReceiver) {
        receiver.replayPackets {
            replayPackets.forEach { packet ->
                logger.verbose { "Sending replay packet to receiver '${receiver.name}': $packet" }
                sendPacketResultToReceiver(receiver, Result.success(packet))
            }
        }
    }

    private suspend fun sendPacketResultToReceiver(
        receiver: ActiveReceiver,
        packet: Result<JdwpPacketView>
    ) {
        runCatching {
            logger.verbose { "Sending packet to receiver '${receiver.name}': $packet" }
            receiver.sendPacketResult(packet)
        }.onFailure { t ->
            if (t !is CancellationException) {
                logger.info(t) { "Failure sending packet to receiver: '${receiver.name}': $packet" }
            }
        }
    }

    private class JdwpPacketReceiverImpl(
        private val jdwpSession: SharedJdwpSessionImpl
    ) : JdwpPacketReceiver() {

        override fun flow(): Flow<JdwpPacketView> {
            return ReceiverFlowImpl(this).flow()
        }

        private class ReceiverFlowImpl(private val jdwpPacketReceiver: JdwpPacketReceiverImpl) {

            private val jdwpSession: SharedJdwpSessionImpl
                get() = jdwpPacketReceiver.jdwpSession

            private val name: String
                get() = jdwpPacketReceiver.name

            private val filterId: SharedJdwpSessionFilter.FilterId?
                get() = jdwpPacketReceiver.filterId

            private val receiverLogger = jdwpSession.logger.withPrefix("receiver for '$name': ")

            private val flowLogger = jdwpSession.logger.withPrefix("flow for '$name': ")

            fun flow(): Flow<JdwpPacketView> = flow {
                // Create channels the "Receiver" and "Flow" use to synchronize processing
                // JdwpPackets.
                Channels().use { channels ->
                    collectWithActiveReceiver(channels)
                }
            }

            private suspend fun FlowCollector<JdwpPacketView>.collectWithActiveReceiver(
                channels: Channels
            ) {
                // Add a receiver that sends every packet to the flow
                jdwpSession.addActiveReceiver(jdwpPacketReceiver.name)
                    .withCallback { packetResult ->
                        // Note: Callback is invoked from a scope distinct from the flow.
                        // Send packet to flow and wait until flow is done with it
                        receiverLogger.verbose { "Sending packet to flow: $packetResult" }
                        try {
                            // Note that when the flow ends, both channels are closed by the flow
                            // collection coroutine.
                            channels.newPacket.send(packetResult)
                            channels.releasePacket.receive()
                        } catch (e: ClosedSendChannelException) {
                            // Ignore
                            receiverLogger.verbose(e) { "Send Channel has been closed" }
                        } catch (e: ClosedReceiveChannelException) {
                            // Ignore
                            receiverLogger.verbose(e) { "Receive Channel has been closed" }
                        }
                    }.use { activeReceiver ->
                        // Ensure flow is cancelled when the JDWP session is closed
                        val flowContext = currentCoroutineContext()
                        val flowCompletionHandle =
                            activeReceiver.scope.coroutineContext.job.invokeOnCompletion { throwable ->
                                flowLogger.verbose { "Receiver scope has completed with throwable=$throwable" }
                                flowContext.cancel(
                                    (throwable as? CancellationException) ?: CancellationException(
                                        "Flow cancellation because receiver has been closed",
                                        throwable
                                    )
                                )
                            }
                        try {
                            activeReceiver.setReady() // callback is ready to be invoked
                            jdwpPacketReceiver.activation() // Notify flow is collecting

                            // Process the packets
                            receiveAndEmitPackets(channels)
                        } finally {
                            flowCompletionHandle.dispose()
                        }
                    }
            }

            private suspend fun FlowCollector<JdwpPacketView>.receiveAndEmitPackets(
                channels: Channels
            ) {
                while (true) {
                    flowLogger.verbose { "Waiting for packet on channel" }
                    val packet = try {
                        channels.newPacket.receive().getOrThrow()
                    } catch (e: EOFException) {
                        // EOF is "normal" termination, so we end flow "normally"
                        flowLogger.debug { "EOF reached, ending flow" }
                        break
                    }
                    if (jdwpSession.jdwpFilter.filterReceivedPacket(filterId, packet)) {
                        flowLogger.verbose { "Emitting packet flow: $packet" }
                        emit(packet)
                    } else {
                        flowLogger.verbose { "Skipping packet due to filter: $packet" }
                    }

                    // Give control back to receiver callback
                    channels.releasePacket.send(Unit)
                }
            }

            private inner class Channels : AutoCloseable {

                val newPacket = Channel<Result<JdwpPacketView>>()
                val releasePacket = Channel<Unit>()

                override fun close() {
                    receiverLogger.verbose { "Closing send and receive channels" }
                    newPacket.close()
                    releasePacket.close()
                }
            }
        }
    }

    /**
     * A [PayloadProvider] that wraps a [payload][AdbInputChannel] using a
     * [ScopedAdbBufferedInputChannel] so that cancellation of pending read operations
     * never close the initial [payload][AdbInputChannel].
     */
    private class ScopedPayloadProvider(
        /**
         * The [CoroutineScope] used to asynchronously read from the [payload]. This scope
         * should be active as long as [payload] is active.
         */
        scope: CoroutineScope,
        /**
         * The [AdbInputChannel] being wrapped.
         */
        payload: AdbInputChannel
    ) : PayloadProvider {

        private var closed = false

        /**
         * Locks access to [scopedPayload] to guarantee there is at most a single reader
         */
        private val mutex = Mutex()

        /**
         * The [ScopedAdbBufferedInputChannel] wrapping the original [AdbInputChannel], to ensure
         * cancellation of [AdbInputChannel.read] operations don't close the [AdbInputChannel].
         */
        private var scopedPayload = ScopedAdbBufferedInputChannel(scope, payload)

        override suspend fun acquirePayload(): AdbInputChannel {
            throwIfClosed()
            mutex.lock()
            return scopedPayload
        }

        override suspend fun releasePayload() {
            scopedPayload.waitForPendingRead()
            scopedPayload.rewind()
            mutex.unlock()
        }

        override suspend fun shutdown(workBuffer: ResizableBuffer) {
            closed = true
            mutex.withLock {
                scopedPayload.waitForPendingRead()
            }
        }

        override fun close() {
            closed = true
            scopedPayload.close()
        }

        private fun throwIfClosed() {
            if (closed) {
                throw IllegalStateException("Payload is not available anymore because the provider has been closed")
            }
        }

        /**
         * An [AdbBufferedInputChannel] that reads from another [AdbBufferedInputChannel] in a custom
         * [CoroutineScope] so that cancellation does not affect the initial [bufferedInput].
         */
        private class ScopedAdbBufferedInputChannel(
            private val scope: CoroutineScope,
            input: AdbInputChannel
        ) : AdbBufferedInputChannel {

            private var currentReadJob: Job? = null

            /**
             * Ensure we have a [AdbBufferedInputChannel] so we support
             * [AdbBufferedInputChannel.rewind]
             */
            private val bufferedInput = if (input is AdbBufferedInputChannel) {
                input
            } else {
                AdbBufferedInputChannel.forInputChannel(input)
            }

            override suspend fun rewind() {
                throwIfPendingRead()
                bufferedInput.rewind()
            }

            override suspend fun finalRewind() {
                throw IllegalStateException("finalRewind should never be called on ${this::class}")
            }

            override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                return scopedRead { bufferedInput.read(buffer) }
            }

            override suspend fun readExactly(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                return scopedRead { bufferedInput.readExactly(buffer) }
            }

            suspend fun waitForPendingRead() {
                currentReadJob?.join()
            }

            override fun close() {
                // We cancel any pending job if there is one, so that we support prompt
                // cancellation if the owner of this instance if cancelled. In non-exceptional
                // code paths, "currentReadJob" is already completed (because of the
                // "waitForPendingRead" call) and the "cancel" call below is a no-op.
                currentReadJob?.cancel("${this::class} has been closed")
            }

            private suspend inline fun <R> scopedRead(crossinline reader: suspend () -> R): R {
                throwIfPendingRead()

                // Start new job in parent scope and wait for its completion
                return scope.async {
                    reader()
                }.also { job ->
                    currentReadJob = job
                }.await().also {
                    currentReadJob = null
                }
            }

            private fun throwIfPendingRead() {
                check(!(currentReadJob?.isActive ?: false)) {
                    "Operation is not supported if there is a pending read operation"
                }
            }
        }
    }

    /**
     * Send [JdwpPacketView] to the underlying [jdwpSession] in such a way that cancellation
     * of the [sendPacket] coroutine does not cancel the underlying socket connection.
     */
    private class PacketSender(
        private val scope: CoroutineScope,
        private val sharedJdwpSession: SharedJdwpSessionImpl
    ) {

        suspend fun sendPacket(packet: JdwpPacketView) {
            scope.async {
                sharedJdwpSession.jdwpMonitor?.onSendPacket(packet)
                sharedJdwpSession.jdwpFilter.beforeSendPacket(packet)
                sharedJdwpSession.jdwpSession.sendPacket(packet)
            }.await()
        }
    }

    private class ActiveReceiver(
        private val jdwpSession: SharedJdwpSessionImpl,
        val name: String
    ) : AutoCloseable {

        private val logger = jdwpSession.logger.withPrefix("ActiveReceiver '$name': ")

        private val mutex = Mutex()

        private val isReady = MutableStateFlow(false)

        private var callback: suspend CoroutineScope.(Result<JdwpPacketView>) -> Unit = { }

        /**
         * The [CoroutineScope] unique to this receiver, used for processing [JdwpPacketView].
         * [scope] is cancelled when [close] is called.
         */
        val scope = jdwpSession.scope.createChildScope()

        @Volatile
        private var _needReplayPackets: Boolean = true

        /**
         * Set the [callback] that will be invoked each time [sendPacketResult] is invoked.
         * The [callback] is invoked in the context of [scope].
         */
        fun withCallback(callback: suspend CoroutineScope.(Result<JdwpPacketView>) -> Unit): ActiveReceiver {
            this.callback = callback
            return this
        }

        /**
         * Invoked when there is an active flow collector, meaning the receiver is
         * ready to emit [JdwpPacketView].
         */
        fun setReady() {
            isReady.value = true
        }

        /**
         * Wait until [setReady] has been called
         */
        private suspend fun waitReady() {
            if (!isReady.value) {
                isReady.first { isReady -> isReady }
            }
        }

        suspend fun replayPackets(block: suspend () -> Unit) {
            waitReady()
            if (_needReplayPackets) {
                mutex.withReentrantLock {
                    if (_needReplayPackets) {
                        _needReplayPackets = false
                        block()
                    }
                }
            }
        }

        /**
         * Invoke [callback] with [packetResult] in the context of [scope]
         * when the receiver [is ready][setReady].
         */
        suspend fun sendPacketResult(packetResult: Result<JdwpPacketView>) {
            waitReady()
            mutex.withReentrantLock {
                // Run this in the scope of receiver to support cancellation via the "close" method
                // if needed
                withContext(scope.coroutineContext + CoroutineName("JDWP Receiver callback")) {
                    scope.callback(packetResult)
                }
            }
        }

        override fun close() {
            logger.verbose { "Closing receiver" }
            scope.cancel("Active Receiver closed")
            jdwpSession.removeActiveReceiver(this)
        }
    }

    companion object {

        private fun createAggregateJdwpMonitor(jdwpMonitors: List<SharedJdwpSessionMonitor>): SharedJdwpSessionMonitor? {
            return if (jdwpMonitors.isEmpty()) {
                null
            } else {
                object : SharedJdwpSessionMonitor {
                    override suspend fun onSendPacket(packet: JdwpPacketView) {
                        jdwpMonitors.forEach {
                            it.onSendPacket(packet)
                        }
                    }

                    override suspend fun onReceivePacket(packet: JdwpPacketView) {
                        jdwpMonitors.forEach {
                            it.onReceivePacket(packet)
                        }
                    }

                    override fun close() {
                        jdwpMonitors.forEach {
                            it.close()
                        }
                    }
                }
            }
        }
    }
}
