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
import com.android.fakeadbserver.MdnsService
import java.io.IOException
import java.net.Socket
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.function.Consumer

/** host:mdns:check returns the status of mDNS support  */
class MdnsCommandHandler : HostCommandHandler() {

    override fun invoke(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState?,
        args: String
    ): Boolean {
        try {
            if ("check" == args) {
                writeOkayResponse(
                    responseSocket.getOutputStream(),
                    "mdns daemon version [FakeAdb implementation]\n"
                )
            } else if ("services" == args) {
                val result = formatMdnsServiceList(fakeAdbServer.mdnsServicesCopy.get())
                writeOkayResponse(responseSocket.getOutputStream(), result)
            } else {
                writeFailResponse(
                    responseSocket.getOutputStream(), "Invalid mdns command"
                )
            }
        } catch (ignored: IOException) {
            return false
        } catch (ignored: ExecutionException) {
            return false
        } catch (ignored: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return false
    }

    private fun formatMdnsServiceList(services: List<MdnsService>): String {
        val sb = StringBuilder()
        services.forEach(
            Consumer { service: MdnsService ->
                sb.append(
                    String.format(
                        Locale.US,
                        "%s\t%s\t%s:%d\n",
                        service.instanceName,
                        service.serviceName,
                        service.deviceAddress.hostString,
                        service.deviceAddress.port
                    )
                )
            })
        return sb.toString()
    }

    companion object {

        const val COMMAND = "mdns"
    }
}
