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
import com.android.fakeadbserver.ShellV2Protocol
import com.android.fakeadbserver.services.ServiceOutput
import java.nio.ByteBuffer

/**
 * A [SimpleShellV2Handler] that replies to `stdout`, `stderr` and `exit` messages sent
 * to its `stdin` stream with the corresponding [ShellV2Protocol.PacketKind] packets
 */
class ShellProtocolEchoV2CommandHandler() : SimpleShellV2Handler(
    ShellProtocolType.SHELL_V2,
    "shell-protocol-echo"
) {

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        serviceOutput: ServiceOutput,
        device: DeviceState,
        shellCommand: String,
        shellCommandArgs: String?
    ) {
        // Forward `stdin`, `stderr` and `exit` lines as `stdout` packets
        var exitCode = 0
        val stdoutPrefix = "stdout:"
        val stderrPrefix = "stderr:"
        val exitPrefix = "exit:"
        val stdinProcessor = StdinProcessor { line ->
            when {
                line.startsWith(stdoutPrefix) -> {
                    serviceOutput.writeStdout(
                        line.takeLast(line.length - stdoutPrefix.length)
                            .trimStart()
                    )
                }

                line.startsWith(stderrPrefix) -> {
                    serviceOutput.writeStderr(
                        line.takeLast(line.length - stderrPrefix.length)
                            .trimStart()
                    )
                }

                line.startsWith(exitPrefix) -> {
                    exitCode = line.takeLast(line.length - exitPrefix.length)
                        .trimStart()
                        .toInt()
                }
            }
        }
        while (true) {
            val buffer = ByteArray(100)
            val numRead = serviceOutput.readStdin(buffer, 0, buffer.size)
            if (numRead < 0) {
                stdinProcessor.flush()
                serviceOutput.writeExitCode(exitCode)
                break
            }
            stdinProcessor.process(if (numRead == buffer.size) buffer else buffer.copyOfRange(
                0,
                numRead
            ))
        }
    }

    class StdinProcessor(private val block: (String) -> Unit) {

        val buffer: ByteBuffer = ByteBuffer.allocate(8_192)

        fun flush() {
            // Data if from [0 -> position1], so ensure we have
            // [position(0), limit(position1)]
            buffer.flip()
            if (buffer.remaining() > 0) {
                block(bufferToByteArray().toString(Charsets.UTF_8))
                buffer.compact()
            }
        }

        fun process(bytes: ByteArray) {
            for (b in bytes) {
                buffer.put(b)
                if (b.toChar() == '\n') {
                    flush()
                }
            }
        }

        private fun bufferToByteArray(): ByteArray {
            // Copy bytes from [position -> limit] into new byte array
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return bytes
        }
    }
}
