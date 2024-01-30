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
import com.android.adblib.AdbLogger
import com.android.adblib.AdbSession
import com.android.adblib.AutoShutdown
import com.android.adblib.ConnectedDevice
import com.android.adblib.adbLogger
import com.android.adblib.read
import com.android.adblib.scope
import com.android.adblib.tools.debugging.JdwpPacketReceiver
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSessionFilter
import com.android.adblib.tools.debugging.SharedJdwpSessionMonitor
import com.android.adblib.tools.debugging.impl.SharedJdwpSessionImpl.ScopedPayloadProvider.ScopedAdbRewindableInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.impl.EphemeralJdwpPacket
import com.android.adblib.tools.debugging.packets.impl.PayloadProvider
import com.android.adblib.tools.debugging.packets.impl.PayloadProviderFactory
import com.android.adblib.tools.debugging.packets.impl.withPayload
import com.android.adblib.tools.debugging.packets.isThreadSafeAndImmutable
import com.android.adblib.tools.debugging.packets.withPayload
import com.android.adblib.tools.debugging.rethrowCancellation
import com.android.adblib.tools.debugging.sharedJdwpSessionMonitorFactoryList
import com.android.adblib.tools.debugging.utils.AdbRewindableInputChannel
import com.android.adblib.tools.debugging.utils.ByteBufferHolder
import com.android.adblib.tools.debugging.utils.MutableSerializedSharedFlow
import com.android.adblib.tools.debugging.utils.SerializedSharedFlow
import com.android.adblib.tools.debugging.utils.SupportsOffline
import com.android.adblib.tools.debugging.utils.toOffline
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.createChildScope
import com.android.adblib.withPrefix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Implementation of [SharedJdwpSession] over an underlying [JdwpSession]
 */
internal class SharedJdwpSessionImpl(
    override val device: ConnectedDevice,
    private val jdwpSessionFactory: suspend (ConnectedDevice) -> JdwpSession,
    override val pid: Int
) : SharedJdwpSession, AutoShutdown {

    private val session: AdbSession
        get() = device.session

    private val logger = adbLogger(device.session)
        .withPrefix("${device.session} - $device - pid=$pid - ")

    private val jdwpSessionMutex = Mutex()

    private var jdwpSession: JdwpSession? = null

    private var closed = false

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
     * The list of [JdwpPacketView] to replay to each new [JdwpPacketReceiver]
     */
    private val replayPackets = CopyOnWriteArrayList<JdwpPacketView>()

    /**
     * [CompletableDeferred] used to store the [exception][Throwable] thrown by [jdwpSession]
     * when it ended (normally or not).
     */
    private val jdwpSessionException = CompletableDeferred<Throwable>()

    /**
     * The [MutableSerializedSharedFlow] used to [MutableSerializedSharedFlow.emit] JDWP packets
     * to all active [JdwpPacketReceiver] instances.
     *
     * Note: We use [Any] as the type parameter, but we only emit either [Throwable] or
     * [JdwpPacketView] instances. We don't use [Result] to avoid extra allocations.
     */
    private val jdwpPacketSharedFlow = MutableSerializedSharedFlow<Any>()

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

    /**
     * The [SharedJdwpSessionFilterEngine] that forwards all [packets][JdwpPacketView] to all
     * active [SharedJdwpSessionFilter] instances, so that packets can be filtered for
     * [JdwpPacketReceiver] instances that used [JdwpPacketReceiver.withFilter].
     */
    private val jdwpFilter = SharedJdwpSessionFilterEngine(this)

    /**
     * Opens the underlying JDWP session if needed.
     *
     * Note: This method is thread-safe
     */
    suspend fun openIfNeeded() {
        throwIfClosed()
        jdwpSessionMutex.withLock {
            jdwpSession ?: run {
                jdwpSessionFactory(device).also {
                    jdwpSession = it
                    scope.launch {
                        sendReceivedPackets()
                    }
                }
            }
        }
    }

    override suspend fun sendPacket(packet: JdwpPacketView) {
        throwIfClosed()
        packetSender.sendPacket(packet)
    }

    override suspend fun newPacketReceiver(): JdwpPacketReceiver {
        throwIfClosed()
        return JdwpPacketReceiverImpl(this)
    }

    override fun nextPacketId(): Int {
        throwIfClosed()
        return activeJdwpSession().nextPacketId()
    }

    override suspend fun addReplayPacket(packet: JdwpPacketView) {
        throwIfClosed()
        logger.verbose { "Adding JDWP replay packet '$packet'" }
        val clone = packet.toOffline()
        replayPackets.add(clone)
    }

    override suspend fun shutdown() {
        jdwpSessionMutex.withLock {
            jdwpSession?.shutdown()
        }
    }

    override fun close() {
        closed = true
        logger.debug { "Closing session" }
        val exception = CancellationException("Shared JDWP session closed")
        scope.cancel(exception)
        jdwpSession?.close()
        jdwpMonitor?.close()
        jdwpFilter.close()
        jdwpPacketSharedFlow.close()
    }

    private fun throwIfClosed() {
        check(!closed) { "${this::class.simpleName} is closed" }
    }

    private fun activeJdwpSession(): JdwpSession {
        return jdwpSession ?: run {
            throw IllegalStateException("${this::class.simpleName} is not open")
        }
    }

    /**
     * Asynchronous long-running coroutine that forwards packets read from [activeJdwpSession]
     * to active [jdwpPacketSharedFlow]
     */
    private suspend fun sendReceivedPackets() {
        withContext(CoroutineName("Shared JDWP Session: Send received packets job")) {
            val workBuffer = ResizableBuffer()
            val sharedPayloadProviderFactory = SharedPayloadProviderFactory(session, scope)
            while (true) {

                // Wait until we have at least one active receiver
                jdwpPacketSharedFlow.subscriptionCount.waitUntil { it > 0 }

                logger.verbose { "Waiting for next JDWP packet from session" }
                val sessionPacket = try {
                    activeJdwpSession().receivePacket()
                } catch (throwable: Throwable) {
                    // Cancellation cancels the jdwp session scope, which cancel the scope
                    // of all receivers, so they will terminate with cancellation too.
                    throwable.rethrowCancellation()

                    // Reached EOF, flow terminates
                    if (throwable is EOFException) {
                        logger.debug { "JDWP session has ended with EOF" }
                    }
                    logger.verbose(throwable) { "Emitting JDWP session exception '$throwable' to shared flow of receivers" }

                    // Ensure all new receivers receive the same exception if/when they
                    // try to connect to this session...
                    jdwpSessionException.complete(throwable)

                    // ...and emit the exception to all currently active receivers
                    jdwpPacketSharedFlow.emit(throwable)
                    break
                }

                processSessionPacket(sharedPayloadProviderFactory, sessionPacket, workBuffer) { packet ->
                    jdwpMonitor?.onReceivePacket(packet)
                    logger.verbose { "Emitting session packet $packet to shared flow of receivers" }
                    jdwpPacketSharedFlow.emit(packet)
                    jdwpFilter.afterReceivePacket(packet)
                }
            }
        }
    }

    private suspend inline fun processSessionPacket(
        sharedPayloadProviderFactory: SharedPayloadProviderFactory,
        sessionPacket: JdwpPacketView,
        workBuffer: ResizableBuffer,
        block: (JdwpPacketView) -> Unit
    ) {
        if (sessionPacket.isThreadSafeAndImmutable) {
            // JDWP packet can be exposed directly (and safely) to upper layers
            block(sessionPacket)
        } else {
            // "Emit" a thread-safe version of "sessionPacket" to all receivers
            sessionPacket.withPayload { sessionPacketPayload ->
                // Note: "sessionPacketPayload" is an input channel directly connected to the
                // underlying connection.
                // We create a scoped payload channel so that
                // 1) the payload is thread-safe for all receivers, and
                // 2) cancellation of any receiver does not close the underlying socket
                // 3) "shutdown" does not close the underlying PayloadProvider (that is taken
                // care of by the JdwpSession class)
                val payloadProvider =
                    sharedPayloadProviderFactory.create(sessionPacket, sessionPacketPayload)
                EphemeralJdwpPacket.fromPacket(sessionPacket, payloadProvider).use { packet ->
                    block(packet)
                    // Packet should not be used after this, because payload of the jdwp packet
                    // from the underlying jdwp session is about to become invalid.
                    packet.shutdown(workBuffer)
                }
            }
        }
    }

    private suspend fun <T> StateFlow<T>.waitUntil(predicate: suspend (T) -> Boolean) {
        this.first { predicate(it) }
    }

    private class JdwpPacketReceiverImpl(
        private val jdwpSession: SharedJdwpSessionImpl
    ) : JdwpPacketReceiver {

        private var name: String = ""

        private var activation: suspend () -> Unit = { }

        private var filterId: SharedJdwpSessionFilter.FilterId? = null

        override fun withName(name: String): JdwpPacketReceiver {
            this.name = name
            return this
        }

        override fun withFilter(filterId: SharedJdwpSessionFilter.FilterId): JdwpPacketReceiver {
            this.filterId = filterId
            return this
        }

        override fun withActivation(activationBlock: suspend () -> Unit): JdwpPacketReceiver {
            this.activation = activationBlock
            return this
        }

        override suspend fun receive(receiver: suspend (JdwpPacketView) -> Unit) {
            withContext(jdwpSession.session.ioDispatcher) {
                coroutineScope {
                    val receiveScope = this
                    val receiveLogger = jdwpSession.logger.withPrefix("Receiver '$name': ")

                    // The coroutine scope used to run the activation block is a child scope, so
                    // that (non cancellation) exceptions are propagated up to this coroutine.
                    val activationScope = createChildScope(isSupervisor = false)

                    val collectingFlow = jdwpSession.jdwpPacketSharedFlow.onSubscription {
                        // At this point, we know the shared flow collector is active and
                        // will receive any value emitted in the "activation" block. We launch the
                        // activation block in a child coroutine to ensure it can run concurrently
                        // with the shared flow collector.
                        activationScope.launchActivationJob(receiveLogger, receiveScope)

                        // Send replay packets to the receiver (synchronously, so that the
                        // receiver receives the replay packets on the same coroutine
                        // context it invoked this "receive" method)
                        sendReplayPackets(receiveLogger, receiver)

                        // If the JDWP session has ended (with an exception), we emit the
                        // exception to the receiver so that it terminates
                        emitJdwpSessionExceptionIfAvailable()
                    }

                    // Collect the shared flow and invoke "receiver" until we reach EOF
                    collectSharedFlow(receiver, receiveLogger, collectingFlow)

                    // We reached EOF: Send a custom "EOFCancellationException" to terminate the
                    // "activation" coroutine (in case it has not completed yet)
                    activationScope.cancel(EOFCancellationException("Reached EOF"))
                }
            }
        }

        /**
         * Note: Wrt to concurrency: The session can end at any time when receiving packets
         * (see [SharedJdwpSessionImpl.sendReceivedPackets]), so
         * [SharedJdwpSessionImpl.jdwpSessionException] may not be
         * [completed][CompletableDeferred.isCompleted] right away when the session ends.
         *
         * So, if the [JdwpSession] is ending (concurrently, with an exception), we have 2
         * possible code paths:
         *
         * * If [SharedJdwpSessionImpl.jdwpSessionException] is already
         * [completed][CompletableDeferred.isCompleted], then emitting the exception to the
         * [FlowCollector] ensures it will be collected by the
         * [JdwpPacketReceiverImpl.collectSharedFlow] method, and rethrown to the
         * [JdwpPacketReceiver]. This case is taken care of by this method.
         *
         * * If [SharedJdwpSessionImpl.jdwpSessionException] is **not**
         * [completed][CompletableDeferred.isCompleted] yet,
         * it is guaranteed the [exception][Throwable] will be collected from the
         * [SharedJdwpSessionImpl.jdwpPacketSharedFlow] because
         * [SharedJdwpSessionImpl.sendReceivedPackets] emits the exception **after**
         * completing [SharedJdwpSessionImpl.jdwpSessionException]. This case is taken
         * care of by the [collectSharedFlow] method.
         */
        private suspend fun FlowCollector<Any>.emitJdwpSessionExceptionIfAvailable() {
            if (jdwpSession.jdwpSessionException.isCompleted) {
                // Note: using `await` since `getCompleted` is still experimental API
                emit(jdwpSession.jdwpSessionException.await())
            }
        }

        private suspend fun sendReplayPackets(
            receiveLogger: AdbLogger,
            receiver: suspend (JdwpPacketView) -> Unit
        ) {
            receiveLogger.verbose { "Emitting ${jdwpSession.replayPackets.size} replay packet(s)" }
            jdwpSession.replayPackets.forEach { replyPacket ->
                callReceiverWithPacket(receiver, receiveLogger, replyPacket)
            }
        }

        /**
         * Launch the [activation] callback as a child of this [CoroutineScope] that runs
         * concurrently with the [jdwpPacketSharedFlow] collector
         *
         * Note on exceptions: All exceptions, except for [EOFCancellationException], should
         * be propagated to this [CoroutineScope].
         */
        private fun CoroutineScope.launchActivationJob(
            receiveLogger: AdbLogger,
            parentScope: CoroutineScope
        ) {
            launch {
                receiveLogger.verbose { "calling 'activation' callback" }
                this@JdwpPacketReceiverImpl.activation()
            }.also {
                it.invokeOnCompletion { throwable ->
                    when (throwable) {
                        null -> {
                            // The "activation" job completed successfully: nothing to do
                        }

                        is EOFCancellationException -> {
                            // The flow completed successfully with EOF: nothing to do
                        }

                        is CancellationException -> {
                            // The "activation" job was cancelled for some other reason than
                            // EOF: propagate cancellation to parent scope (i.e. the flow)
                            parentScope.cancel(throwable)
                        }

                        else -> {
                            // Any other exception is a "non-cancellation" exception, and it
                            // is automatically propagated to the parent scope (i.e. the flow)
                        }
                    }
                }
            }
        }

        /**
         * Collects [sharedFlow], emitting [JdwpPacketView] using [callReceiverWithPacket] to
         * ensure serialized invocations of all active collectors.
         */
        private suspend fun collectSharedFlow(
            receiver: suspend (JdwpPacketView) -> Unit,
            receiveLogger: AdbLogger,
            sharedFlow: SerializedSharedFlow<Any>
        ) {
            sharedFlow
                .takeWhile { anyValue ->
                    receiveLogger.verbose { "Processing value from shared flow $anyValue" }
                    when (anyValue) {
                        is JdwpPacketView -> {
                            callReceiverWithPacket(receiver, receiveLogger, packet = anyValue)
                            true // Keep collecting the shared flow
                        }

                        is EOFException -> {
                            receiveLogger.debug { "EOF reached, ending flow" }
                            false // We are done collecting the shared flow
                        }

                        is Throwable -> {
                            throw anyValue
                        }

                        else -> {
                            receiveLogger.error("Unknown value in share flow: $anyValue")
                            throw IllegalStateException("Invalid value in flow: $anyValue")
                        }
                    }
                }
                .collect()
        }

        private suspend fun callReceiverWithPacket(
            receiver: suspend (JdwpPacketView) -> Unit,
            receiveLogger: AdbLogger,
            packet: JdwpPacketView
        ) {
            if (jdwpSession.jdwpFilter.filterReceivedPacket(filterId, packet)) {
                receiveLogger.verbose { "Emitting packet flow: $packet" }
                receiver(packet)
            } else {
                receiveLogger.verbose { "Skipping packet due to filter: $packet" }
            }
        }

        private class EOFCancellationException(message: String) : CancellationException(message)
    }

    private class SharedPayloadProviderFactory(
        session: AdbSession,
        private val scope: CoroutineScope
    ) : PayloadProviderFactory(session) {

        private val reusableBuffer = ByteBufferHolder()

        override fun createLargePacketProvider(
            packet: JdwpPacketView,
            packetPayload: AdbInputChannel,
            packetPayloadLength: Int
        ): PayloadProvider {
            // Wrap the payload with a "scoped" payload provider to ensure proper cancellation
            // behavior, and also make sure to re-use our reusable ByteBuffer to prevent
            // extra ByteBuffer allocation
            return ScopedPayloadProvider(scope, packetPayload, reusableBuffer)
        }

    }

    /**
     * A [PayloadProvider] that wraps a [payload][AdbInputChannel] using a
     * [ScopedAdbRewindableInputChannel] so that cancellation of pending read operations
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
        payload: AdbInputChannel,
        /**
         * Reusable [ByteBufferHolder] used to store (and buffer) the content of [payload]
         */
        reusableBuffer: ByteBufferHolder
    ) : PayloadProvider {

        private var closed = false

        /**
         * Locks access to [scopedPayload] to guarantee there is at most a single reader
         */
        private val mutex = Mutex()

        /**
         * The [ScopedAdbRewindableInputChannel] wrapping the original [AdbInputChannel], to ensure
         * cancellation of [AdbInputChannel.read] operations don't close the [AdbInputChannel].
         */
        private var scopedPayload = ScopedAdbRewindableInputChannel(scope, reusableBuffer, payload)

        override suspend fun acquirePayload(): AdbInputChannel {
            throwIfClosed()
            mutex.lock()
            scopedPayload.waitForPendingRead()
            scopedPayload.rewind()
            return scopedPayload
        }

        override fun releasePayload() {
            mutex.unlock()
        }

        override suspend fun shutdown(workBuffer: ResizableBuffer) {
            closed = true
            mutex.withLock {
                scopedPayload.waitForPendingRead()
            }
        }

        override suspend fun toOffline(workBuffer: ResizableBuffer): PayloadProvider {
            return withPayload {
                PayloadProvider.forInputChannel(scopedPayload.toOffline(workBuffer))
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
         * An [AdbRewindableInputChannel] that reads from an [AdbInputChannel] in a custom
         * [CoroutineScope] so that cancellation does not affect the source [AdbInputChannel].
         *
         * Note:
         * * The [close] method may invalidate the initial [AdbInputChannel] if there is a pending
         * [read] operation. Caller should call [waitForPendingRead] to prevent this.
         * * This class is *not* thread-safe
         */
        private class ScopedAdbRewindableInputChannel(
            private val scope: CoroutineScope,
            reusableBuffer: ByteBufferHolder,
            input: AdbInputChannel
        ) : AdbRewindableInputChannel, SupportsOffline<AdbRewindableInputChannel> {

            private var currentReadJob: Job? = null

            /**
             * Ensure we have a [AdbRewindableInputChannel] so we support
             * [AdbRewindableInputChannel.rewind]
             */
            private val rewindableInput = if (input is AdbRewindableInputChannel) {
                input
            } else {
                AdbRewindableInputChannel.forInputChannel(input, reusableBuffer)
            }

            override suspend fun rewind() {
                throwIfPendingRead()
                rewindableInput.rewind()
            }

            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                return scopedRead { rewindableInput.readBuffer(buffer, timeout, unit) }
            }

            override suspend fun readExactly(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                return scopedRead { rewindableInput.readExactly(buffer, timeout, unit) }
            }

            suspend fun waitForPendingRead() {
                currentReadJob?.join()
            }

            override suspend fun toOffline(workBuffer: ResizableBuffer): AdbRewindableInputChannel {
                return scopedRead {
                    rewindableInput.toOffline(workBuffer)
                }
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
     * Send [JdwpPacketView] to the underlying [activeJdwpSession] in such a way that cancellation
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
                sharedJdwpSession.activeJdwpSession().sendPacket(packet)
            }.await()
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
