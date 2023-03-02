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

import com.android.adblib.AdbLogger
import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.LineShellV2Collector
import com.android.adblib.RemoteFileMode
import com.android.adblib.ShellCommandOutputElement.ExitCode
import com.android.adblib.ShellCommandOutputElement.StderrLine
import com.android.adblib.ShellCommandOutputElement.StdoutLine
import com.android.adblib.shellAsLines
import com.android.adblib.shellCommand
import com.android.adblib.syncSend
import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.common.ProcessTracker
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Implementation of [ProcessTracker]
 *
 * Deploys a runs a process`-tracker` binary on a device and converts it output to a
 * [Flow] of [ProcessEvent].
 *
 * See `//tools/base/process-monitor/process-tracker-agent`
 */
internal class AgentProcessTracker(
    private val adbSession: AdbSession,
    private val serialNumber: String,
    private val deviceAbi: String,
    private val agentSourcePath: Path,
    private val intervalMillis: Int,
    private val logger: AdbLogger,
    private val context: CoroutineContext = EmptyCoroutineContext,
) : ProcessTracker {

    override suspend fun trackProcesses()
            : Flow<ProcessEvent> = flow {
        val deviceSelector = DeviceSelector.fromSerialNumber(serialNumber)
        pushAgent(deviceSelector, deviceAbi)
        val command = "$AGENT_PATH --interval $intervalMillis"
        adbSession.deviceServices.shellCommand(deviceSelector, command)
            .withCollector(LineShellV2Collector())
            .execute()
            .collect {
                // TODO(aalbert): Support restarting on crashes etc
                when (it) {
                    is StdoutLine -> handleLine(it.contents)
                    is StderrLine -> logger.warn("$AGENT_NAME error: ${it.contents}")
                    is ExitCode -> logger.warn("$AGENT_NAME terminated: rc=${it.exitCode}")
                }
            }
    }.flowOn(adbSession.ioDispatcher + context)

    // TODO(aalbert): Support multiple ABI's?
    private suspend fun pushAgent(deviceSelector: DeviceSelector, deviceAbi: String) {
        val command = "mkdir -p $AGENT_DIR; chmod 700 $AGENT_DIR; chown shell:shell $AGENT_DIR"
        adbSession.deviceServices.shellAsLines(deviceSelector, command).collect {
            when {
                it is StderrLine ->
                    logger.warn("Unable to create $AGENT_DIR dir: ${it.contents.take(200)}")

                it is ExitCode && it.exitCode != 0 ->
                    logger.warn("Unable to create $AGENT_DIR dir: ${it.exitCode}")
            }
        }
        val binary = agentSourcePath.resolve("native/$deviceAbi/$AGENT_NAME")
        val permissions = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_EXECUTE)
        adbSession.deviceServices.syncSend(deviceSelector, binary, AGENT_PATH, permissions)
    }

    private suspend fun FlowCollector<ProcessEvent>.handleLine(line: String) {
        val event = parseLine(line)
        if (event != null) {
            emit(event)
        } else {
            logger.warn("Invalid tracker line: '$line'")
        }
    }

    private fun parseLine(line: String): ProcessEvent? {
        val split = line.split(' ')
        val size = split.size
        if (size < 2) {
            return null
        }
        val pid = split[1].toIntOrNull() ?: return null
        val action = split[0]
        return when {
            action == "-" -> ProcessRemoved(pid)
            size < 3 -> null
            action == "+" -> ProcessEvent.ProcessAdded(pid, null, split[2])
            else -> null
        }
    }

    companion object {

        const val AGENT_NAME = "process-tracker"

        @VisibleForTesting
        internal const val AGENT_DIR = "/data/local/tmp/.studio"

        @VisibleForTesting
        internal const val AGENT_PATH = "$AGENT_DIR/$AGENT_NAME"

    }

}
