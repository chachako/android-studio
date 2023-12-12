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

import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelFactory
import com.android.adblib.AdbServerSocket
import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.adbLogger
import com.android.adblib.tools.debugging.AtomicStateFlow
import com.android.adblib.tools.debugging.JdwpPacketReceiver
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.JdwpSessionPipeline
import com.android.adblib.tools.debugging.JdwpSessionProxyStatus
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.jdwpSessionPipelineFactoryList
import com.android.adblib.tools.debugging.sendPacket
import com.android.adblib.tools.debugging.utils.NoDdmsPacketFilterFactory
import com.android.adblib.tools.debugging.utils.logIOCompletionErrors
import com.android.adblib.tools.debugging.utils.receiveAll
import com.android.adblib.withPrefix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.io.EOFException

/**
 * Implementation of a JDWP proxy for the process [pid] on a given device. The proxy creates a
 * [server socket][AdbChannelFactory.createServerSocket] on `localhost` (see
 * [JdwpSessionProxyStatus.socketAddress]), then it accepts JDWP connections from external
 * Java debuggers (e.g. IntelliJ or Android Studio) on that server socket. Each time a new
 * socket connection is opened by an external debugger, the proxy opens a JDWP session to the
 * process on the device (see [JdwpSession.openJdwpSession]) and forwards (both ways) JDWP protocol
 * packets between the external debugger and the process on the device.
 */
internal class JdwpSessionProxy(
    private val device: ConnectedDevice,
    private val pid: Int,
    private val jdwpSessionProvider: SharedJdwpSessionProvider,
) {

    private val session: AdbSession
        get() = device.session

    private val logger = adbLogger(device.session)
        .withPrefix("${device.session} - $device - pid=$pid - ")

    suspend fun execute(processStateFlow: AtomicStateFlow<JdwpProcessProperties>) {
        // Create server socket and start accepting JDWP connections
        session.channelFactory.createServerSocket().use { serverSocket ->
            val socketAddress = serverSocket.bind()
            processStateFlow.updateProxyStatus { it.copy(socketAddress = socketAddress) }
            try {
                // Retry proxy as long as process is active (i.e. as long as we have not been
                // cancelled)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    logger.debug { "Waiting for debugger connection on port ${socketAddress.port}" }

                    // Use "supervisorScope" so that cancellation from within the
                    // "acceptOneJdwpConnection" coroutine does not propagate "up", since we
                    // need the proxy to keep running as long as the JDWP process is active.
                    // Note that cancellation coming from the proxy does propagate "down"
                    // and cancels "acceptOneJdwpConnection" as needed
                    supervisorScope {
                        try {
                            acceptOneJdwpConnection(serverSocket, processStateFlow)
                        } catch (t: Throwable) {
                            // We can only log errors, as there is no component to propagate
                            // the exception to. Also, we need to keep the proxy running
                            // as long as the parent process (i.e. scope) is active.
                            logger.logIOCompletionErrors(t)
                        }
                    }
                }
            } finally {
                processStateFlow.updateProxyStatus { it.copy(socketAddress = null) }
            }
        }
    }

    private suspend fun acceptOneJdwpConnection(
        serverSocket: AdbServerSocket,
        processStateFlow: AtomicStateFlow<JdwpProcessProperties>
    ) {
        serverSocket.accept().use { debuggerSocket ->
            logger.debug { "External debugger connection accepted: $debuggerSocket" }
            processStateFlow.updateProxyStatus { it.copy(isExternalDebuggerAttached = true) }
            try {
                proxyJdwpSession(debuggerSocket)
            } finally {
                logger.debug { "Debugger proxy has ended proxy connection" }
                processStateFlow.updateProxyStatus { it.copy(isExternalDebuggerAttached = false) }
            }
        }
    }

    private suspend fun proxyJdwpSession(debuggerSocket: AdbChannel) {
        logger.debug { "Start proxying socket between external debugger and process on device" }
        jdwpSessionProvider.withSharedJdwpSession { deviceSession ->
            // The JDWP Session proxy does not need to send custom JDWP packets,
            // so we pass a `null` value for `nextPacketIdBase`.
            JdwpSession.wrapSocketChannel(device, debuggerSocket, pid, null).use { debuggerSession ->
                coroutineScope {
                    // Note about termination of this coroutine scope:
                    // * [automatic] The common case is to wait for job1 and job2 to complete
                    //   successfully
                    // * [automatic] If job1 or job2 throws non-cancellation exception, the
                    //   exception is propagated to the scope and the scope is cancelled
                    // * [manual] If job1 or job2 throws a CancellationException, we need to
                    //   propagate cancellation to the scope so that all jobs are cancelled
                    //   together.

                    val debuggerPipeline = createDebuggerPipeline(debuggerSession)

                    // We need to ensure forwarding from the device starts
                    val deferredStart = CompletableDeferred<Unit>()

                    // Forward packets from external debugger to jdwp process on device
                    val job1 = launch(session.host.ioDispatcher) {
                        forwardDebuggerJdwpSession(
                            debuggerPipeline,
                            deviceSession,
                            deferredStart
                        )
                    }

                    // Forward packets from jdwp process on device to external debugger
                    val job2 = launch(session.host.ioDispatcher) {
                        forwardDeviceJdwpSession(
                            deviceSession,
                            debuggerPipeline,
                            deferredStart
                        )
                    }

                    // Ensure both jobs complete as soon as one completes, so that we don't keep
                    // a forwarding coroutine active when the other "side" has been closed.
                    job1.invokeOnCompletion { throwable ->
                        cancelJobs(throwable, job1, job2)
                    }
                    job2.invokeOnCompletion { throwable ->
                        cancelJobs(throwable, job1, job2)
                    }
                }
            }
        }
    }

    private fun cancelJobs(throwable: Throwable?, job1: Job, job2: Job) {
        val cancellationException = (throwable as? CancellationException)
            ?: CancellationException("JDWP session proxy has completed", throwable)

        // Ensure both coroutines are cancelled
        job1.cancel(cancellationException)
        job2.cancel(cancellationException)
    }

    private fun createDebuggerPipeline(debuggerSession: JdwpSession): JdwpSessionPipeline {
        // The pipeline source is always the connection to the debugger
        val debuggerPipeline = DebuggerSessionPipeline(session, debuggerSession, pid)

        // Call each factory in priority order
        return session.jdwpSessionPipelineFactoryList
            .sortedBy {
                it.priority
            }
            .fold(debuggerPipeline) { pipeline, factory ->
                return factory.create(device, pid, pipeline) ?: pipeline
            }
    }

    /**
     * Forwards JDWP packets from the external debugger [JdwpSessionPipeline] to the device
     * [SharedJdwpSession].
     */
    private suspend fun forwardDebuggerJdwpSession(
        debuggerPipeline: JdwpSessionPipeline,
        deviceSession: SharedJdwpSession,
        deferredStart: CompletableDeferred<Unit>
    ) {
        // Note: Throws an EOFException exception when channel is closed
        debuggerPipeline.receiveChannel.receiveAll { packet ->
            // Wait until receiver has started to avoid skipping packets
            deferredStart.await()

            logger.verbose { "debugger->device: Forwarding packet to shared jdwp session: $packet" }
            deviceSession.sendPacket(packet)
        }
    }

    /**
     * Forwards JDWP packets from the device [SharedJdwpSession] to the external
     * debugger [JdwpSession].
     */
    private suspend fun forwardDeviceJdwpSession(
        deviceSession: SharedJdwpSession,
        debuggerPipeline: JdwpSessionPipeline,
        deferredStart: CompletableDeferred<Unit>
    ) {
        deviceSession.newPacketReceiver()
            .withName("device session forwarder")
            .withNoDdmsPacketFilter()
            .withActivation {
                logger.debug { "device->debugger: Device session is ready to receive packets" }
                deferredStart.complete(Unit)
            }.receive { packet ->
                logger.verbose { "device->debugger: Forwarding packet to session: $packet" }
                debuggerPipeline.sendPacket(packet)
            }
        throw EOFException("JDWP session with process ${deviceSession.pid} device ${deviceSession.device} reached EOF")
    }

    /**
     * Apply the "no ddms packet" filter so all DDMS command/reply packets are filtered out.
     *
     * * Filtering out DDMS packets is not strictly required for JDWP compliant debuggers
     *   (i.e. The JDWP spec. mentions extension packets should be ignored by debuggers
     *   that don't support them), but the reference JDI implementation emits a `System.err`
     *   message when any unsupported packet is received leading to the assumption there
     *   is a serious issue.
     *   (see https://github.com/JetBrains/intellij-deps-jdi/blob/9d99ceeacefabae3e4c37b411e5f4b7637aba162/src/main/java/com/jetbrains/jdi/TargetVM.java#L244)
     *
     * * Filtering out DDMS packets also slightly improve performances of this debugger
     *   proxy, as DDMS packets are not sent/received on the communication channel
     *   between the external debugger [JdwpSession] and the device [SharedJdwpSession].
     *
     * The main drawback of applying the DDMS filter is that, if needed in the future,
     * it prevents any external debugger connecting to this proxy from directly sending
     * DDMS commands to an Android process.
     */
    private fun JdwpPacketReceiver.withNoDdmsPacketFilter(): JdwpPacketReceiver {
        return withFilter(NoDdmsPacketFilterFactory.filterId)
    }

    private fun AtomicStateFlow<JdwpProcessProperties>.updateProxyStatus(
        updater: (JdwpSessionProxyStatus) -> JdwpSessionProxyStatus
    ) {
        this.update {
            it.copy(jdwpSessionProxyStatus = updater(it.jdwpSessionProxyStatus))
        }
        logger.verbose { "Updated stateflow: ${this.value}" }
    }
}
