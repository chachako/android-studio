/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.fakeadbserver.services.ShellV2Output
import java.io.IOException
import java.util.regex.Pattern

class CmdCommandHandler(shellProtocolType: ShellProtocolType) : SimpleShellHandler(
    shellProtocolType, "cmd"
) {

    override fun execute(
      fakeAdbServer: FakeAdbServer,
      statusWriter: StatusWriter,
      shellCommandOutput: ShellCommandOutput,
      device: DeviceState,
      shellCommand: String,
      shellCommandArgs: String?
    ) {
        statusWriter.writeOk()

        if (shellCommandArgs == null) {
           statusWriter.writeFail()
           return
        }

        // Save command to logs so tests can consult them.
        shellCommandArgs.let {
           device.addCmdLog(shellCommandArgs)
        }

        // Wrap stdin/stdout and execute abb command
        device.serviceManager.processCommand(shellCommandArgs.split(" "), shellCommandOutput)
    }
}
