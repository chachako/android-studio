/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.fakeadbserver.shellcommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.services.PackageManager
import com.android.fakeadbserver.services.ShellCommandOutput
import com.android.fakeadbserver.services.ServiceManager

/*
 * A [ShellHandler] thar writes 'Service <argument>: found' to stdout.
 */
class ServiceCommandHandler(shellProtocolType: ShellProtocolType) : SimpleShellHandler(
    shellProtocolType,
    "service"
) {

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        statusWriter: StatusWriter,
        serviceOutput: ShellCommandOutput,
        device: DeviceState,
        shellCommand: String,
        shellCommandArgs: String?
    ) {
        statusWriter.writeOk()

        when (shellCommandArgs?.substringBefore(" ")) {
            "list" -> {
                val serviceManager = device.serviceManager
                val services = serviceManager.services()
                var i = 0
                val output = "Found ${services.size} services:\n" + services.entries.joinToString {
                    "${i++}       ${it.key}: [${it.value ?: ""}]\n"
                }
                serviceOutput.writeStdout(output)
            }
            else -> {
                serviceOutput.writeStderr("Invalid arguments")
            }
        }
    }
}
