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
package com.android.fakeadbserver.hostcommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.DeviceStateSelector
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.Guava.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.net.Socket

/**
 * `<device-prefix>:wait-for-<transport>-<state>` forces the host to wait for device <transport>
 * to show up in state <state>.
 */
class WaitForCommandHandler: HostCommandHandler() {

    override fun handles(command: String): Boolean {
        return command.startsWith("wait-for")
    }

    override fun invoke(
        fakeAdbServer: FakeAdbServer,
        socketScope: CoroutineScope,
        responseSocket: Socket,
        deviceSelector: DeviceStateSelector,
        command: String,
        args: String
    ): Boolean {
        // Write "OKAY" as the "connect" status
        writeOkay(responseSocket.getOutputStream())

        // Supported formats: "wait-for-TRANSPORT-STATE" or "wait-for-STATE"
        val splits = command.split("-")
        val state: String
        val transport: String
        when (splits.size) {
            4 -> {
                // wait-for-TRANSPORT-STATE
                // where TRANSPORT: "local" | "usb" | "any"
                //           STATE: "device" | "recovery" | "rescue" | "sideload" | "bootloader" | "any" | "disconnect"
                transport = splits[2]
                state = splits[3]
            }

            3 -> {
                // wait-for-STATE
                // where STATE: "device" | "recovery" | "rescue" | "sideload" | "bootloader" | "any" | "disconnect"
                // Default transport value is "any"
                transport = "any"
                state = splits[2]
            }

            else -> {
                writeFailResponse(responseSocket.getOutputStream(), "error: bad wait-for format")
                return false /* don't keep running */
            }
        }
        waitForImpl(fakeAdbServer, socketScope, responseSocket, deviceSelector, state, transport)
        return false /* don't keep running */
    }

    private fun waitForImpl(
        fakeAdbServer: FakeAdbServer,
        socketScope: CoroutineScope,
        responseSocket: Socket,
        device: DeviceStateSelector,
        state: String,
        transport: String
    ) {
        runBlocking {
            socketScope.async {
                when(state) {
                    "disconnect" -> {
                        // "disconnect" is special in the sense we never report errors wrt to
                        // the device selector, because we have to assume the device may already
                        // have disconnected (or was never connected).
                        when (val currentDevice = device.invoke(reportError = false)) {
                            DeviceStateSelector.DeviceResult.Ambiguous -> {
                                // We found more than one matching device: the (somewhat odd)
                                // behavior of the `wait-for` service is to assume "the" device
                                // is disconnected.
                                writeOkay(responseSocket.getOutputStream())
                            }

                            DeviceStateSelector.DeviceResult.None -> {
                                // We found no matching device: assume it is disconnected
                                writeOkay(responseSocket.getOutputStream())
                            }

                            is DeviceStateSelector.DeviceResult.One -> {
                                // We found a single matching device: wait until it is disconnected
                                fakeAdbServer.deviceStateFlow().first { devices ->
                                    devices.all { deviceState ->
                                        currentDevice.deviceState.deviceId != deviceState.deviceId
                                    }
                                }
                                writeOkay(responseSocket.getOutputStream())
                            }
                        }
                    }

                    else -> {
                        // For any other state than "disconnect", we wait until there is a
                        // device matching the device selector.
                        fakeAdbServer.deviceStateFlow().first { devices ->
                            when (val currentDevice = device.invoke(reportError = false)) {
                                DeviceStateSelector.DeviceResult.Ambiguous -> {
                                    // Device is ambiguous, bail out with error
                                    device.invoke(reportError = true)
                                    true
                                }

                                DeviceStateSelector.DeviceResult.None -> {
                                    // Device is not present yet, wait
                                    false
                                }

                                is DeviceStateSelector.DeviceResult.One -> {
                                    devices.any { deviceState ->
                                        currentDevice.deviceState.deviceId == deviceState.deviceId &&
                                                matchesState(deviceState, state) &&
                                                matchesTransport(deviceState, transport)
                                    }.also { found ->
                                        if (found) {
                                            writeOkay(responseSocket.getOutputStream())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }.await()
        }
    }

    private fun matchesTransport(deviceState: DeviceState, transport: String): Boolean {
        return when (transport) {
            "usb" -> deviceState.hostConnectionType == DeviceState.HostConnectionType.USB
            "local" -> deviceState.hostConnectionType == DeviceState.HostConnectionType.LOCAL
            "any" -> true
            else -> false
        }
    }

    private fun matchesState(deviceState: DeviceState, state: String): Boolean {
        return deviceState.deviceStatus.state == state
    }

    private fun FakeAdbServer.deviceStateFlow() = flow {
        while (currentCoroutineContext().isActive) {
            val devices = deviceListCopy.await()
            emit(devices)
            delay(20)
        }
    }
}
