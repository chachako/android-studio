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
package com.android.adblib.tools.debugging.processinventory.impl

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.serialNumber
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils
import com.android.adblib.testingutils.CoroutineTestUtils.waitNonNull
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.JdwpSessionProxyStatus
import com.android.adblib.tools.debugging.processinventory.AdbLibToolsProcessInventoryServerProperties
import com.android.adblib.tools.debugging.processinventory.server.ProcessInventoryServerConfiguration
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class ProcessInventoryServerConnectionTest : AdbLibToolsTestBase() {

    @Test
    fun testProcessListIsEmptyWhenStarting(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        setHostPropertyValue(
            session.host,
            AdbLibToolsProcessInventoryServerProperties.LOCAL_PORT_V1,
            findFreeTcpPort()
        )
        val serverConnection = createServerConnection(session)
        val deviceState = addFakeDevice(fakeAdb, api = 32)
        val device = waitForOnlineConnectedDevice(session, deviceState.deviceId)

        // Act
        val processList = serverConnection.withConnectionForDevice(device) {
            processListStateFlow.first()
        }

        // Assert
        assertTrue(processList.isEmpty())
    }

    @Test
    fun testBlockIsCancelledWhenDeviceDisconnects(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        setHostPropertyValue(
            session.host,
            AdbLibToolsProcessInventoryServerProperties.LOCAL_PORT_V1,
            findFreeTcpPort()
        )
        val serverConnection = createServerConnection(session)
        val deviceState = addFakeDevice(fakeAdb, api = 32)
        val device = waitForOnlineConnectedDevice(session, deviceState.deviceId)

        // Act
        val started = CompletableDeferred<Unit>()
        val job = async {
            serverConnection.withConnectionForDevice(device) {
                processListStateFlow.collect {
                    started.complete(Unit)
                }
            }
        }
        started.await()
        fakeAdb.disconnectDevice(device.serialNumber)
        val result  = runCatching { job.await() }

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test
    fun testProcessUpdateUpdatesAllProperties(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        setHostPropertyValue(
            session.host,
            AdbLibToolsProcessInventoryServerProperties.LOCAL_PORT_V1,
            findFreeTcpPort()
        )
        val serverConnection = createServerConnection(session)
        val deviceState = addFakeDevice(fakeAdb, api = 32)
        val device = waitForOnlineConnectedDevice(session, deviceState.deviceId)

        // Act
        val processList = CopyOnWriteArrayList<List<JdwpProcessProperties>>()
        val job = async {
            serverConnection.withConnectionForDevice(device) {
                processListStateFlow.collect {
                    processList.add(it)
                }
            }
        }

        val localProperties = JdwpProcessProperties(
            pid = 10,
            processName = "Foo",
            packageName = "Bar",
            userId = 5,
            vmIdentifier = "vm",
            abi = "x86",
            jvmFlags = "flags",
            isNativeDebuggable = true,
            waitCommandReceived = true,
            isWaitingForDebugger = true,
            features = listOf("feat1", "feat2"),
            completed = true,
            exception = null
        )
        serverConnection.withConnectionForDevice(device) {
            sendProcessProperties(localProperties)
        }

        yieldUntil { processList.isNotEmpty() && processList.last().size == 1 }
        job.cancel()

        // Assert
        val lastList = processList.last()
        assertEquals(1, lastList.size)
        assertEquals(localProperties, lastList[0])
    }

    @Test
    fun testProcessUpdateUpdatesAllPropertiesExceptProxyAddressWhenIsWaitingForDebuggerIsFalse(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        setHostPropertyValue(
            session.host,
            AdbLibToolsProcessInventoryServerProperties.LOCAL_PORT_V1,
            findFreeTcpPort()
        )
        val serverConnection = createServerConnection(session)
        val deviceState = addFakeDevice(fakeAdb, api = 32)
        val device = waitForOnlineConnectedDevice(session, deviceState.deviceId)

        // Act
        val processList = CopyOnWriteArrayList<List<JdwpProcessProperties>>()
        val job = async {
            serverConnection.withConnectionForDevice(device) {
                processListStateFlow.collect {
                    processList.add(it)
                }
            }
        }

        val localProperties = JdwpProcessProperties(
            pid = 10,
            processName = "Foo",
            packageName = "Bar",
            userId = 5,
            vmIdentifier = "vm",
            abi = "x86",
            jvmFlags = "flags",
            isNativeDebuggable = true,
            waitCommandReceived = true,
            jdwpSessionProxyStatus = JdwpSessionProxyStatus(
                socketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 200),
                isExternalDebuggerAttached = true
            ),
            isWaitingForDebugger = false,
            features = listOf("feat1", "feat2"),
            completed = true,
            exception = null
        )
        serverConnection.withConnectionForDevice(device) {
            sendProcessProperties(localProperties)
        }

        yieldUntil { processList.isNotEmpty() && processList.last().size == 1 }
        job.cancel()

        // Assert
        val lastList = processList.last()
        val localPropertiesWithoutProxySocketAddress = localProperties.copy(
            jdwpSessionProxyStatus = JdwpSessionProxyStatus(
                socketAddress = null,
                isExternalDebuggerAttached = true
            )
        )
        assertEquals(1, lastList.size)
        assertEquals(localPropertiesWithoutProxySocketAddress, lastList[0])
    }

    @Test
    fun testProcessUpdateUpdatesAllPropertiesIncludingProxyAddressWhenIsWaitingForDebuggerIsTrue(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        setHostPropertyValue(
            session.host,
            AdbLibToolsProcessInventoryServerProperties.LOCAL_PORT_V1,
            findFreeTcpPort()
        )
        val serverConnection = createServerConnection(session)
        val deviceState = addFakeDevice(fakeAdb, api = 32)
        val device = waitForOnlineConnectedDevice(session, deviceState.deviceId)

        // Act
        val processList = CopyOnWriteArrayList<List<JdwpProcessProperties>>()
        val job = async {
            serverConnection.withConnectionForDevice(device) {
                processListStateFlow.collect {
                    processList.add(it)
                }
            }
        }

        val localProperties = JdwpProcessProperties(
            pid = 10,
            processName = "Foo",
            packageName = "Bar",
            userId = 5,
            vmIdentifier = "vm",
            abi = "x86",
            jvmFlags = "flags",
            isNativeDebuggable = true,
            waitCommandReceived = true,
            jdwpSessionProxyStatus = JdwpSessionProxyStatus(
                socketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 200),
                isExternalDebuggerAttached = true
            ),
            isWaitingForDebugger = true,
            features = listOf("feat1", "feat2"),
            completed = true,
            exception = null //Exception("Cancelled")
        )
        serverConnection.withConnectionForDevice(device) {
            sendProcessProperties(localProperties)
        }

        yieldUntil { processList.isNotEmpty() && processList.last().size == 1 }
        job.cancel()

        // Assert
        val lastList = processList.last()
        assertEquals(1, lastList.size)
        assertEquals(localProperties, lastList[0])
    }

    @Test
    fun testProcessUpdatesAreAccumulatedToStateFlow(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        setHostPropertyValue(
            session.host,
            AdbLibToolsProcessInventoryServerProperties.LOCAL_PORT_V1,
            findFreeTcpPort()
        )
        val serverConnection = createServerConnection(session)
        val deviceState = addFakeDevice(fakeAdb, api = 32)
        val device = waitForOnlineConnectedDevice(session, deviceState.deviceId)

        // Act
        val processList = CopyOnWriteArrayList<List<JdwpProcessProperties>>()
        val job = async {
            serverConnection.withConnectionForDevice(device) {
                processListStateFlow.collect {
                    processList.add(it)
                }
            }
        }
        serverConnection.withConnectionForDevice(device) {
            sendProcessProperties(JdwpProcessProperties(10, processName = "Foo"))
            sendProcessProperties(JdwpProcessProperties(11, processName = "Foo"))
            sendProcessProperties(JdwpProcessProperties(12, processName = "Foo"))
        }

        yieldUntil {
            processList.isNotEmpty() && processList.last().size == 3
        }
        job.cancel()

        // Assert
        val lastList = processList.last()
        assertEquals(3, lastList.size)
        assertNotNull(lastList.firstOrNull { it.pid == 10 })
        assertNotNull(lastList.firstOrNull { it.pid == 11 })
        assertNotNull(lastList.firstOrNull { it.pid == 12 })
    }

    @Test
    fun testWithConnectionForDeviceIsTransparentToExceptions(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        setHostPropertyValue(
            session.host,
            AdbLibToolsProcessInventoryServerProperties.LOCAL_PORT_V1,
            findFreeTcpPort()
        )
        val serverConnection = createServerConnection(session)
        val deviceState = addFakeDevice(fakeAdb, api = 32)
        val device = waitForOnlineConnectedDevice(session, deviceState.deviceId)

        // Act
        exceptionRule.expect(Exception::class.java)
        exceptionRule.expectMessage("Foo")
        serverConnection.withConnectionForDevice(device) {
            processListStateFlow.collect {
                throw Exception("Foo")
            }
        }

        // Assert
        @Suppress("UNREACHABLE_CODE")
        Assert.fail("Should not reach")
    }

    @Test
    fun testWithConnectionForDeviceIsTransparentToCancellation(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        setHostPropertyValue(
            session.host,
            AdbLibToolsProcessInventoryServerProperties.LOCAL_PORT_V1,
            findFreeTcpPort()
        )
        val serverConnection = createServerConnection(session)
        val deviceState = addFakeDevice(fakeAdb, api = 32)
        val device = waitForOnlineConnectedDevice(session, deviceState.deviceId)

        // Act
        exceptionRule.expect(CancellationException::class.java)
        exceptionRule.expectMessage("Foo")
        serverConnection.withConnectionForDevice(device) {
            processListStateFlow.collect {
                cancel("Foo")
            }
        }

        // Assert
        @Suppress("UNREACHABLE_CODE")
        Assert.fail("Should not reach")
    }

    @Test
    fun testProcessUpdatesAreForwardedToAllClients(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        setHostPropertyValue(
            session.host,
            AdbLibToolsProcessInventoryServerProperties.LOCAL_PORT_V1,
            findFreeTcpPort()
        )
        val clientCount = 10
        val sessions = listOf(session) + (2..clientCount).map {
            registerCloseable(fakeAdbRule.createTestAdbSession(session.host))
        }
        val serverConnections = sessions.map { session ->
            createServerConnection(session)
        }
        val deviceState = addFakeDevice(fakeAdb, api = 32)
        val devices = sessions.map { session ->
            async {
                waitForOnlineConnectedDevice(session, deviceState.deviceId)
            }
        }.awaitAll()

        // Act: One connection sends updates, all connections should receive them
        val processListFlows = sessions.map {
            MutableStateFlow<List<JdwpProcessProperties>>(emptyList())
        }
        val collectorJobs = List(sessions.size) { index ->
            async {
                serverConnections[index].withConnectionForDevice(devices[index]) {
                    this.processListStateFlow.collect {
                        processListFlows[index].value = it
                    }
                }
            }
        }

        // Send incremental updates, to make sure the server incrementally set fields
        var localProperties = JdwpProcessProperties(pid = 10)
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10
        }.also {
            assertEquals(10, it.pid)
            assertNull(it.processName)
            assertNull(it.packageName)
            assertNull(it.userId)
            assertNull(it.vmIdentifier)
            assertNull(it.abi)
            assertNull(it.jvmFlags)
            @Suppress("DEPRECATION")
            assertFalse(it.isNativeDebuggable)
            assertFalse(it.waitCommandReceived)
            assertFalse(it.isWaitingForDebugger)
            assertNull(it.jdwpSessionProxyStatus.socketAddress)
            assertFalse(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertTrue(it.features.isEmpty())
            assertFalse(it.completed)
            assertNull(it.exception)
        }

        localProperties = localProperties.copy(processName = "Foo")
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10 && it.processName == "Foo"
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertNull(it.packageName)
            assertNull(it.userId)
            assertNull(it.vmIdentifier)
            assertNull(it.abi)
            assertNull(it.jvmFlags)
            @Suppress("DEPRECATION")
            assertFalse(it.isNativeDebuggable)
            assertFalse(it.waitCommandReceived)
            assertFalse(it.isWaitingForDebugger)
            assertNull(it.jdwpSessionProxyStatus.socketAddress)
            assertFalse(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertTrue(it.features.isEmpty())
            assertFalse(it.completed)
            assertNull(it.exception)
        }

        localProperties = localProperties.copy(packageName = "Bar")
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10 && it.packageName == "Bar"
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertEquals("Bar", it.packageName)
            assertNull(it.userId)
            assertNull(it.vmIdentifier)
            assertNull(it.abi)
            assertNull(it.jvmFlags)
            @Suppress("DEPRECATION")
            assertFalse(it.isNativeDebuggable)
            assertFalse(it.waitCommandReceived)
            assertFalse(it.isWaitingForDebugger)
            assertNull(it.jdwpSessionProxyStatus.socketAddress)
            assertFalse(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertTrue(it.features.isEmpty())
            assertFalse(it.completed)
            assertNull(it.exception)
        }

        localProperties = localProperties.copy(userId = 12)
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10 && it.userId == 12
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertEquals("Bar", it.packageName)
            assertEquals(12, it.userId)
            assertNull(it.vmIdentifier)
            assertNull(it.abi)
            assertNull(it.jvmFlags)
            @Suppress("DEPRECATION")
            assertFalse(it.isNativeDebuggable)
            assertFalse(it.waitCommandReceived)
            assertFalse(it.isWaitingForDebugger)
            assertNull(it.jdwpSessionProxyStatus.socketAddress)
            assertFalse(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertTrue(it.features.isEmpty())
            assertFalse(it.completed)
            assertNull(it.exception)
        }

        localProperties = localProperties.copy(vmIdentifier = "vm")
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10 && it.vmIdentifier == "vm"
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertEquals("Bar", it.packageName)
            assertEquals(12, it.userId)
            assertEquals("vm", it.vmIdentifier)
            assertNull(it.abi)
            assertNull(it.jvmFlags)
            @Suppress("DEPRECATION")
            assertFalse(it.isNativeDebuggable)
            assertFalse(it.waitCommandReceived)
            assertFalse(it.isWaitingForDebugger)
            assertNull(it.jdwpSessionProxyStatus.socketAddress)
            assertFalse(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertTrue(it.features.isEmpty())
            assertFalse(it.completed)
            assertNull(it.exception)
        }

        localProperties = localProperties.copy(abi = "x86")
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10 && it.abi == "x86"
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertEquals("Bar", it.packageName)
            assertEquals(12, it.userId)
            assertEquals("vm", it.vmIdentifier)
            assertEquals("x86", it.abi)
            assertNull(it.jvmFlags)
            @Suppress("DEPRECATION")
            assertFalse(it.isNativeDebuggable)
            assertFalse(it.waitCommandReceived)
            assertFalse(it.isWaitingForDebugger)
            assertNull(it.jdwpSessionProxyStatus.socketAddress)
            assertFalse(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertTrue(it.features.isEmpty())
            assertFalse(it.completed)
            assertNull(it.exception)
        }

        localProperties = localProperties.copy(jvmFlags = "FooBar")
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10 && it.jvmFlags == "FooBar"
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertEquals("Bar", it.packageName)
            assertEquals(12, it.userId)
            assertEquals("vm", it.vmIdentifier)
            assertEquals("x86", it.abi)
            assertEquals("FooBar", it.jvmFlags)
            @Suppress("DEPRECATION")
            assertFalse(it.isNativeDebuggable)
            assertFalse(it.waitCommandReceived)
            assertFalse(it.isWaitingForDebugger)
            assertNull(it.jdwpSessionProxyStatus.socketAddress)
            assertFalse(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertTrue(it.features.isEmpty())
            assertFalse(it.completed)
            assertNull(it.exception)
        }

        localProperties = localProperties.copy(isNativeDebuggable = true)
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            @Suppress("DEPRECATION")
            it.pid == 10 && it.isNativeDebuggable
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertEquals("Bar", it.packageName)
            assertEquals(12, it.userId)
            assertEquals("vm", it.vmIdentifier)
            assertEquals("x86", it.abi)
            assertEquals("FooBar", it.jvmFlags)
            @Suppress("DEPRECATION")
            assertTrue(it.isNativeDebuggable)
            assertFalse(it.waitCommandReceived)
            assertFalse(it.isWaitingForDebugger)
            assertNull(it.jdwpSessionProxyStatus.socketAddress)
            assertFalse(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertTrue(it.features.isEmpty())
            assertFalse(it.completed)
            assertNull(it.exception)
        }

        localProperties = localProperties.copy(waitCommandReceived = true)
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10 && it.waitCommandReceived
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertEquals("Bar", it.packageName)
            assertEquals(12, it.userId)
            assertEquals("vm", it.vmIdentifier)
            assertEquals("x86", it.abi)
            assertEquals("FooBar", it.jvmFlags)
            @Suppress("DEPRECATION")
            assertTrue(it.isNativeDebuggable)
            assertTrue(it.waitCommandReceived)
            assertFalse(it.isWaitingForDebugger)
            assertNull(it.jdwpSessionProxyStatus.socketAddress)
            assertFalse(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertTrue(it.features.isEmpty())
            assertFalse(it.completed)
            assertNull(it.exception)
        }

        localProperties = localProperties.copy(isWaitingForDebugger = true)
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10 && it.isWaitingForDebugger
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertEquals("Bar", it.packageName)
            assertEquals(12, it.userId)
            assertEquals("vm", it.vmIdentifier)
            assertEquals("x86", it.abi)
            assertEquals("FooBar", it.jvmFlags)
            @Suppress("DEPRECATION")
            assertTrue(it.isNativeDebuggable)
            assertTrue(it.waitCommandReceived)
            assertTrue(it.isWaitingForDebugger)
            assertFalse(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertNull(it.jdwpSessionProxyStatus.socketAddress)
            assertTrue(it.features.isEmpty())
            assertFalse(it.completed)
            assertNull(it.exception)
        }

        localProperties = localProperties.copy(
            jdwpSessionProxyStatus = localProperties.jdwpSessionProxyStatus.copy(
                isExternalDebuggerAttached = true
            )
        )
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10 && it.jdwpSessionProxyStatus.isExternalDebuggerAttached
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertEquals("Bar", it.packageName)
            assertEquals(12, it.userId)
            assertEquals("vm", it.vmIdentifier)
            assertEquals("x86", it.abi)
            assertEquals("FooBar", it.jvmFlags)
            @Suppress("DEPRECATION")
            assertTrue(it.isNativeDebuggable)
            assertTrue(it.waitCommandReceived)
            assertTrue(it.isWaitingForDebugger)
            assertTrue(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertNull(it.jdwpSessionProxyStatus.socketAddress)
            assertTrue(it.features.isEmpty())
            assertFalse(it.completed)
            assertNull(it.exception)
        }

        val proxyAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 200)
        localProperties = localProperties.copy(
            jdwpSessionProxyStatus = localProperties.jdwpSessionProxyStatus.copy(
                socketAddress = proxyAddress
            )
        )
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10 && it.jdwpSessionProxyStatus.socketAddress == proxyAddress
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertEquals("Bar", it.packageName)
            assertEquals(12, it.userId)
            assertEquals("vm", it.vmIdentifier)
            assertEquals("x86", it.abi)
            assertEquals("FooBar", it.jvmFlags)
            @Suppress("DEPRECATION")
            assertTrue(it.isNativeDebuggable)
            assertTrue(it.waitCommandReceived)
            assertTrue(it.isWaitingForDebugger)
            assertTrue(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertEquals(proxyAddress, it.jdwpSessionProxyStatus.socketAddress)
            assertTrue(it.features.isEmpty())
            assertFalse(it.completed)
            assertNull(it.exception)
        }

        localProperties = localProperties.copy(features = listOf("f1", "f2", "f3"))
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10 && it.features == listOf("f1", "f2", "f3")
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertEquals("Bar", it.packageName)
            assertEquals(12, it.userId)
            assertEquals("vm", it.vmIdentifier)
            assertEquals("x86", it.abi)
            assertEquals("FooBar", it.jvmFlags)
            @Suppress("DEPRECATION")
            assertTrue(it.isNativeDebuggable)
            assertTrue(it.waitCommandReceived)
            assertTrue(it.isWaitingForDebugger)
            assertTrue(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertEquals(proxyAddress, it.jdwpSessionProxyStatus.socketAddress)
            assertEquals(listOf("f1", "f2", "f3"), it.features)
            assertFalse(it.completed)
            assertNull(it.exception)
        }

        localProperties = localProperties.copy(completed = true)
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10 && it.completed
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertEquals("Bar", it.packageName)
            assertEquals(12, it.userId)
            assertEquals("vm", it.vmIdentifier)
            assertEquals("x86", it.abi)
            assertEquals("FooBar", it.jvmFlags)
            @Suppress("DEPRECATION")
            assertTrue(it.isNativeDebuggable)
            assertTrue(it.waitCommandReceived)
            assertTrue(it.isWaitingForDebugger)
            assertTrue(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertEquals(proxyAddress, it.jdwpSessionProxyStatus.socketAddress)
            assertEquals(listOf("f1", "f2", "f3"), it.features)
            assertTrue(it.completed)
            assertNull(it.exception)
        }

        localProperties = localProperties.copy(exception = Exception("Message"))
        sendAndWaitForUpdate(serverConnections.first(), devices.first(), processListFlows, localProperties) {
            it.pid == 10 && it.exception?.message == "Message"
        }.also {
            assertEquals(10, it.pid)
            assertEquals("Foo", it.processName)
            assertEquals("Bar", it.packageName)
            assertEquals(12, it.userId)
            assertEquals("vm", it.vmIdentifier)
            assertEquals("x86", it.abi)
            assertEquals("FooBar", it.jvmFlags)
            @Suppress("DEPRECATION")
            assertTrue(it.isNativeDebuggable)
            assertTrue(it.waitCommandReceived)
            assertTrue(it.isWaitingForDebugger)
            assertTrue(it.jdwpSessionProxyStatus.isExternalDebuggerAttached)
            assertEquals(proxyAddress, it.jdwpSessionProxyStatus.socketAddress)
            assertEquals(listOf("f1", "f2", "f3"), it.features)
            assertTrue(it.completed)
            assertEquals("Message", it.exception?.message)
        }

        collectorJobs.forEach {
            it.cancel("Cancellation from test")
            it.join()
        }

        // Assert
        processListFlows.map { it.value }.forEach { list ->
            assertEquals(1, list.size)
            assertEquals(10, list.first().pid)
            assertEquals("Foo", list.first().processName)
            assertEquals("Bar", list.first().packageName)
        }
    }

    private suspend fun sendAndWaitForUpdate(
        serverConnection: ProcessInventoryServerConnection,
        device: ConnectedDevice,
        processListFlows: List<MutableStateFlow<List<JdwpProcessProperties>>>,
        localProperties: JdwpProcessProperties,
        predicate: (JdwpProcessProperties) -> Boolean
    ): JdwpProcessProperties {
        serverConnection.withConnectionForDevice(device) {
            sendProcessProperties(localProperties)
        }

        return waitNonNull {
            // Check all state flow contain a `JdwpProperties` instance that match the predicate
            val allDone = processListFlows.map { stateFlow ->
                stateFlow.value
            }.all { propertiesList ->
                propertiesList.firstOrNull {
                    predicate(it)
                } != null
            }
            if (!allDone) {
                null
            } else {
                // Return the first one (this should always succeed given `allDone` is `true`)
                processListFlows.first().value.first { predicate(it) }
            }
        }
    }

    private fun createServerConnection(session: AdbSession): ProcessInventoryServerConnection {
        val config = object : ProcessInventoryServerConfiguration {
            override val clientDescription: String = "foo"
            override val serverDescription: String = "bar"
        }
        return registerCloseable(ProcessInventoryServerConnection.create(session, config))
    }

    private suspend fun findFreeTcpPort(): Int {
        val session = registerCloseable(FakeAdbSession())
        val freePort = session.channelFactory.createServerSocket().use {
            it.bind(InetSocketAddress(0)).port
        }
        return freePort
    }
}
