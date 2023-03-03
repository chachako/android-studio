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
import com.android.fakeadbserver.ShellV2Protocol
import com.google.common.base.Charsets
import java.io.ByteArrayOutputStream
import java.lang.Integer.min

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
class CatV2CommandHandler : SimpleShellV2Handler("cat") {

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        protocol: ShellV2Protocol,
        device: DeviceState,
        args: String?
    ) {
        protocol.writeOkay()

        if (args.isNullOrEmpty()) {
            forwardStdinAsStdout(protocol)
            return
        }

        if (tryHandleCatProcPidCmdline(protocol, device, args)) {
            return
        }

        catRegularFiles(protocol, device, args)
    }

    private fun forwardStdinAsStdout(protocol: ShellV2Protocol) {
        // Forward `stdin` packets back as `stdout` packets
        val stdinProcessor = StdinProcessor(protocol)
        while (true) {
            val packet = protocol.readPacket()
            when (packet.kind) {
                ShellV2Protocol.PacketKind.CLOSE_STDIN -> {
                    stdinProcessor.flush()
                    protocol.writeExitCode(0)
                    break
                }
                ShellV2Protocol.PacketKind.STDIN -> {
                    stdinProcessor.process(packet.bytes)
                }
                else -> {
                    // Ignore?
                }
            }
        }
    }

    private fun tryHandleCatProcPidCmdline(
        protocol: ShellV2Protocol,
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
            protocol.writeStderr("profileableClient with a pid $pid not found")
            return true
        }

        protocol.writeStdout(profileableClient.commandLine.toByteArray(Charsets.UTF_8))
        protocol.writeExitCode(0)
        return true
    }

    private fun catRegularFiles(protocol: ShellV2Protocol, device: DeviceState, args: String) {
        val fileName = args.trim()
        if (fileName.contains("\\s+")) {
            throw NotImplementedError("Multiple files or file names with spaces are not implemented")
        }

        val file = device.getFile(fileName)
        if (file == null) {
            protocol.writeStderr("No such file or directory")
        } else {
            protocol.writeStdout(file.bytes)
        }
    }

    class StdinProcessor(private val protocol: ShellV2Protocol) {
        val byteStream = ByteArrayOutputStream()

        fun flush() {
            if (byteStream.size() > 0) {
                protocol.writeStdout(byteStream.toByteArray())
                byteStream.reset()
            }
        }

        fun process(bytes: ByteArray) {
            // Send `stdout` packets of 200 bytes max. so simulate potentially custom
            // process on a "real" device
            var offset = 0
            while (offset < bytes.size) {
                val endIndex = min(offset + STDOUT_PACKET_SIZE, bytes.size)
                protocol.writeStdout(bytes.copyOfRange(offset, endIndex))
                offset = endIndex
            }
        }
    }
}
