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
package com.android.fakeadbserver.hostcommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.server.adb.protos.DevicesProto
import java.net.Socket
import java.nio.ByteBuffer

class ServerStatusCommandHandler :  SimpleHostCommandHandler("server-status") {

    override fun invoke(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState?,
        args: String
    ): Boolean {
        val stream = responseSocket.getOutputStream()

        val status =  DevicesProto.AdbServerStatus.newBuilder()
        status.setUsbBackend(DevicesProto.AdbServerStatus.UsbBackend.LIBUSB)
        status.setUsbBackendForced(true)
        status.setMdnsBackend(DevicesProto.AdbServerStatus.MdnsBackend.OPENSCREEN)
        status.setMdnsBackendForced(true)
        status.setVersion("35.0.2")
        status.setExecutableAbsolutePath("/path/to/adb")
        status.setLogAbsolutePath("/tmp/adb.log")
        status.setOs(System.getProperty("os.name"))

        writeOkay(stream)
        val statusBytes = ByteBuffer.wrap(status.build().toByteArray())
        write4ByteHexIntString(stream, statusBytes.remaining())
        stream.write(statusBytes.array())
        stream.flush()
        return false
    }
}

