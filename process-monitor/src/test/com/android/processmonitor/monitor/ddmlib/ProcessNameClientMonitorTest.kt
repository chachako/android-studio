/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.processmonitor.monitor.ddmlib

import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.ddmlib.IDevice
import com.android.processmonitor.common.FakeProcessTracker
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.common.ProcessTracker
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for [ProcessNameClientMonitor]
 */
@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalCoroutinesApi::class) // runTest is experimental (replaced runTestTest)
class ProcessNameClientMonitorTest {

    private val process1 = ProcessInfo(1, "package1", "process1")
    private val process2 = ProcessInfo(2, "package2", "process2")
    private val process3 = ProcessInfo(3, "package3", "process3")

    private val device = mockDevice("device1")

    @Test
    fun trackClients_addClients(): Unit = runTest {
        FakeProcessNameMonitorFlows().use { flows ->
            val monitor = processNameClientMonitor(device = device, flows = flows)

            flows.sendClientEvents(
                device.serialNumber,
                clientsAddedEvent(process1, process2),
                clientsAddedEvent(process3),
            )

            advanceUntilIdle()
            assertThat(monitor.getProcessNames(1)).isEqualTo(process1.names)
            assertThat(monitor.getProcessNames(2)).isEqualTo(process2.names)
            assertThat(monitor.getProcessNames(3)).isEqualTo(process3.names)
        }
    }

    @Test
    fun trackClients_addAndRemoveClients(): Unit = runTest {
        FakeProcessNameMonitorFlows().use { flows ->
            val monitor = processNameClientMonitor(device = device, flows = flows)

            flows.sendClientEvents(
                device.serialNumber,
                clientsAddedEvent(process1, process2, process3),
                clientsRemovedEvent(1, 2, 3)
            )

            advanceUntilIdle()
            assertThat(monitor.clientProcessNames.map).containsExactly(
                1, process1.names,
                2, process2.names,
                3, process3.names,
            )
            assertThat(monitor.clientProcessNames.retentionList).containsExactly(1, 2, 3)
        }
    }

    @Test
    fun trackClients_addTrackedProcesses(): Unit = runTest {
        FakeProcessTracker().use { tracker ->
            val monitor = processNameClientMonitor(device = device, processTracker = tracker)

            tracker.send(
                ProcessAdded(1, process1.packageName, process1.processName),
                ProcessAdded(2, process2.packageName, process2.processName),
                ProcessAdded(3, process3.packageName, process3.processName),
            )

            advanceUntilIdle()
            assertThat(monitor.getProcessNames(1)).isEqualTo(process1.names)
            assertThat(monitor.getProcessNames(2)).isEqualTo(process2.names)
            assertThat(monitor.getProcessNames(3)).isEqualTo(process3.names)
        }
    }

    @Test
    fun trackClients_addAndRemoveTrackedProcesses(): Unit = runTest {
        FakeProcessTracker().use { tracker ->
            val monitor = processNameClientMonitor(device = device, processTracker = tracker)

            tracker.send(
                ProcessAdded(1, process1.packageName, process1.processName),
                ProcessAdded(2, process2.packageName, process2.processName),
                ProcessAdded(3, process3.packageName, process3.processName),
                ProcessRemoved(1),
                ProcessRemoved(2),
                ProcessRemoved(3),
            )

            advanceUntilIdle()
            assertThat(monitor.trackerProcessNames.map).containsExactly(
                1, process1.names,
                2, process2.names,
                3, process3.names,
            )
            assertThat(monitor.trackerProcessNames.retentionList).containsExactly(1, 2, 3)
        }
    }

    @Test
    fun trackClients_addFromBoth(): Unit = runTest {
        FakeProcessNameMonitorFlows().use { flows ->
            FakeProcessTracker().use { tracker ->
                val monitor =
                    processNameClientMonitor(
                        device = device,
                        flows = flows,
                        processTracker = tracker,
                    )

                val trackerProcess = ProcessInfo(1, "tracker-package", "tracker-process")
                val clientProcess = ProcessInfo(2, "client-package", "client-process")
                tracker.send(
                    ProcessAdded(1, trackerProcess.packageName, trackerProcess.processName)
                )
                flows.sendClientEvents(device.serialNumber, clientsAddedEvent(clientProcess))

                advanceUntilIdle()
                assertThat(monitor.getProcessNames(1)).isEqualTo(trackerProcess.names)
                assertThat(monitor.getProcessNames(2)).isEqualTo(clientProcess.names)
            }
        }
    }

    @Test
    fun trackClients_prefersClients(): Unit = runTest {
        FakeProcessNameMonitorFlows().use { flows ->
            FakeProcessTracker().use { tracker ->
                val monitor =
                    processNameClientMonitor(
                        device = device,
                        flows = flows,
                        processTracker = tracker,
                    )

                val trackerProcess = ProcessInfo(1, "tracker-package", "tracker-process")
                val clientProcess = ProcessInfo(1, "client-package", "client-process")
                tracker.send(
                    ProcessAdded(1, trackerProcess.packageName, trackerProcess.processName)
                )
                flows.sendClientEvents(device.serialNumber, clientsAddedEvent(clientProcess))

                advanceUntilIdle()
                assertThat(monitor.getProcessNames(1)).isEqualTo(clientProcess.names)
            }
        }
    }

    @Test
    fun stop_closesFlow(): Unit = runTest {
        val flows = TerminationTrackingProcessNameMonitorFlows()
        val monitor = processNameClientMonitor(device, flows)
        advanceTimeBy(2000) // Let the flow run a few cycles
        assertThat(flows.isClientFlowStarted(device.serialNumber)).isTrue()

        monitor.close()

        advanceUntilIdle()
        assertThat(flows.isClientFlowTerminated(device.serialNumber)).isTrue()
    }

    private fun CoroutineScope.processNameClientMonitor(
        device: IDevice = this@ProcessNameClientMonitorTest.device,
        flows: ProcessNameMonitorFlows = FakeProcessNameMonitorFlows(),
        processTracker: ProcessTracker? = null,
        maxPids: Int = 10
    ): ProcessNameClientMonitor {
        return ProcessNameClientMonitor(
            this,
            device,
            flows,
            processTracker,
            FakeAdbLoggerFactory().logger,
            maxProcessRetention = maxPids
        )
            .apply {
                start()
            }
    }
}
