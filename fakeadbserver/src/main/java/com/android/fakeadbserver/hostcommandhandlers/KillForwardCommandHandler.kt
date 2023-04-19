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
import com.android.fakeadbserver.FakeAdbServer
import java.io.IOException
import java.io.OutputStream
import java.net.Socket

/**
 * host-prefix:killforward ADB command removes a port forward from the specified local port. This
 * implementation only handles tcp sockets, and not Unix domain sockets.
 */
class KillForwardCommandHandler : HostCommandHandler() {

    override fun invoke(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState?,
        args: String
    ): Boolean {
        assert(device != null)
        val stream: OutputStream
        stream = try {
            responseSocket.getOutputStream()
        } catch (ignored: IOException) {
            return false
        }
        val hostAddress = args.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        when (hostAddress[0]) {
            "tcp" -> {}
            "local" -> {
                writeFailResponse(
                    stream, "Host Unix domain sockets not supported in fake ADB Server."
                )
                return false
            }

            else -> {
                writeFailResponse(stream, "Invalid host transport specified: " + hostAddress[0])
                return false
            }
        }
        val hostPort: Int
        hostPort = try {
            hostAddress[1].toInt()
        } catch (ignored: NumberFormatException) {
            writeFailResponse(stream, "Invalid port specified: " + hostAddress[1])
            return false
        }
        if (!device!!.removePortForwarder(hostPort)) {
            writeFailResponse(stream, "Could not successfully remove forward.")
            return false
        }
        // We send 2 OKAY answers: 1st OKAY is connect, 2nd OKAY is status.
        // See
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=1058
        writeOkay(stream)
        writeOkay(stream)

        // We always close the connection, as per ADB protocol spec.
        return false
    }

    companion object {

        const val COMMAND = "killforward"
    }
}
