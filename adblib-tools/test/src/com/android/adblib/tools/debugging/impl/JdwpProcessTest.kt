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

import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.tools.AdbLibToolsProperties
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.packets.JdwpCommands
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.clone
import com.android.adblib.tools.debugging.packets.payloadLength
import com.android.adblib.tools.debugging.properties
import com.android.adblib.tools.debugging.toByteArray
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant

class JdwpProcessTest : AdbLibToolsTestBase() {

    @Test
    fun basicStuffWorks() = runBlockingWithTimeout {
        // Prepare
        val (_, device, process) = createJdwpProcess()

        // Act
        val key = object : CoroutineScopeCache.Key<Int>("foo") {}
        val cachedValue = process.cache.getOrPut(key) { 11 }
        val cachedValue2 = process.cache.getOrPutSuspending(key) { 12 }

        // Assert
        assertEquals(10, process.pid)
        assertSame(device, process.device)
        assertTrue(process.scope.isActive)
        assertEquals(11, cachedValue)
        assertEquals(12, cachedValue2)
    }

    @Test
    fun withJdwpSessionWorks() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess()

        // Act
        val reply = process.withJdwpSession {
            val versionCommand = MutableJdwpPacket.createCommandPacket(
                nextPacketId(),
                JdwpCommands.CmdSet.SET_VM.value,
                JdwpCommands.VmCmd.CMD_VM_VERSION.value,
                ByteBuffer.allocate(0)
            )
            newPacketReceiver()
                .onActivation {
                    sendPacket(versionCommand)
                }.flow()
                .map { reply -> reply.clone() }
                .first { reply -> reply.id == versionCommand.id }
        }

        // Assert
        assertEquals(true, reply.isReply)
        assertEquals(42, reply.payloadLength)
        assertEquals(42, reply.payload.toByteArray(reply.payloadLength).size)
    }

    @Test
    fun propertiesFlowInitialStateIsEmpty() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess()

        // Act
        val properties = process.propertiesFlow.value
        val properties2 = process.properties

        // Assert
        assertSame(properties, properties2)
        assertProcessPropertiesIsSkeleton(properties)
    }

    @Test
    fun startMonitoringWorks() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess()
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(2)
        )

        // Act
        process.startMonitoring()
        yieldUntil { process.properties.completed }

        // Assert
        val properties = process.properties
        assertProcessPropertiesComplete(properties)
    }


    @Suppress("unused")
    //@Test // Disabled because of b/271466829
    fun startMonitoringEndsEarlyIfWaitForDebugger() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess(waitForDebugger = true)

        // Act:
        // When the AndroidVM sends a "WAIT" packet, collecting the process properties
        // should complete fast, i.e. in a much shorter time than the max. timeout
        // specified in PROCESS_PROPERTIES_READ_TIMEOUT
        val longTimeout = Duration.ofSeconds(50)
        val shortTimeout = Duration.ofSeconds(15)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            longTimeout
        )
        process.startMonitoring()
        yieldUntil(shortTimeout) { process.properties.completed }

        // Assert
        val properties = process.properties
        assertProcessPropertiesComplete(properties)
    }

    @Test
    fun startMonitoringDoesNotEndEarlyIfWaitForDebugger() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess(waitForDebugger = true)

        // Act:
        // Even if the AndroidVM sends a "WAIT" packet, collecting the process properties
        // should not complete until the timeout is reached
        val timeout = Duration.ofSeconds(2)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            timeout
        )
        val start = Instant.now()
        process.startMonitoring()
        yieldUntil { process.properties.completed }
        val waitTime = Duration.between(start, Instant.now())

        // Assert: We waited (close to) "timeout" for "completed" to get set
        assertTrue(waitTime >= timeout.dividedBy(2))
    }

    @Test
    fun startMonitoringNeverEndsIfLongTimeoutAndNoWaitPacket() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess(waitForDebugger = false)

        // Act:
        // When the AndroidVM does not send a "WAIT" packet, collecting the process properties
        // never completes before the timeout specified in PROCESS_PROPERTIES_READ_TIMEOUT
        val longTimeout = Duration.ofSeconds(50)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            longTimeout
        )
        process.startMonitoring()
        yieldUntil {
            process.properties.processName != null &&
                    process.properties.features.isNotEmpty()
        }

        // Assert
        val properties = process.properties
        assertNull(properties.exception)
        assertFalse(properties.completed)
    }

    @Test
    fun startMonitoringDoesRetryWhenProcessNotAvailable() = runBlockingWithTimeout {
        // Prepare
        val (_, _, firstProcess) = createJdwpProcess(waitForDebugger = false)
        setHostPropertyValue(
            firstProcess.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(50)
        )
        firstProcess.startMonitoring() // collect properties for a very long time (50 seconds)
        yieldUntil { firstProcess.properties.processName != null }

        // Act: Set short timeout and observe we get a timeout exception
        // Also set a very short retry timeout, so that we retry when the first
        // process is closed
        setHostPropertyValue(
            firstProcess.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofMillis(100)
        )
        setHostPropertyValue(
            firstProcess.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_RETRY_DURATION,
            Duration.ofMillis(10)
        )
        val process =
            registerCloseable(
                JdwpProcessImpl(
                    firstProcess.device.session,
                    firstProcess.device,
                    firstProcess.pid
                )
            )

        // This will time out and retry because there is another process collecting properties
        process.startMonitoring()

        // Close the other process so that it frees up the JDWP session
        launch {
            delay(1_000)
            firstProcess.close()
        }

        yieldUntil { process.properties.completed }

        // Assert
        val properties = process.properties
        assertProcessPropertiesComplete(properties)
    }

    @Test
    fun startMonitoringStopsOnEOF() = runBlockingWithTimeout {
        // Prepare
        val (fakeAdb, _, process) = createJdwpProcess(waitForDebugger = false)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(50)
        )
        process.startMonitoring() // collect properties for a very long time (50 seconds)

        // Act: Stop process before timeout expires (they will send EOF to
        // the process properties collector)
        fakeAdb.device(process.device.serialNumber).stopClient(process.pid)
        delay(500)

        // Assert
        val properties = process.properties
        assertFalse(properties.completed)
    }

    @Test
    fun closeClearsCacheAndCancelsScope() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess()

        // Launch a long job
        val job = process.scope.launch { delay(50_000) }
        // Add cache entries
        val key1 = CoroutineScopeCache.Key<Int>("key1")
        process.cache.getOrPut(key1) { 5 }
        val key2 = CoroutineScopeCache.Key<Int>("key2")
        process.cache.getOrPut(key2) { 10 }

        // Act
        process.close()

        // Assert
        assertTrue(job.isCancelled)
        assertEquals(1_000, process.cache.getOrPut(key1) { 1_000 })
        assertEquals(2_000, process.cache.getOrPut(key2) { 2_000 })
    }

    private suspend fun createJdwpProcess(
        deviceApi: Int = 30,
        waitForDebugger: Boolean = true
    ): Triple<FakeAdbServerProvider, ConnectedDevice, JdwpProcessImpl> {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, deviceApi)
        val session = createSession(fakeAdb)
        val device = waitForOnlineConnectedDevice(session, fakeDevice.deviceId)
        fakeAdb.device(fakeDevice.deviceId).startClient(10, 2, "p1", "pkg", waitForDebugger)
        val process = registerCloseable(JdwpProcessImpl(session, device, 10))
        return Triple(fakeAdb, device, process)
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

    private fun assertProcessPropertiesIsSkeleton(properties: JdwpProcessProperties) {
        assertEquals(10, properties.pid)
        assertNull(properties.processName)
        assertNull(properties.userId)
        assertNull(properties.packageName)
        assertNull(properties.vmIdentifier)
        assertNull(properties.abi)
        assertNull(properties.jvmFlags)
        @Suppress("DEPRECATION")
        assertFalse(properties.isNativeDebuggable)
        assertFalse(properties.jdwpSessionProxyStatus.isExternalDebuggerAttached)
        assertNull(properties.jdwpSessionProxyStatus.socketAddress)
        assertTrue(properties.features.isEmpty())
        assertNull(properties.exception)
        assertFalse(properties.completed)
    }
}
