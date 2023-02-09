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

import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ProfileableProcessState
import com.android.fakeadbserver.statechangehubs.ClientStateChangeHandlerFactory
import com.android.fakeadbserver.statechangehubs.StateChangeHandlerFactory
import com.android.server.adb.protos.AppProcessesProto
import com.android.server.adb.protos.AppProcessesProto.ProcessEntry
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.Callable

/**
 * Implementation of the `track-app` command, tracking the device's process list
 * (both [ClientState] and [ProfileableProcessState]), sending change messages
 * whenever a process is added/removed.
 */
class TrackAppCommandHandler : DeviceCommandHandler("track-app") {

    override fun invoke(
        server: FakeAdbServer,
        socket: Socket,
        device: DeviceState,
        args: String
    ) {
        val stream = socket.getOutputStream()

        val queue = device.clientChangeHub
            .subscribe(
                object : ClientStateChangeHandlerFactory {
                    override fun createClientListChangedHandler(): Callable<StateChangeHandlerFactory.HandlerResult> {
                        return Callable {
                            // No need to call "sendAppProcessList", as a "createClientListChanged"
                            // event always implies an associated "createAppProcessListChanged"
                            // event
                            StateChangeHandlerFactory.HandlerResult(true)
                        }
                    }

                    override fun createAppProcessListChangedHandler(): Callable<StateChangeHandlerFactory.HandlerResult> {
                        return Callable {
                            sendAppProcessList(device, stream)
                            StateChangeHandlerFactory.HandlerResult(true)
                        }
                    }

                    override fun createLogcatMessageAdditionHandler(
                        message: String
                    ): Callable<StateChangeHandlerFactory.HandlerResult> {
                        return Callable { StateChangeHandlerFactory.HandlerResult(true) }
                    }
                })
            ?: run {
                // Server has shutdown before we are able to start listening to the queue.
                return
            }

        try {
            writeOkay(stream) // Send ok first.
            sendAppProcessList(device, stream) // Then send the initial client list.
            while (true) {
                if (!queue.take().call().mShouldContinue) {
                    break
                }
            }
        } finally {
            device.clientChangeHub.unsubscribe(queue)
        }
    }

    private fun sendAppProcessList(device: DeviceState, stream: OutputStream) {
        val appProcesses = buildProtoBuf(device)
        val bytes = serializeToByteArray(appProcesses)
        write4ByteHexIntString(stream, bytes.size)
        stream.write(bytes)
    }

    private fun buildProtoBuf(device: DeviceState): AppProcessesProto.AppProcesses {
        val builder = AppProcessesProto.AppProcesses.newBuilder()
        val processStates = device.copyOfProcessStates()
        processStates.forEach { processState ->
            val entry = ProcessEntry.newBuilder()
            entry.pid = processState.pid.toLong()
            entry.debuggable = processState.debuggable
            entry.profileable = processState.profileable
            entry.architecture = processState.architecture
            builder.addProcess(entry.build())

        }
        return builder.build()
    }

    private fun serializeToByteArray(appProcesses: AppProcessesProto.AppProcesses): ByteArray {
        val output = ByteArrayOutputStream()
        appProcesses.writeTo(output)
        output.close()
        return output.toByteArray()
    }
}
