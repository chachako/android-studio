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
import com.android.fakeadbserver.PortForwarder
import com.android.fakeadbserver.devicecommandhandlers.ForwardArgs.Companion.parse
import java.io.IOException
import java.net.Socket
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * host-prefix:forward ADB command adds a port forward to the connected device. This implementation
 * only handles tcp sockets, and not Unix domain sockets.
 */
class ForwardCommandHandler : SimpleHostCommandHandler("forward") {

    override fun invoke(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState?,
        args: String
    ): Boolean {
        assert(device != null)
        val stream = try {
            responseSocket.getOutputStream()
        } catch (ignored: IOException) {
            return false
        }
        val (norebind, hostTransport, fromTransportArg, deviceTransport, toTransportArg) = parse(
            args
        )
        device?.delayStdout?.let {
            if (it != Duration.ZERO) {
                Thread.sleep(it.toLong(DurationUnit.MILLISECONDS))
            }
        }
        when (hostTransport) {
            "tcp" -> {}
            "local" -> {
                writeFailResponse(
                    stream, "Host Unix domain sockets not supported in fake ADB Server."
                )
                return false
            }

            else -> {
                writeFailResponse(stream, "Invalid host transport specified: $hostTransport")
                return false
            }
        }
        var hostPort: Int
        var hostPortToSendBack: Int?
        try {
            hostPort = fromTransportArg.toInt()
            if (hostPort == 0) {
                // This is to emulate ADB Server behavior of picking an available port
                // This is currently hard-coded as we don't actually create sockets
                hostPort = 40000 + (Math.random() * 100).toInt()
            }
            hostPortToSendBack = hostPort
        } catch (ignored: NumberFormatException) {
            writeFailResponse(
                stream, "Invalid host port specified: $fromTransportArg"
            )
            return false
        }
        val forwarder: PortForwarder
        forwarder = when (deviceTransport) {
            "tcp" -> try {
                val devicePort = toTransportArg.toInt()
                PortForwarder.createPortForwarder(hostPort, devicePort)
            } catch (ignored: NumberFormatException) {
                writeFailResponse(
                    stream, "Invalid device port or pid specified: "
                            + toTransportArg
                )
                return false
            }

            "local" -> PortForwarder.createUnixForwarder(
                hostPort, toTransportArg
            )

            "jdwp" -> {
                writeFailResponse(stream, "JDWP connections not yet supported in fake ADB Server.")
                return false
            }

            else -> {
                writeFailResponse(stream, "Invalid device transport specified: $deviceTransport")
                return false
            }
        }
        val bindOk = device!!.addPortForwarder(forwarder, norebind)
        // We send 2 OKAY answers: 1st OKAY is connect, 2nd OKAY is status.
        // See
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=1058
        writeOkay(stream)
        if (bindOk) {
            if (hostPortToSendBack != null) {
                writeOkayResponse(stream, hostPortToSendBack.toString())
            } else {
                writeOkay(stream)
            }
        } else {
            writeFailResponse(stream, "Could not bind to the specified forwarding ports.")
        }

        // We always close the connection, as per ADB protocol spec.
        return false
    }

}
