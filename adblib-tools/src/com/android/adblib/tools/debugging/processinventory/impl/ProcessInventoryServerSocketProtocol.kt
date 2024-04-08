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
package com.android.adblib.tools.debugging.processinventory.impl

import com.android.adblib.AdbChannel
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLogger
import com.android.adblib.AdbOutputChannel
import com.android.adblib.AdbSession
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.adbLogger
import com.android.adblib.getOrPutSynchronized
import com.android.adblib.skipRemaining
import com.android.adblib.tools.debugging.processinventory.protos.ProcessInventoryServerProto
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.ByteOrder

/**
 * Wrapper around the network socket protocol used to send [ProcessInventoryServerProto]
 * messages. This can be used from both the server and client side of the protocol.
 */
internal class ProcessInventoryServerSocketProtocol(
    private val session: AdbSession,
    private val socket: AdbChannel
) {

    private val logger = adbLogger(session)

    private val workBuffer = newProtocolBuffer()
    private val reader = Reader(logger, socket, workBuffer)
    private val writer = Writer(logger, socket, workBuffer)

    fun forClient(clientDescription: String): ClientProtocol {
        return ClientProtocol(clientDescription)
    }

    fun forServer(serverDescription: String): ServerProtocol {
        return ServerProtocol(serverDescription)
    }

    inner class ClientProtocol(private val clientDescription: String) {

        /**
         * Invokes the [ProcessInventoryServerProto.Request.RequestType.REQUEST_TRACK_DEVICE] service
         * on the server a given device [deviceSerial], returning a [Flow] of
         * [ProcessInventoryServerProto.Response] each time there are updates to the inventory
         * of [ProcessInventoryServerProto.JdwpProcessInfo] of the corresponding device.
         */
        suspend fun trackDevice(deviceSerial: String): Flow<ProcessInventoryServerProto.Response> =
            flow {
                val request = ProcessInventoryServerProto.Request.newBuilder()
                    .setClientDescription(clientDescription)
                    .setTrackDeviceRequestPayload(
                        ProcessInventoryServerProto.Request.TrackDeviceRequestPayload
                            .newBuilder()
                            .setDeviceId(deviceId(deviceSerial))
                    )
                    .build()

                writeRequest(request)

                while (true) {
                    val response = readResponse()
                    emit(response)
                }
            }

        /**
         * Invokes the [ProcessInventoryServerProto.Request.RequestType.REQUEST_UPDATE_DEVICE] service
         * on the server given a device [deviceSerial], returning a
         * [ProcessInventoryServerProto.Response] with "ok" status.
         */
        suspend fun sendDeviceProcessInfo(
            deviceSerial: String,
            processInfo: ProcessInventoryServerProto.JdwpProcessInfo
        ): ProcessInventoryServerProto.Response {
            return sendDeviceProcessInfoUpdates(deviceSerial, listOf(processInfo), emptyList())
        }

        suspend fun sendDeviceProcessInfoList(
            deviceSerial: String,
            processInfoUpdateList: List<ProcessInventoryServerProto.JdwpProcessInfo>,
        ): ProcessInventoryServerProto.Response {
            return sendDeviceProcessInfoUpdates(deviceSerial, processInfoUpdateList, emptyList())
        }

        /**
         * Invokes the [ProcessInventoryServerProto.Request.RequestType.REQUEST_UPDATE_DEVICE] service
         * on the server given a device [deviceSerial], returning a
         * [ProcessInventoryServerProto.Response] with "ok" status.
         */
        suspend fun sendDeviceProcessRemoval(
            deviceSerial: String,
            pid: Int
        ): ProcessInventoryServerProto.Response {
            return sendDeviceProcessInfoUpdates(deviceSerial, emptyList(), listOf(pid))
        }

        private suspend fun sendDeviceProcessInfoUpdates(
            deviceSerial: String,
            processInfoUpdateList: List<ProcessInventoryServerProto.JdwpProcessInfo>,
            removedProcessList: List<Int>
        ): ProcessInventoryServerProto.Response {
            val request = ProcessInventoryServerProto.Request
                .newBuilder()
                .setClientDescription(clientDescription)
                .setUpdateDeviceRequestPayload(
                    ProcessInventoryServerProto.Request.UpdateDeviceRequestPayload
                        .newBuilder()
                        .setDeviceId(deviceId(deviceSerial))
                        .setProcessUpdates(
                            ProcessInventoryServerProto.ProcessUpdates.newBuilder()
                                .addAllProcessUpdate(
                                    processInfoUpdateList.map {
                                        ProcessInventoryServerProto.ProcessUpdate.newBuilder()
                                            .setProcessUpdated(it)
                                            .build()
                                    } + removedProcessList.map {
                                        ProcessInventoryServerProto.ProcessUpdate.newBuilder()
                                            .setProcessTerminatedPid(it)
                                            .build()
                                    }
                                )
                        )
                )
                .build()

            writeRequest(request)
            return readResponse()
        }

        private suspend fun readResponse(): ProcessInventoryServerProto.Response {
            return reader.readResponse()
        }

        private suspend fun writeRequest(request: ProcessInventoryServerProto.Request) {
            writer.writeRequest(request)
        }
    }

    inner class ServerProtocol(private val serverDescription: String) {
        var lastRequest: ProcessInventoryServerProto.Request? = null

        suspend fun readRequest(): ProcessInventoryServerProto.Request {
            return reader.readRequest().also {
                lastRequest = it
            }
        }

        suspend fun writeResponse(response: ProcessInventoryServerProto.Response) {
            writer.writeResponse(response)
        }

        suspend fun writeErrorResponse(message: String) {
            writer.writeErrorResponse(buildErrorResponse(message))
        }

        fun buildOkResponse(
            block: (ProcessInventoryServerProto.Response.Builder) -> Unit
        ): ProcessInventoryServerProto.Response {
            return ProcessInventoryServerProto.Response.newBuilder()
                .setServerDescription(serverDescription)
                .setOk(true)
                .also { block(it) }
                .build()
        }

        fun buildErrorResponse(message: String): ProcessInventoryServerProto.Response {
            return ProcessInventoryServerProto.Response.newBuilder()
                .setServerDescription(serverDescription)
                .setOk(false)
                .setErrorMessage(message)
                .build()
        }

        suspend fun shutdown() {
            // Orderly shutdown requires waiting for EOF to ensure peer
            // received all data.
            socket.shutdownOutput()
            socket.skipRemaining(workBuffer)
        }
    }

    private fun deviceId(deviceSerial: String): ProcessInventoryServerProto.DeviceId {
        return ProcessInventoryServerProto.DeviceId
            .newBuilder()
            .setAdbSessionId(session.processInventorySessionId())
            .setSerialNumber(deviceSerial)
            .build()
    }

    private fun newProtocolBuffer(): ResizableBuffer {
        return ResizableBuffer().order(ByteOrder.LITTLE_ENDIAN)
    }

    private class Reader(
        private val logger: AdbLogger,
        private val inputChannel: AdbInputChannel,
        private val workBuffer: ResizableBuffer
    ) {
        /**
         * Whether the input stream is in between fully written request or response packet.
         */
        var readingAllowed = true

        suspend fun readRequest(): ProcessInventoryServerProto.Request {
            // Response not allowed until we fully read this request
            check(readingAllowed)
            readingAllowed = false

            val length = readMessageLength()
            workBuffer.clear()
            inputChannel.readExactly(workBuffer.forChannelRead(length))
            val buffer = workBuffer.afterChannelRead()
            return ProcessInventoryServerProto.Request.parseFrom(buffer).also {
                logger.verbose { "Read request: $it" }
                // A response is allowed after reading a full request
                readingAllowed = true
            }
        }

        suspend fun readResponse(): ProcessInventoryServerProto.Response {
            // Response not allowed until we fully read this one
            check(readingAllowed)
            readingAllowed = false

            val length = readMessageLength()
            workBuffer.clear()
            inputChannel.readExactly(workBuffer.forChannelRead(length))
            val buffer = workBuffer.afterChannelRead()
            return ProcessInventoryServerProto.Response.parseFrom(buffer).also {
                logger.verbose { "Read response: $it" }
                // A response is allowed after reading a full response
                readingAllowed = true
            }
        }

        private suspend fun readMessageLength(): Int {
            workBuffer.clear()
            inputChannel.readExactly(workBuffer.forChannelRead(4))
            val buffer = workBuffer.afterChannelRead().order(ByteOrder.LITTLE_ENDIAN)
            return buffer.getInt()
        }
    }

    private class Writer(
        private val logger: AdbLogger,
        private val outputChannel: AdbOutputChannel,
        private val workBuffer: ResizableBuffer
    ) {

        /**
         * Whether the output stream is in between fully written request or response packet.
         */
        var writingAllowed = true

        suspend fun writeResponse(response: ProcessInventoryServerProto.Response) {
            check(writingAllowed)
            writingAllowed = false
            logger.verbose { "Write response: $response" }

            //TODO: Maybe we can avoid this allocation and write directly to the ByteBuffer...
            val bytes = response.toByteArray()

            workBuffer.clear()
            workBuffer.appendInt(bytes.size)
            workBuffer.appendBytes(bytes)
            outputChannel.writeExactly(workBuffer.forChannelWrite())
            writingAllowed = true
        }

        suspend fun writeRequest(request: ProcessInventoryServerProto.Request) {
            check(writingAllowed)
            writingAllowed = false
            logger.verbose { "Write request: $request" }

            //TODO: Maybe we can avoid this allocation and write directly to the ByteBuffer...
            val bytes = request.toByteArray()

            workBuffer.clear()
            workBuffer.appendInt(bytes.size)
            workBuffer.appendBytes(bytes)
            outputChannel.writeExactly(workBuffer.forChannelWrite())
            writingAllowed = true
        }

        suspend fun writeErrorResponse(response: ProcessInventoryServerProto.Response) {
            if (writingAllowed) {
                writeResponse(response)
            }
        }
    }

    companion object {

        private val sessionIdKey = CoroutineScopeCache.Key<String>("sessionIdKey")

        /**
         * Generates a value for [ProcessInventoryServerProto.DeviceId.Builder.setAdbSessionId],
         * currently it is a hard-coded value as we assume devices serial numbers are unique
         * amongst all running instances of the ADB server the process inventory server
         * deals with.
         */
        private fun AdbSession.processInventorySessionId(): String {
            return cache.getOrPutSynchronized(sessionIdKey) {
                "<adblib-generic-session-id-v1>"
            }
        }
    }
}
