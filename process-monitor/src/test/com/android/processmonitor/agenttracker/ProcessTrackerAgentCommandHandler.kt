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
package com.android.processmonitor.agenttracker

import ai.grazie.utils.dropPrefix
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.ShellV2Protocol
import com.android.fakeadbserver.services.ServiceOutput
import com.android.fakeadbserver.shellv2commandhandlers.SimpleShellV2Handler
import com.android.processmonitor.agenttracker.AgentProcessTracker.Companion.AGENT_PATH
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking

private const val STDOUT = "STDOUT:"
private const val STDERR = "STDERR:"
private const val EOF = "EOF"

/**
 * Simulates the execution of the process-tracker binary.
 *
 * See `//tools/base/process-monitor/process-tracker-agent`
 */
internal class ProcessTrackerAgentCommandHandler : SimpleShellV2Handler(
    ShellProtocolType.SHELL_V2,
    AGENT_PATH
) {

    private val channel = Channel<String>(10)

    val invocations = mutableListOf<String>()

    suspend fun emitStdout(line: String): ProcessTrackerAgentCommandHandler {
        channel.send("$STDOUT$line\n")
        return this
    }
    suspend fun emitEof() = channel.send(EOF)

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        serviceOutput: ServiceOutput,
        device: DeviceState,
        shellCommand: String,
        shellCommandArgs: String?
    ) {
        invocations.add("${device.deviceId}: $shellCommandArgs")
        runBlocking {
            channel.consumeAsFlow().takeWhile {
                it != EOF
            }.collect {
                when {
                    it.startsWith(STDOUT) -> serviceOutput.writeStdout(it.dropPrefix(STDOUT))
                    it.startsWith(STDERR) -> serviceOutput.writeStderr(it.dropPrefix(STDERR))
                    else -> throw IllegalStateException("Unexpected data: $it")
                }
            }
        }
        serviceOutput.writeExitCode(0)
    }
}
