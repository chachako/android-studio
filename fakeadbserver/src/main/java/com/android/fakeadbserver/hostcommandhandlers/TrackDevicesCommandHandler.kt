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
import com.android.fakeadbserver.statechangehubs.DeviceStateChangeHandlerFactory
import com.android.fakeadbserver.statechangehubs.StateChangeHandlerFactory
import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

/**
 * host:track-devices is a persistent connection that tracks device connection/disconnections, as
 * well as device state changes (such as coming online, going offline, etc...). Every time an event
 * occurs, the list of connected devices and their states are sent.
 */
class TrackDevicesCommandHandler: HostCommandHandler() {

    override fun handles(command: String): Boolean {
        return command == COMMAND || command == LONG_COMMAND || command == PROTO_BINARY || command == PROTO_TEXT
    }

    private fun sendDeviceList(
        server: FakeAdbServer,
        responseSocket: Socket,
        format: ListDevicesCommandHandler.DeviceListFormat
    ): Callable<StateChangeHandlerFactory.HandlerResult> {
        return Callable {
            try {
                val stream = responseSocket.getOutputStream()
                val deviceList =
                    ListDevicesCommandHandler.formatDeviceList(
                        server.deviceListCopy.get(),
                        format
                    )
                write4ByteHexIntString(stream, deviceList.remaining())
                stream.write(deviceList.array())
                stream.flush()
                return@Callable StateChangeHandlerFactory.HandlerResult(true)
            } catch (e: InterruptedException) {
                return@Callable StateChangeHandlerFactory.HandlerResult(false)
            } catch (e: ExecutionException) {
                return@Callable StateChangeHandlerFactory.HandlerResult(false)
            } catch (e: IOException) {
                return@Callable StateChangeHandlerFactory.HandlerResult(false)
            }
        }
    }

    override fun invoke(
        fakeAdbServer: FakeAdbServer,
        socketScope: CoroutineScope,
        responseSocket: Socket,
        deviceSelector: DeviceStateSelector,
        command: String,
        args: String
    ): Boolean {
        val queue = fakeAdbServer
            .deviceChangeHub
            .subscribe(
                object : DeviceStateChangeHandlerFactory {
                    override fun createDeviceListChangedHandler(
                        deviceList: Collection<DeviceState?>
                    ): Callable<StateChangeHandlerFactory.HandlerResult> {
                        return sendDeviceList(fakeAdbServer, responseSocket, commandToDeviceListFormat(command))
                    }

                    override fun createDeviceStateChangedHandler(
                        device: DeviceState,
                        status: DeviceState.DeviceStatus
                    ): Callable<StateChangeHandlerFactory.HandlerResult> {
                        return sendDeviceList(fakeAdbServer, responseSocket, commandToDeviceListFormat(command))
                    }
                })
            ?: return false // Server has shutdown before we are able to start listening to the queue.
        try {
            writeOkay(responseSocket.getOutputStream()) // Send ok first.

            // Then send over the full list of devices before going into monitoring mode.
            sendDeviceList(fakeAdbServer, responseSocket, commandToDeviceListFormat(command)).call()
            while (true) {
                try {
                    // Grab a command from the queue (take()), and execute the command (get(), as
                    // defined above in the DeviceStateChangeHandlerFactory) as-is in the current
                    // thread so that we can send the message in the opened connection (which only
                    // exists in the current thread).
                    if (!queue.take().call().mShouldContinue) {
                        break
                    }
                } catch (ignored: InterruptedException) {
                    // Most likely server going into shutdown, so quit out of the loop.
                    break
                }
            }
        } catch (ignored: Exception) {
        } finally {
            fakeAdbServer.deviceChangeHub.unsubscribe(queue)
        }
        return false // The only we can get here is if the connection/server was terminated.
    }


    companion object {

        const val COMMAND = "track-devices"
        const val LONG_COMMAND = "track-devices-l"
        const val PROTO_TEXT = "track-devices-proto-text"
        const val PROTO_BINARY = "track-devices-proto-binary"

        fun commandToDeviceListFormat(command: String) : ListDevicesCommandHandler.DeviceListFormat {
            return when(command) {
                COMMAND -> ListDevicesCommandHandler.DeviceListFormat.COMMAND
                LONG_COMMAND -> ListDevicesCommandHandler.DeviceListFormat.LONG_COMMAND
                PROTO_TEXT -> ListDevicesCommandHandler.DeviceListFormat.PROTO_TEXT
                PROTO_BINARY -> ListDevicesCommandHandler.DeviceListFormat.PROTO_BINARY
                else -> ListDevicesCommandHandler.DeviceListFormat.LONG_COMMAND
            }
        }
    }
}
