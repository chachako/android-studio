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
package com.android.fakeadbserver.devicecommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.statechangehubs.ClientStateChangeHandlerFactory
import com.android.fakeadbserver.statechangehubs.StateChangeHandlerFactory
import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.Callable

/**
 * track-jdwp tracks the device's Android Client list, sending change messages whenever a client is
 * added/removed, or have its state changed.
 */
class TrackJdwpCommandHandler : DeviceCommandHandler("track-jdwp") {

    override fun invoke(
        server: FakeAdbServer,
        socketScope: CoroutineScope,
        socket: Socket,
        device: DeviceState,
        args: String
    ) {
        val stream: OutputStream = socket.getOutputStream()
        val queue = device.clientChangeHub
            .subscribe(
                object : ClientStateChangeHandlerFactory {
                    override fun createClientListChangedHandler(): Callable<StateChangeHandlerFactory.HandlerResult> {
                        return Callable {
                            try {
                                sendClientList(device, stream)
                                return@Callable StateChangeHandlerFactory.HandlerResult(
                                    true
                                )
                            } catch (ignored: IOException) {
                                return@Callable StateChangeHandlerFactory.HandlerResult(
                                    false
                                )
                            }
                        }
                    }

                    override fun createAppProcessListChangedHandler(): Callable<StateChangeHandlerFactory.HandlerResult> {
                        return Callable { StateChangeHandlerFactory.HandlerResult(true) }
                    }

                    override fun createLogcatMessageAdditionHandler(
                        message: String
                    ): Callable<StateChangeHandlerFactory.HandlerResult> {
                        return Callable { StateChangeHandlerFactory.HandlerResult(true) }
                    }
                })
            ?: return  // Server has shutdown before we are able to start listening to the queue.
        try {
            writeOkay(stream) // Send ok first.
            sendClientList(device, stream) // Then send the initial client list.
            while (true) {
                if (!queue.take().call().mShouldContinue) {
                    break
                }
            }
        } catch (ignored: Exception) {
        } finally {
            device.clientChangeHub.unsubscribe(queue)
        }
        return
    }

    companion object {

        private fun sendClientList(device: DeviceState, stream: OutputStream) {
            val clientListString = device.clientListString
            write4ByteHexIntString(stream, clientListString.length)
            writeString(stream, clientListString)
        }
    }
}
