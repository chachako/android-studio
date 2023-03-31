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
        try {
            if (shellCommandArgs == null) {
                statusWriter.writeFail()
                return
            }

            // Save command to logs so tests can consult them.
            shellCommandArgs.let {
                device.addCmdLog(shellCommandArgs)
            }

            statusWriter.writeOk()

            val response: String = when {
                shellCommandArgs.split(" ")
                    .contains(PackageManager.BAD_FLAG) -> "Error: (requested to fail via -BAD_FAG)"

                shellCommandArgs.startsWith("package install-create") -> installMultiple()
                shellCommandArgs.startsWith("package install-commit") -> installCommit()
                shellCommandArgs.startsWith("package install-write") -> installWrite(
                  shellCommandArgs,
                  shellCommandOutput
                )

                else -> ""
            }

            shellCommandOutput.writeStdout(response)
        } catch (ignored: IOException) {
        }

        return
    }

    /**
     * Handler for commands that look like:
     *
     *    adb shell cmd package install-create -r -t --ephemeral -S 1298948
     */
    private fun installMultiple(): String {
        return "Success: created install session [1234]"
    }

    /**
     * handler for commands that look like:
     *
     *    adb shell cmd package install-commit 538681231
     */
    private fun installCommit(): String {
        return "Success\n"
    }

    /**
     * Handler for commands that look like:
     *
     *    cmd package install-write -S 1289508 548838628 0_base-debug -
     *
     * `args` would be "package install-write -S 1289508 548838628 0_base-debug -" in
     * the above example.
     */
    private fun installWrite(args: String, shellCommandOutput: ShellCommandOutput): String {
        val streamLengthExtractor = Pattern.compile("package install-write\\s+-S\\s+(\\d+).*")
        val streamLengthMatcher = streamLengthExtractor.matcher(args)
        streamLengthMatcher.find()

        val expectedBytesLength = if (streamLengthMatcher.groupCount() < 1) {
            0
        } else {
            try {
                streamLengthMatcher.group(1).toInt()
            } catch (numFormatException: NumberFormatException) {
                0
            }
        }

        val buffer = ByteArray(1024)
        var totalBytesRead = 0
        while (totalBytesRead < expectedBytesLength) {
            val numRead: Int =
                shellCommandOutput.readStdin(buffer, 0, Math.min(buffer.size, expectedBytesLength - totalBytesRead))
            if (numRead < 0) {
                break
            }
            totalBytesRead += numRead
        }

        return "Success: streamed ${totalBytesRead} bytes\n"
    }
}
