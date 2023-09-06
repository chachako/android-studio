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
import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutionException

/**
 * host:devices is a one-shot command to list devices and their states that are presently connected
 * to the server.
 */
class ListDevicesCommandHandler: HostCommandHandler() {

    override fun handles(command: String): Boolean {
        return command == COMMAND || command == LONG_COMMAND
    }

    override fun invoke(
        fakeAdbServer: FakeAdbServer,
        socketScope: CoroutineScope,
        responseSocket: Socket,
        deviceSelector: DeviceStateSelector,
        command: String,
        args: String
    ): Boolean {
        val longFormat = command == LONG_COMMAND

        val stream = responseSocket.getOutputStream()
        val deviceListString = formatDeviceList(
            fakeAdbServer.deviceListCopy.get(),
            longFormat
        )
        // Send ok first, then a prefixed length string with the list of devices
        writeOkay(stream)
        write4ByteHexIntString(stream, deviceListString.length)
        stream.write(deviceListString.toByteArray(StandardCharsets.US_ASCII))
        return false /* close connection */
    }

    companion object {

        const val COMMAND = "devices"
        const val LONG_COMMAND = "devices-l"

        fun formatDeviceList(deviceList: List<DeviceState>, longFormat: Boolean): String {
            val builder = StringBuilder()
            for (deviceState in deviceList) {
                builder.append(deviceState.deviceId)
                builder.append("\t")
                builder.append(deviceState.deviceStatus.state)
                if (longFormat) {
                    if (deviceState.deviceStatus === DeviceState.DeviceStatus.ONLINE) {
                        builder.append(" ")
                        builder.append("product:")
                        builder.append(deviceState.manufacturer)
                        builder.append(" ")
                        builder.append("model:")
                        builder.append(deviceState.model)
                        builder.append(" ")
                        builder.append("device:")
                        builder.append(deviceState.buildVersionRelease)
                    }
                    builder.append(" ")
                    builder.append("transport_id:")
                    builder.append(deviceState.transportId)
                }
                builder.append("\n")
            }

            // Remove trailing '\n' to match adb server behavior
            if (!deviceList.isEmpty()) {
                builder.deleteCharAt(builder.length - 1)
            }
            return builder.toString()
        }
    }
}
