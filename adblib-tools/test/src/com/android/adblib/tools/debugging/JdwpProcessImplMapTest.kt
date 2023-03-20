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
package com.android.adblib.tools.debugging

import com.android.adblib.ConnectedDevice
import com.android.adblib.scope
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.adblib.tools.debugging.impl.JdwpProcessImpl
import com.android.adblib.tools.debugging.impl.jdwpProcessImplMap
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class JdwpProcessImplMapTest {

    @JvmField
    @Rule
    val fakeAdbRule = FakeAdbServerProviderRule {
        installDefaultCommandHandlers()
        setFeatures("push_sync")
    }

    private val fakeAdb get() = fakeAdbRule.fakeAdb
    private val hostServices get() = fakeAdbRule.adbSession.hostServices

    @Test
    fun retainWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val connectedDevice = createTestDevice()
        val map = connectedDevice.jdwpProcessImplMap()

        // Act
        val p1 = map.retain(10)

        // Assert
        Assert.assertTrue(p1.scope.isActive)
    }

    @Test
    fun retainTwiceWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val connectedDevice = createTestDevice()
        val map = connectedDevice.jdwpProcessImplMap()

        // Act
        val p1 = map.retain(10)
        val p2 = map.retain(10)

        // Assert
        Assert.assertSame(p1, p2)
        Assert.assertTrue(p1.scope.isActive)
    }

    @Test
    fun lastReleaseClosesProcess(): Unit = runBlockingWithTimeout {
        // Prepare
        val connectedDevice = createTestDevice()
        val map = connectedDevice.jdwpProcessImplMap()

        // Act
        val p1 = map.retain(10)
        map.release(10)

        // Assert
        Assert.assertFalse("process scope should be closed", p1.scope.isActive)
    }

    @Test
    fun retainAndReleaseAreThreadSafe(): Unit = runBlockingWithTimeout {
        // Prepare
        val connectedDevice = createTestDevice()
        val map = connectedDevice.jdwpProcessImplMap()

        // Act
        val jdwpProcess = map.retain(10)
        val j1 = async {
            val list = mutableListOf<JdwpProcessImpl>()
            for (i in 0..1000) {
                list.add(map.retain(10))
            }
            list
        }
        val j2 = async {
            for (i in 0..1000) {
                map.release(10)
            }
        }
        awaitAll(j1, j2)
        val instances = j1.await()

        // Assert
        Assert.assertTrue(jdwpProcess.scope.isActive)
        map.release(10)
        Assert.assertFalse(jdwpProcess.scope.isActive)
        for (i in 0..1000) {
            Assert.assertSame(jdwpProcess, instances[i])
        }
    }

    @Test
    fun releaseThenRetainProducesNewInstance(): Unit = runBlockingWithTimeout {
        // Prepare
        val connectedDevice = createTestDevice()
        val map = connectedDevice.jdwpProcessImplMap()

        // Act
        val p1 = map.retain(10)
        map.release(10)
        val p2 = map.retain(10)

        // Assert
        Assert.assertNotSame(p1, p2)
        Assert.assertFalse("process scope should be closed", p1.scope.isActive)
        Assert.assertTrue("process scope should still be active", p2.scope.isActive)
    }

    @Test
    fun closeCallsCloseOnAllJdwpProcessImplInstances(): Unit = runBlockingWithTimeout {
        // Prepare
        val connectedDevice = createTestDevice()
        val map = connectedDevice.jdwpProcessImplMap()
        val p1 = map.retain(10)
        val p2 = map.retain(11)
        val p3 = map.retain(12)

        // Act
        map.close()

        // Assert
        Assert.assertFalse(p1.scope.isActive)
        Assert.assertFalse(p2.scope.isActive)
        Assert.assertFalse(p3.scope.isActive)
    }

    @Test
    fun allProcessesAreClosedAfterDeviceIsDisconnected(): Unit = runBlockingWithTimeout {
        // Prepare
        val connectedDevice = createTestDevice()
        val map = connectedDevice.jdwpProcessImplMap()
        val p1 = map.retain(10)
        val p2 = map.retain(11)
        val p3 = map.retain(12)

        // Act
        fakeAdb.disconnectDevice(connectedDevice.serialNumber)
        yieldUntil { !connectedDevice.scope.isActive }

        // Assert
        // Note: We use `yieldUntil` instead of assert due to the asynchronous nature
        // of coroutine cancellation.
        yieldUntil { !p1.scope.isActive }
        yieldUntil { !p2.scope.isActive }
        yieldUntil { !p3.scope.isActive }
    }

    @Test
    fun jdwProcessTrackerAndAppProcessTrackerReturnSameJdwpProcessInstances(): Unit =
        runBlockingWithTimeout {
            val connectedDevice = createTestDevice()
            val pid10 = 10
            val pid11 = 11

            // Act
            val fakeDevice = fakeAdb.device(connectedDevice.serialNumber)
            fakeDevice.startClient(pid10, 0, "a.b.c", false)
            fakeDevice.startClient(pid11, 0, "a.b.c", false)

            val jdwpProcessTracker = connectedDevice.jdwpProcessTracker
            val appProcessTracker = connectedDevice.appProcessTracker

            yieldUntil { jdwpProcessTracker.processesFlow.value.size == 2 }
            yieldUntil { appProcessTracker.appProcessFlow.value.size == 2 }

            // Assert
            Assert.assertSame(
                jdwpProcessTracker.processesFlow.value.first { it.pid == pid10 },
                appProcessTracker.appProcessFlow.value.first { it.pid == pid10 }.jdwpProcess
            )
            Assert.assertSame(
                jdwpProcessTracker.processesFlow.value.first { it.pid == pid11 },
                appProcessTracker.appProcessFlow.value.first { it.pid == pid11 }.jdwpProcess
            )
        }

    private suspend fun createTestDevice(): ConnectedDevice {
        val deviceID = "1234"
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "30", // SDK >= 30 is required for abb_exec feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        return waitForOnlineConnectedDevice(hostServices.session, fakeDevice.deviceId)
    }
}
