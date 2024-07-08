/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adblib.tools.debugging.processinventory

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.SOCKET_CONNECT_TIMEOUT_MS
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.CoroutineTestUtils
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.adblib.tools.AdbLibToolsProperties
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.jdwpProcessFlow
import com.android.adblib.tools.debugging.processinventory.server.ProcessInventoryServerConfiguration
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.job
import org.junit.Assert
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.time.Duration

class ProcessInventoryJdwpProcessPropertiesCollectorFactoryTest {

    @JvmField
    @Rule
    val fakeAdbRule = FakeAdbServerProviderRule {
        installDefaultCommandHandlers()
        setFeatures("push_sync")
    }

    @JvmField
    @Rule
    val closeables = CloseablesRule()

    @Ignore("b/345498106")
    @Test
    fun testJdwpPropertiesCollectionIsDistributed(): Unit =
        CoroutineTestUtils.runBlockingWithTimeout {
            // Prepare
            // Create 2 adblib sessions for a single fake adb server, install a
            // ProcessInventoryServer on both sessions, start collecting properties from both
            // sessions with a long timeout for keeping JDWP sessions open (one of the 2 adblib
            // sessions is stuck waiting for the other one when trying to collect properties),
            // check that properties are available faster than the long delay on *both* sessions.
            fakeAdbRule.host.setPropertyValue(
                AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
                Duration.ofMinutes(10)
            )
            testWithMultipleSessions(sequence {
                val session1 = fakeAdbRule.adbSession
                val session2 = createSessionClone(fakeAdbRule)
                session1.installTestProcessInventoryServer()
                session2.installTestProcessInventoryServer()
                yield(session1)
                yield(session2)
            })
        }

    @Ignore("b/349545929")
    @Test
    fun testJdwpPropertiesCollectionSupportsBothDistributedAndLocalOnly(): Unit =
        CoroutineTestUtils.runBlockingWithTimeout {
            fakeAdbRule.host.setPropertyValue(
                AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
                Duration.ofSeconds(2)
            )
            testWithMultipleSessions(sequence {
                val session1 = fakeAdbRule.adbSession
                val session2 = createSessionClone(fakeAdbRule)
                val session3 = createSessionClone(fakeAdbRule)
                session1.installTestProcessInventoryServer()
                session2.installTestProcessInventoryServer()
                yield(session1)
                yield(session2)
                yield(session3)
            })
        }

    @Ignore("b/345498106")
    @Test
    fun testDistributedJdwpPropertiesCollectionRecoversFromSessionClosing(): Unit =
        CoroutineTestUtils.runBlockingWithTimeout {
            // Prepare
            // Create 3 adblib sessions for a single fake adb server, install a
            // ProcessInventoryServer on all sessions, start collecting properties from both
            // sessions with a long timeout for keeping JDWP sessions open (one of the 2 adblib
            // sessions is stuck waiting for the other one when trying to collect properties),
            // check that properties are available faster than the long delay on *both* sessions.
            val fakeAdbServer = fakeAdbRule.fakeAdb
            fakeAdbRule.host.setPropertyValue(
                AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
                Duration.ofMinutes(10)
            )
            val session1 = fakeAdbRule.adbSession
            val session2 = createSessionClone(fakeAdbRule)
            val session3 = createSessionClone(fakeAdbRule)
            val pid1 = 20
            val fakeDevice = fakeAdbServer.addSampleDevice()
            val clientState1 = fakeDevice.addSampleJdwpProcess(pid1)
            session1.installTestProcessInventoryServer()
            val connectedDevice1 = waitForOnlineConnectedDevice(session1, fakeDevice.deviceId)
            val defProps1Pid1 = fetchProcessPropertiesAsync(connectedDevice1, pid1)
            val pid2 = 20
            val clientState2 = fakeDevice.addSampleJdwpProcess(pid2)

            // Act
            val props1 = defProps1Pid1.await()
            session1.closeAndJoin()
            session2.installTestProcessInventoryServer()
            session3.installTestProcessInventoryServer()
            val connectedDevice2 = waitForOnlineConnectedDevice(session2, fakeDevice.deviceId)
            val connectedDevice3 = waitForOnlineConnectedDevice(session3, fakeDevice.deviceId)

            val defProps2Pid1 = fetchProcessPropertiesAsync(connectedDevice2, pid1)
            val defProps2Pid2 = fetchProcessPropertiesAsync(connectedDevice2, pid2)

            val defProps3Pid1 = fetchProcessPropertiesAsync(connectedDevice3, pid1)
            val defProps3Pid2 = fetchProcessPropertiesAsync(connectedDevice3, pid2)

            val props2pid1 = defProps2Pid1.await()
            val props2pid2 = defProps2Pid2.await()

            val props3pid1 = defProps3Pid1.await()
            val props3pid2 = defProps3Pid2.await()

            // Assert
            Assert.assertEquals(pid1, props1.pid)
            Assert.assertEquals(clientState1.processName, props1.processName)
            Assert.assertEquals(clientState1.packageName, props1.packageName)
            Assert.assertEquals(clientState1.uid, props1.userId)
            Assert.assertEquals(clientState1.architecture, props1.abi)
            Assert.assertEquals(false, props1.isWaitingForDebugger)
            Assert.assertTrue(props1.features.contains("feat1"))
            Assert.assertTrue(props1.features.contains("feat2"))
            Assert.assertTrue(props1.features.contains("feat3"))

            with(props2pid1) {
                assertJdwpPropertiesAreEqual(props1)
            }

            with(props3pid1) {
                assertJdwpPropertiesAreEqual(props1)
            }

            Assert.assertEquals(pid2, props2pid2.pid)
            Assert.assertEquals(clientState2.processName, props2pid2.processName)
            Assert.assertEquals(clientState2.packageName, props2pid2.packageName)
            Assert.assertEquals(clientState2.uid, props2pid2.userId)
            Assert.assertEquals(clientState2.architecture, props2pid2.abi)
            Assert.assertEquals(false, props2pid2.isWaitingForDebugger)
            Assert.assertTrue(props2pid2.features.contains("feat1"))
            Assert.assertTrue(props2pid2.features.contains("feat2"))
            Assert.assertTrue(props2pid2.features.contains("feat3"))

            with(props2pid2) {
                assertJdwpPropertiesAreEqual(props3pid2)
            }
        }

    private suspend fun testWithMultipleSessions(sequenceOf: Sequence<AdbSession>) {
        val fakeAdbServer = fakeAdbRule.fakeAdb
        val sessions = sequenceOf.toList()

        // Create a single process for testing
        val pid = 20
        val fakeDevice = fakeAdbServer.addSampleDevice()
        val clientState = fakeDevice.addSampleJdwpProcess(pid)

        val connectedDevices = sessions.map {
            waitForOnlineConnectedDevice(it, fakeDevice.deviceId)
        }

        // Act
        val props = coroutineScope {
            val defProps = connectedDevices.map {
                fetchProcessPropertiesAsync(it, pid)
            }

            defProps.awaitAll()
        }

        // Assert
        val props1 = props.first()
        Assert.assertEquals(pid, props1.pid)
        Assert.assertEquals(clientState.processName, props1.processName)
        Assert.assertEquals(clientState.packageName, props1.packageName)
        Assert.assertEquals(clientState.uid, props1.userId)
        Assert.assertEquals(clientState.architecture, props1.abi)
        Assert.assertEquals(false, props1.isWaitingForDebugger)
        Assert.assertTrue(props1.features.contains("feat1"))
        Assert.assertTrue(props1.features.contains("feat2"))
        Assert.assertTrue(props1.features.contains("feat3"))

        props.drop(1).forEach {
            with(it) {
                assertJdwpPropertiesAreEqual(props1)
            }
        }
    }

    private fun JdwpProcessProperties.assertJdwpPropertiesAreEqual(props1: JdwpProcessProperties) {
        Assert.assertEquals(props1.pid, this.pid)
        Assert.assertEquals(props1.processName, this.processName)
        Assert.assertEquals(props1.packageName, this.packageName)
        Assert.assertEquals(props1.userId, this.userId)
        Assert.assertEquals(props1.vmIdentifier, this.vmIdentifier)
        Assert.assertEquals(props1.abi, this.abi)
        Assert.assertEquals(props1.jvmFlags, this.jvmFlags)
        @Suppress("DEPRECATION")
        Assert.assertEquals(props1.isNativeDebuggable, this.isNativeDebuggable)
        Assert.assertEquals(props1.features, this.features)
    }

    private suspend fun AdbSession.closeAndJoin() {
        val job = scope.coroutineContext.job
        close()
        job.join()
    }

    private fun CoroutineScope.fetchProcessPropertiesAsync(device: ConnectedDevice, pid: Int) =
        async {
            val process = device.jdwpProcessFlow.mapNotNull {
                it.firstOrNull { process -> process.pid == pid }
            }.first()

            val properties = process.propertiesFlow.first {
                it.packageName == "a.b.c" &&
                        it.features.isNotEmpty()
            }
            properties
        }

    private fun FakeAdbServerProvider.addSampleDevice(): DeviceState {
        return connectDevice(
            deviceId = "1234",
            manufacturer = "test1",
            deviceModel = "test2",
            release = "model",
            sdk = "30",
            hostConnectionType = com.android.fakeadbserver.DeviceState.HostConnectionType.USB
        ).also {
            it.deviceStatus = com.android.fakeadbserver.DeviceState.DeviceStatus.ONLINE
        }
    }

    private fun DeviceState.addSampleJdwpProcess(pid: Int): ClientState {
        return startClient(
            pid,
            uid = 0,
            packageName = "a.b.c",
            isWaiting = false
        ).also {
            it.addFeature("feat1")
            it.addFeature("feat2")
            it.addFeature("feat3")
        }
    }

    private fun createSessionClone(fakeAdbRule: FakeAdbServerProviderRule): AdbSession {
        val host = fakeAdbRule.host
        return AdbSession.create(
            host,
            fakeAdbRule.createChannelProvider(),
            Duration.ofMillis(SOCKET_CONNECT_TIMEOUT_MS)
        ).also {
            registerCloseable(it)
        }
    }

    private fun AdbSession.installTestProcessInventoryServer() {
        ProcessInventoryJdwpProcessPropertiesCollectorFactory.installForSession(
            this,
            TestServerConfig(),
            enabled = { true }
        )
    }

    private class TestServerConfig : ProcessInventoryServerConfiguration {

        override var clientDescription: String = "test_client"

        override var serverDescription: String = "test_server"
    }

    private fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }
}
