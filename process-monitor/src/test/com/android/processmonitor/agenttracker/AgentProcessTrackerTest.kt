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

import com.android.adblib.AdbSession
import com.android.adblib.RemoteFileMode
import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.fakeadbserver.DeviceFileState
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.DeviceState.HostConnectionType.USB
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import com.android.processmonitor.agenttracker.AgentProcessTracker.Companion.AGENT_PATH
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.testutils.TestResources
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

/**
 * Tests for [AgentProcessTracker]
 */
@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalCoroutinesApi::class) // runTest is experimental (replaced runTestTest)
internal class AgentProcessTrackerTest {

    @get:Rule
    val closeables = CloseablesRule()

    private val makeAgentDirHandler = MakeAgentDirCommandHandler()
    private val agentHandler = ProcessTrackerAgentCommandHandler()

    private val fakeAdb = closeables.register(
        FakeAdbServerProvider()
            .buildDefault()
            .start()
            .installDeviceHandler(agentHandler)
            .installDeviceHandler(makeAgentDirHandler)
            .installDeviceHandler(SyncCommandHandler())
    )
    private val adbHost = closeables.register(TestingAdbSessionHost())
    private val adbSession = closeables.register(
        AdbSession.create(adbHost, fakeAdb.createChannelProvider(adbHost))
    )

    private val logger = FakeAdbLoggerFactory().logger

    private val agentSourcePath = TestResources.getDirectory("/agent").toPath()

    @Test
    fun trackProcesses_createsStudioDirectory(): Unit = runTest {
        setupDevice("device1")
        agentHandler.emitEof()
        val tracker = agentProcessTracker("device1")

        tracker.trackProcesses().toList()

        assertThat(makeAgentDirHandler.invocations).containsExactly("device1")
    }

    @Test
    fun trackProcesses_tracks(): Unit = runTest {
        setupDevice("device1")
        agentHandler
            .emitStdout("+ 1 foo")
            .emitStdout("+ 2 bar")
            .emitStdout("- 1")
            .emitStdout("- 2")
            .emitEof()
        val tracker = agentProcessTracker("device1", intervalMillis = 1000)

        val events = tracker.trackProcesses().toList()

        assertThat(agentHandler.invocations).containsExactly("device1: --interval 1000")
        assertThat(events).containsExactly(
            ProcessAdded(1, null, "foo"),
            ProcessAdded(2, null, "bar"),
            ProcessRemoved(1),
            ProcessRemoved(2),
        ).inOrder()
    }

    @Test
    fun trackProcesses_ignoresBadLines(): Unit = runTest {
        setupDevice("device2")
        agentHandler
            .emitStdout("+ 5 foo")
            .emitStdout("bar")
            .emitStdout("- bar")
            .emitStdout("+ 2")
            .emitEof()
        val tracker = agentProcessTracker("device2", intervalMillis = 2000)

        val events = tracker.trackProcesses().toList()

        assertThat(agentHandler.invocations).containsExactly("device2: --interval 2000")
        assertThat(events).containsExactly(
            ProcessAdded(5, null, "foo"),
        ).inOrder()
    }

    @Test
    fun trackProcesses_pushesAgent(): Unit = runTest {
        val device = setupDevice("device1")
        agentHandler.emitEof()
        val tracker = agentProcessTracker("device1", "abi", agentSourcePath)
        tracker.trackProcesses().toList()

        val agentFile: DeviceFileState = device.getFile(AGENT_PATH)!!

        assertThat(agentFile.posixPermission()).isEqualTo("r-x------")
        assertThat(String(agentFile.bytes)).isEqualTo("Hello from process-tracker\n")
    }

    private fun setupDevice(serialNumber: String): DeviceState =
        fakeAdb.connectDevice(serialNumber, "", "", "13", "33", USB)

    private fun agentProcessTracker(
        serialNumber: String,
        deviceAbi: String = "abi",
        agentSourcePath: Path = this.agentSourcePath,
        intervalMillis: Int = 1000,
    ): AgentProcessTracker =
        AgentProcessTracker(
            adbSession,
            serialNumber,
            deviceAbi,
            agentSourcePath,
            intervalMillis,
            logger
        )
}

private fun DeviceFileState.posixPermission() = RemoteFileMode.fromModeBits(permission).posixString
