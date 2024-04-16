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

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.adbLogger
import com.android.adblib.getOrPutSynchronized
import com.android.adblib.property
import com.android.adblib.scope
import com.android.adblib.serialNumber
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.JdwpSessionProxyStatus
import com.android.adblib.tools.debugging.processinventory.AdbLibToolsProcessInventoryServerProperties
import com.android.adblib.tools.debugging.processinventory.impl.ProcessInventoryServerConnection.ConnectionForDevice
import com.android.adblib.tools.debugging.processinventory.protos.ProcessInventoryServerProto
import com.android.adblib.tools.debugging.processinventory.server.ProcessInventoryServer
import com.android.adblib.tools.debugging.processinventory.server.ProcessInventoryServerConfiguration
import com.android.adblib.tools.debugging.utils.logIOCompletionErrors
import com.android.adblib.tools.tcpserver.RetryPolicy
import com.android.adblib.tools.tcpserver.TcpServerConnection
import com.android.adblib.utils.createChildScope
import com.android.adblib.utils.runAlongOtherScope
import com.google.protobuf.ByteString
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress

internal class ProcessInventoryServerConnectionImpl(
    session: AdbSession,
    private val config: ProcessInventoryServerConfiguration,
) : ProcessInventoryServerConnection {

    /**
     * The "failover" connection to the [ProcessInventoryServer] server, meaning either we start
     * the server if not running or we connect to an existing one. The "retry" policy ensures
     * that, if the server shutdowns, we try to start a new one ourselves.
     */
    private val serverWithFailOver = TcpServerConnection.createWithFailoverConnection(
        session = session,
        tcpServer = ProcessInventoryServer(session, config),
        port = session.property(AdbLibToolsProcessInventoryServerProperties.LOCAL_PORT_V1),
        connectTimeout = session.property(AdbLibToolsProcessInventoryServerProperties.CONNECT_TIMEOUT),
        retryPolicy = RetryPolicy.fixedDelay(
            session.property(
                AdbLibToolsProcessInventoryServerProperties.START_RETRY_DELAY
            )
        )
    )

    override suspend fun <R> withConnectionForDevice(
        device: ConnectedDevice,
        block: suspend ConnectionForDevice.() -> R
    ): R {
        // Use scope of device so `block` is cancelled when the device scope is cancelled
        return runAlongOtherScope(device.scope) {
            val connectionForDevice =
                device.processInventoryServerConnection(config, serverWithFailOver)
            connectionForDevice.block()
        }
    }

    override fun close() {
        serverWithFailOver.close()
    }
}

private val processInventoryServerConnectionForDeviceKey =
    CoroutineScopeCache.Key<ProcessInventoryServerConnectionForDevice>("processInventoryServerConnectionForDeviceKey")

private fun ConnectedDevice.processInventoryServerConnection(
    config: ProcessInventoryServerConfiguration,
    server: TcpServerConnection
): ProcessInventoryServerConnectionForDevice {
    return this.cache.getOrPutSynchronized(processInventoryServerConnectionForDeviceKey) {
        ProcessInventoryServerConnectionForDevice(server, config, this)
    }
}

/**
 * A TCP client to the [server] connection to [ProcessInventoryServer] for a given
 * [ConnectedDevice], synchronizing process properties updates coming from internal process
 * discovery and from the server.
 */
private class ProcessInventoryServerConnectionForDevice(
    private val server: TcpServerConnection,
    private val config: ProcessInventoryServerConfiguration,
    override val device: ConnectedDevice,
): ConnectionForDevice, AutoCloseable {

    private val session: AdbSession
        get() = device.session

    private val logger = adbLogger(session)

    private val processPropertiesListMutableStateFlow =
        MutableStateFlow<List<JdwpProcessProperties>>(emptyList())

    private val scope = device.scope.createChildScope(isSupervisor = true)

    override val processListStateFlow = processPropertiesListMutableStateFlow.asStateFlow()

    init {
        scope.launch {
            runCatching {
                trackProcessesFromServer()
            }.onFailure { throwable ->
                logger.logIOCompletionErrors(throwable, "Remote process tracking coroutine")
            }
        }
    }

    override fun close() {
        scope.cancel("${this::class.java.simpleName} has been closed")
        processPropertiesListMutableStateFlow.update { emptyList() }
    }

    override suspend fun sendProcessProperties(properties: JdwpProcessProperties) {
        // Note: This will retry until the server is available
        server.withClientSocket { _, socketChannel ->
            logger.debug { "Sending process properties $properties on socket $socketChannel" }
            val protocolChannel = ProcessInventoryServerSocketProtocol(session, socketChannel)
                .forClient(config.clientDescription)
            val response =
                protocolChannel.sendDeviceProcessInfo(
                    device.serialNumber,
                    properties.toJdwpProcessInfoProto()
                )
            logger.debug { "Sent process properties: $response" }
        }
    }

    override suspend fun notifyProcessExit(pid: Int) {
        // Note: This will retry until the server is available
        server.withClientSocket { _, socketChannel ->
            logger.debug { "Sending process termination notification on socket $socketChannel: pid=${pid}" }
            val protocolChannel = ProcessInventoryServerSocketProtocol(session, socketChannel)
                .forClient(config.clientDescription)
            val response = protocolChannel.sendDeviceProcessRemoval(device.serialNumber, pid)
            logger.debug { "Process termination response: $response" }
        }
    }

    /**
     * Collects process info updates from [ProcessInventoryServer], converting the received
     * [ProcessInventoryServerProto.JdwpProcessInfo] updates into a [StateFlow] of
     * [JdwpProcessProperties].
     */
    private suspend fun trackProcessesFromServer() {
        // Note: This is a long-running connection that should remain active as long
        // as the device is connected. If the server becomes unavailable, a new connection
        // is opened automatically (until the device is disconnected)
        server.withClientSocket { newServerInstance, socketChannel ->
            val protocolChannel = ProcessInventoryServerSocketProtocol(session, socketChannel)
                .forClient(config.clientDescription)

            // If a new server has just been started, send it the full list of known
            // processes
            if (newServerInstance) {
                val currentList = processListStateFlow.value
                if (currentList.isNotEmpty()) {
                    logger.debug {
                        "New server has started, sending list of" +
                                " ${currentList.size} know processes to socket $socketChannel"
                    }
                    protocolChannel.sendDeviceProcessInfoList(
                        device.serialNumber,
                        currentList.map {
                            it.toJdwpProcessInfoProto()
                        })
                }
            }

            logger.debug { "Start collecting ${ProcessInventoryServerProto.ProcessUpdates::class.java.simpleName} from socket $socketChannel" }
            protocolChannel.trackDevice(device.serialNumber).mapNotNull {
                if (it.hasTrackDeviceResponsePayload() && it.trackDeviceResponsePayload.hasProcessUpdates()) {
                    it.trackDeviceResponsePayload.processUpdates
                } else {
                    null
                }
            }.collect { updates ->
                val updated = mutableListOf<JdwpProcessProperties>()
                val removed = mutableSetOf<Int>()

                updates.processUpdateList.forEach { update ->
                    if (update.hasProcessUpdated()) {
                        val properties = update.processUpdated.toJdwpProcessProperties()
                        logger.debug { "Process ${properties.pid} has been updated: $properties" }
                        updated.add(properties)
                    } else if (update.hasProcessTerminatedPid()) {
                        val pid = update.processTerminatedPid
                        logger.debug { "Process $pid has exited" }
                        removed.add(pid)
                    }
                }

                // Create a new list (i.e. map) with "updated" processes,
                // excluding the "removed" items
                val newMap = mutableMapOf<Int, JdwpProcessProperties>()
                processPropertiesListMutableStateFlow.value
                    .filter {
                        !removed.contains(it.pid)
                    }.forEach {
                        newMap[it.pid] = it
                    }
                updated.forEach { processProperties ->
                    newMap[processProperties.pid] = processProperties
                }

                newMap.values.sortedBy { it.pid }.also { newList ->
                    logger.debug { "Updating process properties flow to ${newList.size} items" }
                    logger.verbose { "New properties list: $newList" }

                    // Only update if `close` has not been called, since we don't want to overwrite
                    // the `closed` state (i.e. empty list)
                    scope.ensureActive()
                    processPropertiesListMutableStateFlow.value = newList
                }
            }
        }
    }

    /**
     * Converts a [ProcessInventoryServerProto.JdwpProcessInfo] from the inventory server,
     * into a [JdwpProcessProperties] for internal use.
     */
    private fun ProcessInventoryServerProto.JdwpProcessInfo.toJdwpProcessProperties(): JdwpProcessProperties {
        val source = this
        return JdwpProcessProperties(
            pid = source.pid,
            processName = if (source.hasProcessName()) source.processName else null,
            packageName = if (source.hasPackageName()) source.packageName else null,
            userId = if (source.hasUserId()) source.userId else null,
            vmIdentifier = if (source.hasVmIdentifier()) source.vmIdentifier else null,
            abi = if (source.hasAbi()) source.abi else null,
            jvmFlags = if (source.hasJvmFlags()) source.jvmFlags else null,
            isNativeDebuggable = if (source.hasNativeDebuggable()) source.nativeDebuggable else false,
            waitCommandReceived = if (source.hasWaitPacketReceived()) source.waitPacketReceived else false,
            features = if (source.hasFeatures()) source.features.featureList else emptyList(),
            completed = if (source.hasCollectionStatus()) source.collectionStatus.completed else false,
            exception = if (source.hasCollectionStatus() && source.collectionStatus.hasException()) source.collectionStatus.exception.toThrowable() else null,
            isWaitingForDebugger = if (source.hasExternalDebuggerStatus()) source.externalDebuggerStatus.waitingForDebugger else false,
            jdwpSessionProxyStatus = if (source.hasExternalDebuggerStatus()) source.externalDebuggerStatus.toJdwpSessionProxyStatus() else JdwpSessionProxyStatus()
        )
    }

    /**
     * Converts a [JdwpProcessProperties] from a local [JdwpProcessProperties] to a
     * [ProcessInventoryServerProto.JdwpProcessInfo] for sending to the inventory server.
     */
    private fun JdwpProcessProperties.toJdwpProcessInfoProto(): ProcessInventoryServerProto.JdwpProcessInfo {
        val source = this

        val collectionStatus = ProcessInventoryServerProto.JdwpProcessInfo.CollectionStatus
            .newBuilder()
            .also { proto ->
                source.completed.also { proto.completed = it }
                source.exception?.also { proto.exception = it.toExceptionProto() }
            }

        val debuggerStatus = ProcessInventoryServerProto.JdwpProcessInfo.JavaDebuggerStatus
            .newBuilder()
            .also { proto ->
                source.isWaitingForDebugger.also { proto.waitingForDebugger = it }
                source.jdwpSessionProxyStatus.isExternalDebuggerAttached.also {
                    proto.debuggerIsAttached =
                        it
                }
                source.jdwpSessionProxyStatus.socketAddress?.also {
                    proto.socketAddress =
                        it.toInetSocketAddressProto()
                }
            }

        return ProcessInventoryServerProto.JdwpProcessInfo
            .newBuilder()
            .also { proto ->
                source.pid.also { proto.pid = it }
                proto.setCollectionStatus(collectionStatus)
                proto.setExternalDebuggerStatus(debuggerStatus)
                source.processName?.also { proto.processName = it }
                source.packageName?.also { proto.packageName = it }
                source.userId?.also { proto.userId = it }
                source.vmIdentifier?.also { proto.vmIdentifier = it }
                source.abi?.also { proto.abi = it }
                source.jvmFlags?.also { proto.jvmFlags = it }
                @Suppress("DEPRECATION")
                source.isNativeDebuggable.also { proto.nativeDebuggable = it }
                source.waitCommandReceived.also { proto.waitPacketReceived = it }
                source.features.also {
                    if (it.isNotEmpty()) proto.features =
                        it.toFeaturesProto()
                }
            }
            .build()
    }

    private fun List<String>.toFeaturesProto(): ProcessInventoryServerProto.JdwpProcessInfo.Features {
        return ProcessInventoryServerProto.JdwpProcessInfo.Features.newBuilder()
            .addAllFeature(this)
            .build()
    }

    class RemoteException(
        val className: String,
        message: String,
        cause: Throwable?
    ) : Exception(message, cause)

    private fun Throwable.toExceptionProto(): ProcessInventoryServerProto.Exception {
        return enumerateCauses().fold<Throwable, ProcessInventoryServerProto.Exception?>(null) { acc, exception ->
            ProcessInventoryServerProto.Exception.newBuilder().also {
                it.setClassName(exception::class.java.simpleName)
                it.setMessage(exception.message)
                if (acc != null) it.setCause(acc)
            }.build()
        }!!
    }

    private fun Throwable.enumerateCauses(): Sequence<Throwable> = sequence {
        var current: Throwable? = this@enumerateCauses
        val first = current
        while (current != null) {
            yield(current)
            current = current.cause
            if (current === first) {
                break
            }
        }
    }

    private fun ProcessInventoryServerProto.Exception.toThrowable(): Throwable {
        return enumerateCausesProto().fold<ProcessInventoryServerProto.Exception, Throwable?>(
            null
        ) { acc, exception ->
            RemoteException(exception.className, exception.message, acc)
        }!!
    }

    private fun ProcessInventoryServerProto.Exception.enumerateCausesProto() = sequence {
        var current: ProcessInventoryServerProto.Exception? = this@enumerateCausesProto
        while (current != null) {
            yield(current)
            current = if (current.hasCause()) {
                current.cause
            } else {
                null
            }
        }
    }

    /**
     * Converts a protobuf [ProcessInventoryServerProto.InetSocketAddress] to an
     * equivalent [InetSocketAddress].
     *
     * Note: An [InetSocketAddress] is a wrapper around a 4-bytes or a 16-bytes array
     * of bytes. The "hostname" part of it is optional.
     */
    private fun ProcessInventoryServerProto.InetSocketAddress.toInetSocketAddress(): InetSocketAddress {
        val sourceProto = this
        // Note: We need to make sure we don't perform actual name resolution, hence we use the
        // `getByAddress` API.
        val inetAddress =
            InetAddress.getByAddress(sourceProto.hostname, sourceProto.ipAddress.toByteArray())
        return InetSocketAddress(inetAddress, sourceProto.tcpPort)
    }

    /**
     * Converts a protobuf [ProcessInventoryServerProto.InetSocketAddress] to an
     * equivalent [InetSocketAddress]
     *
     * Note: An [InetSocketAddress] is a wrapper around a 4-bytes or 16-bytes array
     * of bytes. The "hostname" part of it is optional.
     */
    private fun InetSocketAddress.toInetSocketAddressProto(): ProcessInventoryServerProto.InetSocketAddress {
        val source = this
        return ProcessInventoryServerProto.InetSocketAddress
            .newBuilder()
            .also { proto ->
                source.hostString?.also { proto.hostname = it }
                source.address?.also { proto.ipAddress = ByteString.copyFrom(it.address) }
                proto.tcpPort = source.port
            }
            .build()
    }

    private fun ProcessInventoryServerProto.JdwpProcessInfo.JavaDebuggerStatus.toJdwpSessionProxyStatus(): JdwpSessionProxyStatus {
        val sourceProto = this
        return JdwpSessionProxyStatus(
            socketAddress = if (sourceProto.hasSocketAddress()) sourceProto.socketAddress.toInetSocketAddress() else null,
            isExternalDebuggerAttached = sourceProto.debuggerIsAttached,
        )
    }
}
