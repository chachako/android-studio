/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adblib.tools.debugging.processinventory.server

import com.android.adblib.AdbChannel
import com.android.adblib.AdbLogger
import com.android.adblib.AdbServerSocket
import com.android.adblib.AdbSession
import com.android.adblib.IsThreadSafe
import com.android.adblib.adbLogger
import com.android.adblib.property
import com.android.adblib.tools.debugging.processinventory.AdbLibToolsProcessInventoryServerProperties.UNUSED_DEVICE_REMOVAL_DELAY
import com.android.adblib.tools.debugging.processinventory.impl.ProcessInventoryServerSocketProtocol
import com.android.adblib.tools.debugging.processinventory.protos.ProcessInventoryServerProto
import com.android.adblib.tools.debugging.processinventory.protos.ProcessInventoryServerProto.Request
import com.android.adblib.tools.debugging.processinventory.protos.ProcessInventoryServerProto.Response
import com.android.adblib.utils.closeOnException
import com.android.adblib.withPrefix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

internal class ProcessInventoryServerInstance(
    private val session: AdbSession,
    config: ProcessInventoryServerConfiguration,
    private val parentScope: CoroutineScope,
    private val serverSocket: AdbServerSocket,
) {
    private val serverInstanceDescription = config.serverDescription +
            "-EphemeralInstanceId(#${nextInstanceId()})"

    private val logger = adbLogger(session)
        .withPrefix("$session - $serverInstanceDescription - ")

    private val activeDevicesMap = DeviceMap(session, parentScope)

    fun runAsync(): Job = parentScope.launch {
        runCatching {
            logger.info { "Starting server at server socket '$serverSocket'" }
            while (true) {
                // Accept one connection and handle it asynchronously, ensuring socket is closed
                // in all cases (cancellation, errors and success)
                serverSocket.accept().closeOnException { socketChannel ->
                    logger.debug { "Accepted new socket connection: $socketChannel" }
                    launch {
                        RequestHandler(
                            session,
                            logger,
                            serverInstanceDescription,
                            activeDevicesMap,
                            socketChannel
                        ).handleRequest()
                    }.invokeOnCompletion {
                        logger.debug(it) { "Closing socket channel for request" }
                        socketChannel.close()
                    }
                }
            }
        }.onFailure { throwable ->
            currentCoroutineContext().ensureActive()
            // Log exception (nothing else we can do)
            logger.info(throwable) { "Accept operation failed due to unexpected error" }
        }
    }.also {
        it.invokeOnCompletion {
            logger.info { "Stopping server running at server socket '$serverSocket' (throwable='$it')" }
        }
    }

    /**
     * Handles a single server request asynchronously on a given [socketChannel].
     *
     * Note: Requests can be short (e.g. [Request.UpdateDeviceRequestPayload] ) or long-running
     * (e.g. [Request.TrackDeviceRequestPayload]).
     */
    private class RequestHandler(
        session: AdbSession,
        private val logger: AdbLogger,
        serverInstanceDescription: String,
        private val activeDevicesMap: DeviceMap,
        private val socketChannel: AdbChannel
    ) {

        private val protocolSocket = ProcessInventoryServerSocketProtocol(session, socketChannel)
            .forServer(serverInstanceDescription)

        /**
         * Asynchronously handles a single server request on [socketChannel], closing the
         * [socketChannel] when the request is done. If the request fails, there is an attempt
         * to send an error message to the peer.
         */
        suspend fun handleRequest() {
            runCatching {
                logger.verbose { "Processing one request on client socket '$socketChannel'" }
                processOneRequest()
            }.onFailure { throwable ->
                currentCoroutineContext().ensureActive()

                // Attempt to send error to peer, which may fail if socket has been closed
                runCatching {
                    val requestName =
                        protocolSocket.lastRequest?.payloadCase?.toString() ?: "<unknown>"
                    protocolSocket.writeErrorResponse("Error processing request '$requestName' (${throwable.message})")
                }

                // Log exception (nothing else we can do)
                when (throwable) {
                    is IOException, is TimeoutException -> {
                        // IO errors can happen anytime (peer can disappear, close the socket, etc.)
                        logger.debug(throwable) { "Error processing request " }
                    }

                    else -> {
                        logger.info(throwable) { "Unexpected error processing request" }
                    }
                }
            }
        }

        private suspend fun processOneRequest() {
            val request = protocolSocket.readRequest()
            logger.verbose { "Processing request ${request.payloadCase}" }
            processRequest(request).collect { response ->
                logger.verbose { "Sending one response: $response" }
                protocolSocket.writeResponse(response)
            }
            protocolSocket.shutdown()
            logger.verbose { "Done processing request ${request.payloadCase}" }
        }

        private suspend fun processRequest(request: Request) = flow {
            when {
                request.hasTrackDeviceRequestPayload() -> {
                    emitAll(trackDeviceProcesses(request.trackDeviceRequestPayload))
                }

                request.hasUpdateDeviceRequestPayload() -> {
                    notifyUpdateDevice(request.updateDeviceRequestPayload)
                    emitOkResponse()
                }

                else -> {
                    emitErrorResponse("Request is not supported by this server")
                }
            }
        }

        /**
         * Tracks changes to the processes of a given device for as long as the device is
         * active.
         */
        private suspend fun trackDeviceProcesses(
            trackDeviceRequest: Request.TrackDeviceRequestPayload
        ) = flow {
            val deviceId = trackDeviceRequest.deviceId
            // Acquire device process catalog for device, collect it and emit response to our flow
            activeDevicesMap.withDeviceProcessCatalog(deviceId) { deviceCatalog ->
                deviceCatalog.trackProcesses().collect { processUpdates ->
                    emitOkResponse { builder ->
                        builder.setTrackDeviceResponsePayload(
                            Response.TrackDeviceResponsePayload.newBuilder()
                                .setProcessUpdates(processUpdates)
                        )
                    }
                }
            }
        }

        private fun notifyUpdateDevice(updateDeviceRequest: Request.UpdateDeviceRequestPayload) {
            val deviceId = updateDeviceRequest.deviceId
            activeDevicesMap.withDeviceProcessCatalog(deviceId) { deviceCatalog ->
                deviceCatalog.updateProcessList(updateDeviceRequest.processUpdates)
            }
        }

        private suspend fun FlowCollector<Response>.emitOkResponse(block: (Response.Builder) -> Unit = {}) {
            emit(protocolSocket.buildOkResponse(block))
        }

        private suspend fun FlowCollector<Response>.emitErrorResponse(message: String) {
            emit(protocolSocket.buildErrorResponse(message))
        }
    }

    /**
     * A simple wrapper for a thread-safe [Map] of [ProcessInventoryServerProto.DeviceId] to
     * [DeviceProcessCatalog] instances.
     *
     * Note: Since there is no deterministic way of knowing when a device is disconnected, we rely
     * on an "inactive timeout" specified in the [removalDelay] parameter, i.e. when a device has
     * not been queried or updated within that specified timeout, the device is removed from
     * the internal list of [DeviceProcessCatalog].
     */
    @IsThreadSafe
    private class DeviceMap(
        private val session: AdbSession,
        parentScope: CoroutineScope,
        private val removalDelay: Duration = session.property(UNUSED_DEVICE_REMOVAL_DELAY),
        clock: Clock = Clock.systemUTC()
    ) {

        private val logger = adbLogger(session)

        private val map =
            UsageTrackingMap<ProcessInventoryServerProto.DeviceId, DeviceProcessCatalog>(
                logger,
                parentScope,
                removalDelay,
                clock,
                factory = { deviceId -> DeviceProcessCatalog(session, deviceId) }
            )

        inline fun <R> withDeviceProcessCatalog(
            deviceId: ProcessInventoryServerProto.DeviceId,
            block: (DeviceProcessCatalog) -> R
        ): R {
            return map.withValue(deviceId) { deviceCatalog ->
                block(deviceCatalog)
            }
        }
    }

    companion object {

        private val instanceCount = AtomicLong(0)

        fun nextInstanceId(): Long {
            return instanceCount.incrementAndGet()
        }
    }
}
