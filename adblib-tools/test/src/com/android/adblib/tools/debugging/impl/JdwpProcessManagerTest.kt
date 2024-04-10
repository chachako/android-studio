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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.tools.AdbLibToolsProperties
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.jdwpProcessTracker
import com.android.adblib.tools.debugging.properties
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import com.android.adblib.waitForDevice
import com.android.fakeadbserver.ClientState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class JdwpProcessManagerTest : AdbLibToolsTestBase() {

    @Test
    fun addProcessesEnsuresProcessInstancesAreCreated() = runBlockingWithTimeout {
        // Prepare
        setHostPropertyValue(
            session.host,
            AdbLibToolsProperties.JDWP_PROCESS_MANAGER_REFRESH_DELAY,
            Duration.ofSeconds(20)
        )
        val device = fakeAdb.addDevice()

        // Act
        val processManager = device.jdwpProcessManager
        val map = processManager.addProcesses(setOf(10, 15, 20))

        // Assert
        assertEquals(3, map.size)
        assertEquals(setOf(10, 15, 20), map.keys)
        map.values.forEach { jdwpProcess ->
            assertNotNull(jdwpProcess)
            assertTrue(jdwpProcess.scope.isActive)
        }
    }

    @Test
    fun addProcessesCallsAreCumulative() = runBlockingWithTimeout {
        // Prepare
        setHostPropertyValue(
            session.host,
            AdbLibToolsProperties.JDWP_PROCESS_MANAGER_REFRESH_DELAY,
            Duration.ofSeconds(20)
        )
        val device = fakeAdb.addDevice()

        // Act
        val processManager = device.jdwpProcessManager
        val map1 = processManager.addProcesses(setOf(10, 20))
        val map2 = processManager.addProcesses(setOf(15, 20))

        // Assert
        (map1.values + map2.values).forEach { jdwpProcess ->
            assertNotNull(jdwpProcess)
            assertTrue(jdwpProcess.scope.isActive)
        }
    }

    @Test
    fun addProcessesEnsuresProcessIsCollectingProperties() = runBlockingWithTimeout {
        // Prepare
        val device = fakeAdb.addDevice()
        device.createFakeAdbProcess(10)
        val processManager = device.jdwpProcessManager
        val map = processManager.addProcesses(setOf(10))
        val process = map[10]!!

        // Act
        val props = process.propertiesFlow.first {
            // Having a process names implies monitoring has started
            it.processName != null
        }

        // Assert
        assertEquals(10, props.pid)
        assertEquals("p1", props.processName)
        assertEquals("pkg", props.packageName)
    }

    @Test
    fun addProcessesIsThreadSafeAndCoherent() = runBlockingWithTimeout {
        // Prepare
        val device = fakeAdb.addDevice()
        val processManager = device.jdwpProcessManager
        val processIds = setOf(10, 20, 25, 30, 45, 100, 2560)

        // Act
        val maps1Async = async(Dispatchers.Default) {
            (1..100).map {
                async {
                    processManager.addProcesses(processIds)
                }
            }.awaitAll()
        }
        val maps = maps1Async.await()

        // Assert
        assertEquals(100, maps.size)
        maps.forEach { map ->
            assertEquals(processIds.size, map.size)
            processIds.forEach { pid ->
                assertNotNull(map[pid])
            }
        }
    }

    @Test
    fun addProcessesWithDelegatingSessionIsThreadSafeAndCoherent() = runBlockingWithTimeout {
        // Prepare
        val device = fakeAdb.addDevice()
        device.createFakeAdbProcess(10)
        val delegatingSession = device.session.createDelegatingSession()
        val delegatingSessionDevice = delegatingSession.connectedDevicesTracker.waitForDevice(device.serialNumber)
        val processManager = device.jdwpProcessManager
        val delegatingProcessManager = delegatingSessionDevice.jdwpProcessManager
        val processIds = setOf(10, 20, 25, 30, 45, 100, 2560)

        // Act
        val maps1Async = async(Dispatchers.Default) {
            (1..100).map {
                async {
                    processManager.addProcesses(processIds)
                }
            }.awaitAll()
        }

        val maps2Async = async(Dispatchers.Default) {
            (1..100).map {
                async {
                    delegatingProcessManager.addProcesses(processIds)
                }
            }.awaitAll()
        }

        val maps1 = maps1Async.await()
        val maps2 = maps2Async.await()

        // Assert
        listOf(maps1, maps2).forEach { maps ->
            assertEquals(100, maps.size)
            maps.forEach { map ->
                assertEquals(processIds.size, map.size)
                processIds.forEach { pid ->
                    assertNotNull(map[pid])
                }
            }
        }
    }

    @Test
    fun addProcessesWithDelegatingSessionTracksProcessIds() = runBlockingWithTimeout {
        // Prepare
        val device = fakeAdb.addDevice()
        device.createFakeAdbProcess(10)
        val delegatingSession = device.session.createDelegatingSession()
        val delegatingSessionDevice = delegatingSession.connectedDevicesTracker.waitForDevice(device.serialNumber)
        val processManager = device.jdwpProcessManager
        val delegatingProcessManager = delegatingSessionDevice.jdwpProcessManager
        val processIds = setOf(10, 20, 25, 30, 45, 100, 2560)

        // Act
        val map = processManager.addProcesses(processIds)
        val delegatingMap = delegatingProcessManager.addProcesses(processIds)
        val process = map[45] ?: throw AssertionError("Process not found")
        val delegatingProcess = delegatingMap[45] ?: throw AssertionError("Process not found")

        // Only process 10 is active, so process 45 should become "inactive" on its own, i.e.
        // the processManager tracks PIDs automatically
        yieldUntil {
            !process.scope.isActive
        }
        yieldUntil {
            !delegatingProcess.scope.isActive
        }

        // Assert
        assertNotNull(process)
        assertNotNull(delegatingProcess)
    }

    @Test
    fun delegatingProcessToAlternateAdbSessionIfPresent() = runBlockingWithTimeout {
        // Prepare
        setHostPropertyValue(
            session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(1)
        )
        val (_, _, connectedJdwpProcess) = createJdwpProcess(waitForDebugger = false)
        val delegatingSession = session.createDelegatingSession()
        val delegatingProcess = delegatingSession.awaitDelegatingProcess(connectedJdwpProcess)

        // Act: Collecting properties of the "connected" process should impact the properties
        // of the "delegating" process
        yieldUntil { delegatingProcess.properties.completed }

        // Assert
        val properties = delegatingProcess.properties
        assertProcessPropertiesComplete(properties)
    }

    @Test
    fun delegatingProcessSharedJdwpSessionToAlternateAdbSessionIfPresent() = runBlockingWithTimeout {
        // Prepare
        val (_, _, connectedJdwpProcess) = createJdwpProcess(waitForDebugger = false)
        val wasJdwpSessionRetained = CompletableDeferred<Unit>()
        val job = launch {
            // Wait until at least one jdwp session activation (from the delegating process)
            connectedJdwpProcess.jdwpSessionActivationCount.first { it >= 1 }
            wasJdwpSessionRetained.complete(Unit)
        }
        val delegatingSession = connectedJdwpProcess.device.session.createDelegatingSession()
        val delegatingProcess = delegatingSession.awaitDelegatingProcess(connectedJdwpProcess)

        // Act: Acquiring the jdwp session from the delegating process should end up
        // acquiring the jdwp session from the "connected" process
        delegatingProcess.withJdwpSession {
            wasJdwpSessionRetained.await()
        }
        job.join()

        // Assert
        assertTrue(wasJdwpSessionRetained.isCompleted)
    }

    @Test
    fun delegatingProcessWithManyJdwpSessionActivationIsThreadSafe() = runBlockingWithTimeout {
        // Prepare
        val (_, _, connectedJdwpProcess) = createJdwpProcess(waitForDebugger = false)
        val delegatingSessionCount = 5
        val jdwpSessionActivationCount = 100
        val delegatingProcesses = (1..delegatingSessionCount).map {
            val delegatingSession = connectedJdwpProcess.device.session.createDelegatingSession()
            delegatingSession.awaitDelegatingProcess(connectedJdwpProcess)
        }
        setHostPropertyValue(
            connectedJdwpProcess.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(1)
        )

        // Act
        val allSessionsSeen = CompletableDeferred<Unit>()
        launch {
            // Wait until all sessions are active, then tell them all to terminate
            connectedJdwpProcess.jdwpSessionActivationCount.first {
                it == delegatingSessionCount * jdwpSessionActivationCount
            }
            allSessionsSeen.complete(Unit)
        }
        delegatingProcesses.map { process ->
            launch(Dispatchers.Default) {
                (1..jdwpSessionActivationCount).map {
                    launch(Dispatchers.Default) {
                        process.withJdwpSession {
                            allSessionsSeen.await()
                        }
                    }
                }.joinAll()
            }
        }.joinAll()

        // `awaitDelegatingProcess` above triggers process tracking which in turn triggers process
        // property collection. As a result `activationCountStateFlow` is incremented
        // by the `JdwpProcessPropertiesCollector`. Wait for properties collector to be done so that
        // `activationCountStateFlow` is decremented.
        yieldUntil { connectedJdwpProcess.properties.completed }

        // Assert
        assertEquals(0, connectedJdwpProcess.jdwpSessionActivationCount.value)
    }

    @Test
    fun delegatingProcessIsClosedWhenProcessTerminates() = runBlockingWithTimeout {
        // Prepare
        val (_, _, connectedJdwpProcess) = createJdwpProcess(waitForDebugger = false)
        val delegatingSession = connectedJdwpProcess.device.session.createDelegatingSession()
        val delegatingProcess = delegatingSession.awaitDelegatingProcess(connectedJdwpProcess)

        // Act
        val activeBefore = delegatingProcess.scope.isActive
        fakeAdb.device(connectedJdwpProcess.device.serialNumber)
            .stopClient(connectedJdwpProcess.pid)
        yieldUntil { !delegatingProcess.scope.isActive }
        val activeAfter = delegatingProcess.scope.isActive

        // Assert
        assertTrue(activeBefore)
        assertFalse(activeAfter)
    }

    private suspend fun createJdwpProcess(
        deviceApi: Int = 30,
        pid: Int = 10,
        waitForDebugger: Boolean = true
    ): Triple<FakeAdbServerProvider, ConnectedDevice, AbstractJdwpProcess> {
        val device = fakeAdb.addDevice(deviceApi)
        val clientState = device.createFakeAdbProcess(pid, waitForDebugger)
        val process = device.jdwpProcessManager.getProcess(clientState.pid)
        return Triple(fakeAdb, device, process)
    }

    private suspend fun FakeAdbServerProvider.addDevice(deviceApi: Int = 30): ConnectedDevice {
        val fakeAdb = this
        val fakeDevice = addFakeDevice(fakeAdb, deviceApi)
        return waitForOnlineConnectedDevice(session, fakeDevice.deviceId)
    }

    private suspend fun ConnectedDevice.createFakeAdbProcess(
        pid: Int = 10,
        waitForDebugger: Boolean = false
    ): ClientState {
        return fakeAdb.device(serialNumber).startClient(
            pid = pid,
            uid = 2,
            processName = "p1",
            packageName = "pkg",
            isWaiting = waitForDebugger
        )
    }

    private fun assertProcessPropertiesComplete(properties: JdwpProcessProperties) {
        assertEquals(10, properties.pid)
        assertEquals("p1", properties.processName)
        assertEquals(2, properties.userId)
        assertEquals("pkg", properties.packageName)
        assertEquals("FakeVM", properties.vmIdentifier)
        assertEquals("x86_64", properties.abi)
        assertEquals("-jvmflag=true", properties.jvmFlags)
        @Suppress("DEPRECATION")
        assertFalse(properties.isNativeDebuggable)
        assertFalse(properties.jdwpSessionProxyStatus.isExternalDebuggerAttached)
        assertNotNull(properties.jdwpSessionProxyStatus.socketAddress)
        assertEquals(
            listOf(
                "hprof-heap-dump",
                "method-sample-profiling",
                "view-hierarchy",
                "method-trace-profiling",
                "hprof-heap-dump-streaming",
                "method-trace-profiling-streaming",
                "opengl-tracing"
            ), properties.features
        )
        assertNull(properties.exception)
        assertTrue(properties.completed)
    }

    private fun AdbSession.createDelegatingSession(): AdbSession {
        val childSession = AdbSession.createChildSession(
            this,
            fakeAdbRule.host,
            fakeAdb.createChannelProvider(fakeAdbRule.host)
        )
        childSession.addJdwpProcessSessionFinder(object : JdwpProcessSessionFinder {
            override fun findDelegateSession(forSession: AdbSession): AdbSession {
                return if (forSession == childSession) {
                    this@createDelegatingSession
                } else {
                    forSession
                }
            }
        })
        return childSession
    }

    private suspend fun AdbSession.awaitDelegatingProcess(firstProcess: JdwpProcess): JdwpProcess {
        // Find device in "session2", then look for process with same pid
        val sessionDevice = connectedDevicesTracker.waitForDevice(firstProcess.device.serialNumber)
        return sessionDevice.jdwpProcessTracker.processesFlow.transform { processList ->
            processList.firstOrNull { it.pid == firstProcess.pid }?.also {
                emit(it)
            }
        }.first()
    }

    private fun JdwpProcessManager.getProcess(pid: Int): AbstractJdwpProcess {
        return this.addProcesses(setOf(pid))[pid]!! as AbstractJdwpProcess
    }
}
