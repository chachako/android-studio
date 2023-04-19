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
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.channels.SocketChannel
import java.util.Arrays
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
    private var mTargetDevice: DeviceState? = null
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
        try {
            mSmartSocket.use {
                while (mKeepRunning) {
                    request = mSmartSocket.readServiceRequest()
                    if (request.peekToken().startsWith("host")) {
                        handleHostService(request)
                    } else {
                        handleDeviceService(request)
                    }
                }
            }
        } catch (e: Exception) {
            val pw = PrintWriter(StringWriter())
            pw.print("Unable to process '${request.original()}'\n")
            e.printStackTrace(pw)
            mSmartSocket.sendFailWithReason("Exception occurred when processing request. $pw")
        }
    }

    private fun handleHostService(request: ServiceRequest) {
        when (request.nextToken()) {
            "host" -> {
                //  'host:command' can also be used to run a HOST command target a device. In that
                // case it should be interpreted as 'any single device or emulator connected
                // to/running
                // on the HOST'.
                //  e.g.: Port forwarding is a HOST command which targets a device.
                val devices = findAnyDevice()
                if (devices.size == 1) {
                    mTargetDevice = devices[0]
                }
            }

            "host-usb" -> {
                val devicesUSB = findDevicesWithProtocol(HostConnectionType.USB)
                if (devicesUSB.size != 1) {
                    reportDevicesErrorAndStop(devicesUSB.size)
                    return
                }
                mTargetDevice = devicesUSB[0]
            }

            "host-local" -> {
                val emulators = findDevicesWithProtocol(HostConnectionType.LOCAL)
                if (emulators.size != 1) {
                    reportDevicesErrorAndStop(emulators.size)
                    return
                }
                mTargetDevice = emulators[0]
            }

            "host-serial" -> {
                val serial = request.nextToken()
                val device = findDeviceWithSerial(serial)
                if (device.isEmpty) {
                    reportErrorAndStop("No device with serial: '$serial' is connected.")
                    return
                }
                mTargetDevice = device.get()
            }

            else -> {
                val err = String.format(
                    Locale.US,
                    "Command not handled '%s' '%s'",
                    request.currToken(),
                    request.original()
                )
                mSmartSocket.sendFailWithReason(err)
                return
            }
        }
        dispatchToHostHandlers(request)
    }

    private fun reportDevicesErrorAndStop(numDevices: Int) {
        val msg: String
        msg = if (numDevices == 0) {
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
    private fun handleDeviceService(request: ServiceRequest) {
        // Regardless of the outcome, this will be the last request this connection handles.
        mKeepRunning = false
        if (mTargetDevice == null) {
            mSmartSocket.sendFailWithReason("No device available to honor LOCAL service request")
            return
        }
        val serviceName = request.nextToken()
        val command = request.remaining()
        for (handler in mServer.handlers) {
            val accepted = handler.accept(
                mServer, mSmartSocket.socket, mTargetDevice!!, serviceName, command
            )
            if (accepted) {
                return
            }
        }
        mSmartSocket.sendFailWithReason("Unknown request $serviceName-$command")
    }

    private fun dispatchToHostHandlers(request: ServiceRequest) {
        // Intercepting the host:transport* service request because it has the special side-effect
        // of changing the target device.
        // TODO: This should be in a host TransportCommandHandler! Following in next CL.
        if (request.peekToken().startsWith("transport")) {
            when (request.nextToken()) {
                "transport" -> {
                    val serial = request.nextToken()
                    val device = findDeviceWithSerial(serial)
                    if (device.isEmpty) {
                        reportErrorAndStop("No device with serial: '$serial' is connected.")
                        return
                    }
                    mTargetDevice = device.get()
                }

                "transport-usb" -> {
                    val devicesUSB = findDevicesWithProtocol(HostConnectionType.USB)
                    if (devicesUSB.size != 1) {
                        reportDevicesErrorAndStop(devicesUSB.size)
                        return
                    }
                    mTargetDevice = devicesUSB[0]
                }

                "transport-local" -> {
                    val emulators = findDevicesWithProtocol(HostConnectionType.LOCAL)
                    if (emulators.size != 1) {
                        reportDevicesErrorAndStop(emulators.size)
                        return
                    }
                    mTargetDevice = emulators[0]
                }

                "transport-any" -> {
                    val allDevices = findAnyDevice()
                    if (allDevices.size < 1) {
                        reportDevicesErrorAndStop(allDevices.size)
                        return
                    }
                    mTargetDevice = allDevices[0]
                }

                else -> {
                    val err = String.format(
                        Locale.US, "Unsupported request '%s'", request.original()
                    )
                    mSmartSocket.sendFailWithReason(err)
                }
            }
            mSmartSocket.sendOkay()
            return
        }
        val handler = mServer.getHostCommandHandler(request.nextToken())
        if (handler == null) {
            val err = String.format(
                Locale.US,
                "Unimplemented host command received: '%s'",
                request.currToken()
            )
            mSmartSocket.sendFailWithReason(err)
            return
        }
        mKeepRunning = handler.invoke(
            mServer, mSmartSocket.socket, mTargetDevice, request.remaining()
        )
    }

    private fun findAnyDevice(): List<DeviceState> {
        return findDevicesWithProtocols(
            Arrays.asList(
                HostConnectionType.LOCAL, HostConnectionType.USB
            )
        )
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
}
