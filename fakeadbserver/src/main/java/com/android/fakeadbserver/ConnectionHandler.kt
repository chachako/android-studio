/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.fakeadbserver

import com.android.fakeadbserver.DeviceState.HostConnectionType
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.HostCommandHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.channels.SocketChannel
import java.util.Locale
import java.util.Optional
import java.util.concurrent.ExecutionException

/**
 * Handle a connection according to adb SERVICE.TXT and protocol.txt. In a nutshell, a smart
 * connection is able to request HOST (adb server) services or LOCAL (adbd running on a device)
 * services. The smart part is the "transport" part which allows to "route" service request to a
 * device after the connection has been established with the HOST.
 */
// TODO: Rename this class SmartConnection (not doing it now to help review process but should
// be in next CL).
internal class ConnectionHandler(private val mServer: FakeAdbServer, socket: SocketChannel) :
    Runnable {

    private val mSmartSocket: SmartSocket

    /**
     * On demand access to the [DeviceState] associated to the current request being processed.
     * Most [HostCommandHandler] and [DeviceCommandHandler] implementation require either a single
     * device or no device to be specified in the request device "header". There are a few
     * exceptions where the device is optional, so we need to account for that.
     */
    private var mTargetDeviceSelector = DeviceStateSelector { DeviceStateSelector.DeviceResult.None }

    private var mKeepRunning: Boolean

    init {
        mSmartSocket = SmartSocket(socket)
        mKeepRunning = true
    }

    /**
     * Repeatedly read host service requests and serve them until a local request is received. After
     * service the local request, the connection is closed. Note that a host request can also
     * request the connection to end.
     */
    override fun run() {
        var request = ServiceRequest("No request processed")
        val socketScope = CoroutineScope(SupervisorJob())
        try {
            mSmartSocket.use {
                while (mKeepRunning) {
                    request = mSmartSocket.readServiceRequest()
                    if (request.peekToken().startsWith("host")) {
                        handleHostService(request, socketScope)
                    } else {
                        handleDeviceService(request, socketScope)
                    }
                }
            }
        } catch (e: Exception) {
            val pw = PrintWriter(StringWriter())
            pw.print("Unable to process '${request.original()}'\n")
            e.printStackTrace(pw)
        } finally {
            socketScope.cancel("Socket has been closed")
        }
    }

    private fun handleHostService(request: ServiceRequest, socketScope: CoroutineScope) {
        when (request.nextToken()) {
            "host" -> {
                //  'host:command' can also be used to run a HOST command target a device. In that
                // case it should be interpreted as 'any single device or emulator connected
                // to/running
                // on the HOST'.
                //  e.g.: Port forwarding is a HOST command which targets a device.
                mTargetDeviceSelector =
                    DeviceStateSelector.forCollection(
                        deviceListProvider = { findAnyDevice() },
                        errorReporter = { }
                    )
            }

            "host-usb" -> {
                mTargetDeviceSelector =
                    DeviceStateSelector.forCollection(
                        deviceListProvider = { findDevicesWithProtocol(HostConnectionType.USB) },
                        errorReporter = { reportDevicesErrorAndStop(it.size) }
                    )
            }

            "host-local" -> {
                mTargetDeviceSelector =
                    DeviceStateSelector.forCollection(
                        deviceListProvider = { findDevicesWithProtocol(HostConnectionType.LOCAL) },
                        errorReporter = { reportDevicesErrorAndStop(it.size) }
                    )
            }

            "host-serial" -> {
                val serial = request.nextToken()
                mTargetDeviceSelector =
                    DeviceStateSelector.forOptional(
                        deviceProvider = { findDeviceWithSerial(serial) },
                        errorReporter = {
                            reportErrorAndStop("No device with serial: '$serial' is connected.")
                        }
                    )
            }

            "host-transport-id" -> {
                val transportId = request.nextToken()
                mTargetDeviceSelector =
                    DeviceStateSelector.forOptional(
                        deviceProvider = { findDeviceWithTransportId(transportId) },
                        errorReporter = {
                            reportErrorAndStop("No device with transport id: '$transportId' is connected.")
                        }
                    )
            }

            else -> {
                mTargetDeviceSelector =
                    DeviceStateSelector.forError(
                        errorReporter = {
                            val err = String.format(
                                Locale.US,
                                "Command not handled '%s' '%s'",
                                request.currToken(),
                                request.original()
                            )
                            mSmartSocket.sendFailWithReason(err)
                        }
                    )
            }
        }
        dispatchToHostHandlers(request, socketScope)
    }

    private fun reportDevicesErrorAndStop(numDevices: Int) {
        val msg: String = if (numDevices == 0) {
            "No devices available."
        } else {
            String.format(
                Locale.US,
                "More than one device found (%d). Please specify which.",
                numDevices
            )
        }
        reportErrorAndStop(msg)
    }

    private fun reportErrorAndStop(msg: String) {
        mKeepRunning = false
        mSmartSocket.sendFailWithReason(msg)
    }

    // IN SERVICE.TXT these are called "LOCAL".
    private fun handleDeviceService(request: ServiceRequest, socketScope: CoroutineScope) {
        // Regardless of the outcome, this will be the last request this connection handles.
        mKeepRunning = false
        when(val targetDevice = mTargetDeviceSelector.invoke(reportError = true)) {
            DeviceStateSelector.DeviceResult.Ambiguous,
            DeviceStateSelector.DeviceResult.None -> {
                // Nothing to do, error has been reported already
            }
            is DeviceStateSelector.DeviceResult.One -> {
                targetDevice.deviceState.trackCommand(request.original(), socketScope, mSmartSocket.socket) {
                    val serviceName = request.nextToken()
                    val command = request.remaining()
                    for (handler in mServer.handlers) {
                        val accepted = handler.accept(
                            mServer,
                            socketScope,
                            mSmartSocket.socket,
                            targetDevice.deviceState,
                            serviceName,
                            command
                        )
                        if (accepted) {
                            return@handleDeviceService
                        }
                    }
                    mSmartSocket.sendFailWithReason("Unknown request $serviceName-$command")
                }
            }
        }
    }

    private fun dispatchToHostHandlers(request: ServiceRequest, socketScope: CoroutineScope) {
        // Intercepting the host:transport* service request because it has the special side-effect
        // of changing the target device.
        // TODO: This should be in a host TransportCommandHandler! Following in next CL.
        if (request.peekToken().startsWith("transport")) {
            when (request.nextToken()) {
                "transport" -> {
                    val serial = request.nextToken()
                    mTargetDeviceSelector =
                        DeviceStateSelector.forOptional(
                            deviceProvider = { findDeviceWithSerial(serial) },
                            errorReporter = {
                                reportErrorAndStop("No device with serial: '$serial' is connected.")
                            },
                            successBlock = { mSmartSocket.sendOkay() })
                }

                "transport-id" -> {
                    val transportId = request.nextToken()
                    mTargetDeviceSelector =
                        DeviceStateSelector.forOptional(
                            deviceProvider = { findDeviceWithTransportId(transportId) },
                            errorReporter = {
                                reportErrorAndStop("No device with transport id: '$transportId' is connected.")
                            },
                            successBlock = { mSmartSocket.sendOkay() })
                }

                "transport-usb" -> {
                    mTargetDeviceSelector =
                        DeviceStateSelector.forCollection(
                            deviceListProvider = { findDevicesWithProtocol(HostConnectionType.USB) },
                            errorReporter = { reportDevicesErrorAndStop(it.size) },
                            successBlock = { mSmartSocket.sendOkay() })
                }

                "transport-local" -> {
                    mTargetDeviceSelector =
                        DeviceStateSelector.forCollection(
                            deviceListProvider = { findDevicesWithProtocol(HostConnectionType.LOCAL) },
                            errorReporter = { reportDevicesErrorAndStop(it.size) },
                            successBlock = { mSmartSocket.sendOkay() })
                }

                "transport-any" -> {
                    mTargetDeviceSelector =
                        DeviceStateSelector.forCollection(
                            deviceListProvider = { findAnyDevice() },
                            errorReporter = { reportDevicesErrorAndStop(it.size) },
                            successBlock = { mSmartSocket.sendOkay() })
                }

                else -> {
                    mTargetDeviceSelector =
                        DeviceStateSelector.forError(
                            errorReporter = {
                                val err = String.format(
                                    Locale.US,
                                    "Unsupported request '%s'",
                                    request.original()
                                )
                                mSmartSocket.sendFailWithReason(err)
                            }
                        )
                }
            }
        } else if (request.peekToken() == "tport") {
            fun sendTransportId(deviceState: DeviceState): DeviceState {
                // Send 'OKAY' then transport ID as 64-bit little endian value
                mSmartSocket.sendOkay()
                mSmartSocket.sendTransportId(deviceState.transportId.toLong())
                return deviceState
            }

            request.nextToken() // skip 'tport'
            when (request.nextToken()) {
                "serial" -> {
                    val serial = request.nextToken()
                    mTargetDeviceSelector =
                        DeviceStateSelector.forOptional(
                            deviceProvider = { findDeviceWithSerial(serial) },
                            errorReporter = {
                                reportErrorAndStop("No device with serial: '$serial' is connected.")
                            },
                            successBlock = { sendTransportId(it) })
                }

                "usb" -> {
                    mTargetDeviceSelector =
                        DeviceStateSelector.forCollection(
                            deviceListProvider = { findDevicesWithProtocol(HostConnectionType.USB) },
                            errorReporter = { reportDevicesErrorAndStop(it.size) },
                            successBlock = { sendTransportId(it) })
                }

                "local" -> {
                    mTargetDeviceSelector =
                        DeviceStateSelector.forCollection(
                            deviceListProvider = { findDevicesWithProtocol(HostConnectionType.LOCAL) },
                            errorReporter = { reportDevicesErrorAndStop(it.size) },
                            successBlock = { sendTransportId(it) })
                }

                "any" -> {
                    mTargetDeviceSelector =
                        DeviceStateSelector.forCollection(
                            deviceListProvider = { findAnyDevice() },
                            errorReporter = { reportDevicesErrorAndStop(it.size) },
                            successBlock = { sendTransportId(it) })
                }

                else -> {
                    mTargetDeviceSelector =
                        DeviceStateSelector.forError {
                            val err = String.format(
                                Locale.US, "Unsupported request '%s'", request.original()
                            )
                            mSmartSocket.sendFailWithReason(err)
                        }
                }
            }
        }

        val hostCommand = request.nextToken()
        if (hostCommand.isEmpty()) {
            // Host command was a "switch to transport", so we need to fetch the device target
            // device
            val device = mTargetDeviceSelector.invoke(reportError = true)
            mTargetDeviceSelector = DeviceStateSelector { device }
            return
        }
        val handler = mServer.getHostCommandHandler(hostCommand)
        if (handler == null) {
            val err = String.format(
                Locale.US,
                "Unimplemented host command received: '%s'",
                hostCommand
            )
            mSmartSocket.sendFailWithReason(err)
            return
        }
        mKeepRunning = handler.invoke(
            mServer, socketScope, mSmartSocket.socket, mTargetDeviceSelector, hostCommand, request.remaining()
        )
    }

    private fun findAnyDevice(): List<DeviceState> {
        return findDevicesWithProtocols(listOf(HostConnectionType.LOCAL, HostConnectionType.USB))
    }

    private fun findDevicesWithProtocol(
        type: HostConnectionType
    ): List<DeviceState> {
        return findDevicesWithProtocols(listOf(type))
    }

    private fun findDevicesWithProtocols(
        types: List<HostConnectionType>
    ): List<DeviceState> {
        val candidates: List<DeviceState>
        val devices: MutableList<DeviceState> = ArrayList()
        try {
            candidates = mServer.deviceListCopy.get()
        } catch (ignored: ExecutionException) {
            mSmartSocket.sendFailWithReason(
                "Internal server failure while retrieving device list."
            )
            mKeepRunning = false
            return ArrayList()
        } catch (ignored: InterruptedException) {
            mSmartSocket.sendFailWithReason(
                "Internal server failure while retrieving device list."
            )
            mKeepRunning = false
            return ArrayList()
        }
        for (device in candidates) {
            if (types.contains(device.hostConnectionType)) {
                devices.add(device)
            }
        }
        return devices
    }

    private fun findDeviceWithSerial(serial: String): Optional<DeviceState> {
        return try {
            val devices = mServer.deviceListCopy.get()
            devices.stream()
                .filter { streamDevice: DeviceState -> serial == streamDevice.deviceId }
                .findAny()
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        } catch (e: ExecutionException) {
            throw IllegalStateException(e)
        }
    }

    private fun findDeviceWithTransportId(transportId: String): Optional<DeviceState> {
        return try {
            val devices = mServer.deviceListCopy.get()
            devices.stream()
                .filter { streamDevice: DeviceState -> transportId == streamDevice.transportId.toString() }
                .findAny()
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        } catch (e: ExecutionException) {
            throw IllegalStateException(e)
        }
    }
}

