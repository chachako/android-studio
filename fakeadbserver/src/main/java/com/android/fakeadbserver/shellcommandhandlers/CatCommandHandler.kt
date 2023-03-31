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
import com.android.fakeadbserver.services.ShellCommandOutput
import java.io.ByteArrayOutputStream

/**
 * A [SimpleShellHandler] that supports the following behaviors.
 *
 * If there are no arguments to the "cat" command then it outputs all characters received from `stdin` back to `stdout`,
 * one line at a time, i.e. characters are written back to `stdout` only when a newline ("\n")
 * character is received from `stdin`.
 *
 * If "cat" argument is in the form of "/proc/{pid}/cmdline then it returns a command line
 * which started the process.
 */
class CatCommandHandler(shellProtocolType: ShellProtocolType) : SimpleShellHandler(
    shellProtocolType,
    "cat"
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
        if (shellCommandArgs.isNullOrEmpty()) {
            forwardStdinAsStdout(shellCommandOutput)
            return
        }

        if (tryHandleCatProcPidCmdline(shellCommandOutput, device, shellCommandArgs)) {
            return
        }

        catRegularFiles(shellCommandOutput, device, shellCommandArgs)
    }

    /** Outputs all characters received from `stdin` back to `stdout`, one
     * line at a time, i.e. characters are written back to `stdout` only when a newline ("\n")
     * character is received from `stdin`.
     **/
    private fun forwardStdinAsStdout(shellCommandOutput: ShellCommandOutput) {
        val stdoutStream = ByteArrayOutputStream()
        val buffer = ByteArray(1)
        while (true) {
            val numRead = shellCommandOutput.readStdin(buffer, 0, buffer.size)
            if (numRead < 0) {
                shellCommandOutput.writeStdout(stdoutStream.toByteArray())
                shellCommandOutput.writeExitCode(0)
                break
            }
            val ch = buffer[0].toInt()
            stdoutStream.write(ch)
            if (ch == '\n'.code) {
                shellCommandOutput.writeStdout(stdoutStream.toByteArray())
                stdoutStream.reset()
            }
        }
    }

    private fun tryHandleCatProcPidCmdline(
        shellCommandOutput: ShellCommandOutput,
        device: DeviceState,
        args: String
    ): Boolean {
        val procIdRegex = Regex("/proc/(\\d+)/cmdline")
        val matchResult = procIdRegex.find(args) ?: return false
        val pid = matchResult.groups[1]!!.value.toInt()
        if (device.getClient(pid) != null) {
            throw NotImplementedError("cmdline for ClientState is not implemented in FakeAdb")
        }

        val profileableClient = device.getProfileableProcess(pid)
        if (profileableClient == null) {
            shellCommandOutput.writeStderr("profileableClient with a pid $pid not found")
            return true
        }

        shellCommandOutput.writeStdout(profileableClient.commandLine)
        shellCommandOutput.writeExitCode(0)
        return true
    }

    private fun catRegularFiles(shellCommandOutput: ShellCommandOutput, device: DeviceState, args: String) {
        val fileName = args.trim()
        if (fileName.contains("\\s+")) {
            throw NotImplementedError("Multiple files or file names with spaces are not implemented")
        }

        val file = device.getFile(fileName)
        if (file == null) {
            shellCommandOutput.writeStderr("No such file or directory")
        } else {
            shellCommandOutput.writeStdout(file.bytes)
        }
    }
}
