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
package com.android.fakeadbserver.shellv2commandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.services.ServiceOutput

const val STDOUT_PACKET_SIZE = 80

/**
 * A [SimpleShellV2Handler] that supports the following behaviors.
 *
 * If there are no arguments to the "cat" command then it outputs all characters received from `stdin` back to `stdout`,
 * one line at a time, i.e. characters are written back to `stdout` only when a newline ("\n")
 * character is received from `stdin`.
 *
 * If "cat" argument is in the form of "/proc/{pid}/cmdline then it returns a command line
 * which started the process.
 */
class CatV2CommandHandler(shellProtocolType: ShellProtocolType) : SimpleShellV2Handler(
    shellProtocolType,
    "cat"
) {

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        serviceOutput: ServiceOutput,
        device: DeviceState,
        shellCommand: String,
        shellCommandArgs: String?
    ) {
        if (shellCommandArgs.isNullOrEmpty()) {
            forwardStdinAsStdout(serviceOutput, device)
            return
        }

        if (tryHandleCatProcPidCmdline(serviceOutput, device, shellCommandArgs)) {
            return
        }

        catRegularFiles(serviceOutput, device, shellCommandArgs)
    }

    private fun forwardStdinAsStdout(serviceOutput: ServiceOutput, device: DeviceState) {
        // TODO: Combine v1 and v2 handling
        // Forward `stdin` packets back as `stdout` packets
        when (shellProtocolType) {
            ShellProtocolType.SHELL -> {
                val sb = StringBuilder()
                while (true) {
                    // Note: We process each character individually to ensure we send back
                    // all character without any loss and/or conversion.
                    val buffer = ByteArray(1)
                    val numRead = serviceOutput.readStdin(buffer, 0, buffer.size)
                    if (numRead < 0) {
                        serviceOutput.writeStdout(sb.toString())
                        serviceOutput.writeExitCode(0)
                        break
                    }
                    val ch = buffer[0].toInt()
                    if (ch != '\n'.code) {
                        sb.append(ch.toChar())
                    } else {
                        // TODO: Handle '\n'->'\r\n' for older devices in serviceOutput
                        sb.append(shellNewLine(device))
                        serviceOutput.writeStdout(sb.toString())
                        sb.setLength(0)
                    }
                }
            }

            ShellProtocolType.SHELL_V2 -> {
                while (true) {
                    // Send `stdout` packets of 200 bytes max. so simulate potentially custom
                    // process on a "real" device
                    val buffer = ByteArray(STDOUT_PACKET_SIZE)
                    val numRead = serviceOutput.readStdin(buffer, 0, buffer.size)
                    if (numRead < 0) {
                        serviceOutput.writeExitCode(0)
                        break
                    }
                    serviceOutput.writeStdout(
                        if (numRead == buffer.size) buffer else buffer.copyOfRange(
                            0,
                            numRead
                        )
                    )
                }
            }
        }
    }

    private fun tryHandleCatProcPidCmdline(
        serviceOutput: ServiceOutput,
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
            serviceOutput.writeStderr("profileableClient with a pid $pid not found")
            return true
        }

        serviceOutput.writeStdout(profileableClient.commandLine)
        serviceOutput.writeExitCode(0)
        return true
    }

    private fun catRegularFiles(serviceOutput: ServiceOutput, device: DeviceState, args: String) {
        val fileName = args.trim()
        if (fileName.contains("\\s+")) {
            throw NotImplementedError("Multiple files or file names with spaces are not implemented")
        }

        val file = device.getFile(fileName)
        if (file == null) {
            serviceOutput.writeStderr("No such file or directory")
        } else {
            serviceOutput.writeStdout(file.bytes)
        }
    }

    private fun shellNewLine(device: DeviceState): String {
        // Older devices use "\r\n" for newlines (legacy shell protocol only)
        return if (device.apiLevel <= 23) {
            "\r\n"
        } else {
            "\n"
        }
    }
}
