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
package com.android.processmonitor.monitor

import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.common.ProcessTracker
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for [com.android.processmonitor.monitor.PerDeviceMonitor]
 */
@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalCoroutinesApi::class) // runTest is experimental (replaced runTestTest)
class PerDeviceMonitorTest {

    private val logger = FakeAdbLoggerFactory().logger

    @Test
    fun newProcesses() = runTest {
        val tracker = Tracker()
        val monitor = perDeviceMonitor(tracker)

        tracker.send(ProcessAdded(1, null, "process1"))
        tracker.send(ProcessAdded(2, "package2", "process2"))
        tracker.send(ProcessAdded(3, "package3", "process3"))
        advanceUntilIdle()

        assertThat(monitor.getProcessNames(1)).isEqualTo(ProcessNames("process1", "process1"))
        assertThat(monitor.getProcessNames(2)).isEqualTo(ProcessNames("package2", "process2"))
        assertThat(monitor.getProcessNames(3)).isEqualTo(ProcessNames("package3", "process3"))
    }

    @Test
    fun noApplicationId_doesNotReplace() = runTest {
        val tracker = Tracker()
        val monitor = perDeviceMonitor(tracker)

        tracker.send(ProcessAdded(1, "package1", "process1"))
        advanceUntilIdle()
        tracker.send(ProcessAdded(1, null, "process1"))
        advanceUntilIdle()

        assertThat(monitor.getProcessNames(1)).isEqualTo(ProcessNames("package1", "process1"))
    }

    @Test
    fun noApplicationId_differentProcessName_doesReplace() = runTest {
        val tracker = Tracker()
        val monitor = perDeviceMonitor(tracker)

        tracker.send(ProcessAdded(1, "package1", "process1"))
        advanceUntilIdle()
        tracker.send(ProcessAdded(1, null, "process2"))
        advanceUntilIdle()

        assertThat(monitor.getProcessNames(1)).isEqualTo(ProcessNames("process2", "process2"))
    }

    @Test
    fun noApplicationId_isReplaceByApplicationId() = runTest {
        val tracker = Tracker()
        val monitor = perDeviceMonitor(tracker)

        tracker.send(ProcessAdded(1, null, "process1"))
        advanceUntilIdle()
        tracker.send(ProcessAdded(1, "package1", "process1"))
        advanceUntilIdle()

        assertThat(monitor.getProcessNames(1)).isEqualTo(ProcessNames("package1", "process1"))
    }

    @Test
    fun propagatesMaxProcessRetention() = runTest {
        val tracker = Tracker()
        val monitor = perDeviceMonitor(tracker, retention = 1)

        tracker.send(ProcessAdded(1, null, "process1"))
        tracker.send(ProcessAdded(2, null, "process2"))
        tracker.send(ProcessRemoved(1))
        tracker.send(ProcessRemoved(2))
        advanceUntilIdle()

        assertThat(monitor.getProcessNames(1)).isNull()
        assertThat(monitor.getProcessNames(2)).isEqualTo(ProcessNames("process2", "process2"))
    }

    private fun CoroutineScope.perDeviceMonitor(
        tracker: ProcessTracker,
        retention: Int = 10,
    ): PerDeviceMonitor = PerDeviceMonitor(this, logger, retention, tracker).apply { start() }

    private class Tracker : ProcessTracker {

        private val channel = Channel<ProcessEvent>(10)

        suspend fun send(event: ProcessEvent) {
            channel.send(event)
        }

        override suspend fun trackProcesses() = channel.consumeAsFlow()
    }

}
