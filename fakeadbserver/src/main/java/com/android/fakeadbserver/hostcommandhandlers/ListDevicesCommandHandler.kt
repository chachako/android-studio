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
import com.android.server.adb.protos.DevicesProto
import kotlinx.coroutines.CoroutineScope
import java.net.Socket
import java.nio.ByteBuffer

/**
 * host:devices is a one-shot command to list devices and their states that are presently connected
 * to the server.
 */
class ListDevicesCommandHandler: HostCommandHandler() {


    enum class DeviceListFormat {COMMAND, LONG_COMMAND, PROTO_TEXT, PROTO_BINARY}

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

        val stream = responseSocket.getOutputStream()
        val deviceListString = formatDeviceList(fakeAdbServer.deviceListCopy.get(), commandToDeviceListFormat(command))
        // Send ok first, then a prefixed length string with the list of devices
        writeOkay(stream)
        write4ByteHexIntString(stream, deviceListString.remaining())
        stream.write(deviceListString.array())
        return false /* close connection */
    }

    companion object {

        const val COMMAND = "devices"
        const val LONG_COMMAND = "devices-l"

        fun commandToDeviceListFormat(command: String) : DeviceListFormat {
            return when(command) {
                COMMAND -> DeviceListFormat.COMMAND
                LONG_COMMAND -> DeviceListFormat.LONG_COMMAND
                else -> DeviceListFormat.LONG_COMMAND
            }
        }

        fun formatDeviceList(deviceList: List<DeviceState>, format: DeviceListFormat): ByteBuffer {
                return when(format) {
                    DeviceListFormat.COMMAND -> genTextOutput(deviceList, false)
                    DeviceListFormat.LONG_COMMAND -> genTextOutput(deviceList, true)
                    DeviceListFormat.PROTO_BINARY -> genProtobufOutput(deviceList, false)
                    DeviceListFormat.PROTO_TEXT -> genProtobufOutput(deviceList, true)
                }
        }

        fun genTextOutput(deviceList: List<DeviceState>, longFormat: Boolean) : ByteBuffer {
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
            return ByteBuffer.wrap(builder.toString().toByteArray(Charsets.US_ASCII))
        }

        private fun deviceStatusToProto(state: DeviceState.DeviceStatus): DevicesProto.ConnectionState? {
           return when(state) {
               DeviceState.DeviceStatus.ANY -> DevicesProto.ConnectionState.ANY
               DeviceState.DeviceStatus.CONNECTING -> DevicesProto.ConnectionState.CONNECTING
               DeviceState.DeviceStatus.AUTHORIZING -> DevicesProto.ConnectionState.AUTHORIZING
               DeviceState.DeviceStatus.UNAUTHORIZED -> DevicesProto.ConnectionState.UNAUTHORIZED
               DeviceState.DeviceStatus.NOPERMISSION -> DevicesProto.ConnectionState.NOPERMISSION
               DeviceState.DeviceStatus.DETACHED -> DevicesProto.ConnectionState.DETACHED
               DeviceState.DeviceStatus.OFFLINE -> DevicesProto.ConnectionState.OFFLINE
               DeviceState.DeviceStatus.ONLINE -> DevicesProto.ConnectionState.DEVICE
               DeviceState.DeviceStatus.BOOTLOADER -> DevicesProto.ConnectionState.BOOTLOADER
               DeviceState.DeviceStatus.DEVICE -> DevicesProto.ConnectionState.DEVICE
               DeviceState.DeviceStatus.HOST -> DevicesProto.ConnectionState.HOST
               DeviceState.DeviceStatus.RECOVERY -> DevicesProto.ConnectionState.RECOVERY
               DeviceState.DeviceStatus.SIDELOAD -> DevicesProto.ConnectionState.SIDELOAD
               DeviceState.DeviceStatus.RESCUE -> DevicesProto.ConnectionState.RESCUE
               else -> DevicesProto.ConnectionState.ANY
           }
        }

        val DEFAULT_SPEED = 5000L

        fun genProtobufOutput(deviceList: List<DeviceState>, textFormat: Boolean) : ByteBuffer {
            val listBuilder = DevicesProto.Devices.newBuilder()
            for (deviceState in deviceList) {
                val deviceBuilder = DevicesProto.Device.newBuilder()
                deviceBuilder.setSerial(deviceState.deviceId)
                deviceBuilder.setState(deviceStatusToProto(deviceState.deviceStatus))
                deviceBuilder.setBusAddress("0:0")
                deviceBuilder.setProduct(deviceState.manufacturer)
                deviceBuilder.setModel(deviceState.model)
                deviceBuilder.setDevice(deviceState.buildVersionRelease)
                deviceBuilder.setTransportId(deviceState.transportId.toLong())
                deviceBuilder.setConnectionType(
                    when(deviceState.hostConnectionType) {
                        DeviceState.HostConnectionType.USB -> DevicesProto.ConnectionType.USB
                        else -> DevicesProto.ConnectionType.SOCKET
                    })
                deviceBuilder.setMaxSpeed(DEFAULT_SPEED)
                deviceBuilder.setNegotiatedSpeed(DEFAULT_SPEED)
                listBuilder.addDevice(deviceBuilder.build())
            }

            if (textFormat) {
                return ByteBuffer.wrap(listBuilder.build().toString().toByteArray())
            } else {
                return ByteBuffer.wrap(listBuilder.build().toByteArray())
            }
        }
    }
}
