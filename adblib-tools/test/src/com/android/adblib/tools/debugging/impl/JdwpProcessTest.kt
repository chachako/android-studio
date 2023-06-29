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
import com.android.adblib.tools.debugging.packets.payloadLength
import com.android.adblib.tools.debugging.packets.withPayload
import com.android.adblib.tools.debugging.properties
import com.android.adblib.tools.debugging.toByteArray
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import com.android.fakeadbserver.AppStage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
                .first { reply -> reply.id == versionCommand.id }
        }

        // Assert
        assertEquals(true, reply.isReply)
        assertEquals(42, reply.payloadLength)
        assertEquals(42, reply.withPayload { it.toByteArray(reply.payloadLength).size })
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

    @Test
    fun startMonitoringUsesDefaultDelayByDefault() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess()
        val delay = Duration.ofSeconds(2)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_DEFAULT,
            delay
        )
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_SHORT,
            Duration.ofSeconds(0)
        )

        // Act
        val start = Instant.now()
        process.startMonitoring()
        yieldUntil { process.properties.processName != null }
        val waitTime = Duration.between(start, Instant.now())

        // Assert: We waited (close to) "delay" for "completed" to get set
        assertTrue(timeoutExceeded(waitTime, delay))
    }

    @Test
    fun startMonitoringUsesShortDelayAccordingToUseShortDelayPropertyValue() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess()
        val delay = Duration.ofSeconds(2)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_DEFAULT,
            Duration.ofSeconds(0)
        )
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_SHORT,
            delay
        )
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT,
            true
        )

        // Act
        val start = Instant.now()
        process.startMonitoring()
        yieldUntil { process.properties.processName != null }
        val waitTime = Duration.between(start, Instant.now())

        // Assert: We waited (close to) "delay" for "completed" to get set
        assertTrue(timeoutExceeded(waitTime, delay))
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

    /**
     * Regression test for [b/271466829](https://issuetracker.google.com/issues/271466829)
     */
    @Test
    fun startMonitoringDoesNotReleaseJdwpSessionIfWaitForDebugger() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess(waitForDebugger = true)

        // Act: When the AndroidVM sends a "WAIT" packet, "completed" is set early,
        // but the SharedJDWPSession should be retained
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(60) // long timeout
        )
        process.startMonitoring()
        yieldUntil { process.properties.completed }
        delay(500) // give JDWP session holder time to launch

        // Assert: The JDWP session should still be in-use, since we received a `WAIT` packet
        assertTrue(process.isJdwpSessionRetained)
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

    @Test
    fun startMonitoringDoesNotReleaseJdwpSessionIfAppBootStageIsWaitForDebugger() = runBlockingWithTimeout {
        // Prepare
        val (fakeAdb, _, process) = createJdwpProcess(deviceApi = 34, waitForDebugger = false)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.SUPPORT_STAG_PACKETS,
            true
        )
        val clientState = fakeAdb.device(process.device.serialNumber).getClient(process.pid)!!
        clientState.setStage(AppStage.DEBG)

        // Act: When the AndroidVM app boot stage is "DEBG", "completed" is set early,
        // but the SharedJDWPSession should be retained
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(60) // long timeout
        )
        process.startMonitoring()
        yieldUntil { process.properties.completed }
        delay(500) // give JDWP session holder time to launch

        // Assert: The JDWP session should still be in-use, since app boot stage is `DEBG`
        assertTrue(process.isJdwpSessionRetained)
        assertTrue(process.properties.isWaitingForDebugger)
        assertEquals(AppStage.DEBG.value, process.properties.stage?.value)
    }

    @Test
    fun startMonitoringReleasesJdwpSessionIfAppBootStageIsA_go() = runBlockingWithTimeout {
        // Prepare
        val (fakeAdb, _, process) = createJdwpProcess(deviceApi = 34, waitForDebugger = false)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.SUPPORT_STAG_PACKETS,
            true
        )
        val clientState = fakeAdb.device(process.device.serialNumber).getClient(process.pid)!!
        clientState.setStage(AppStage.A_GO)

        // Act: When the AndroidVM app boot stage is "A_GO", "completed" is set early.
        // SharedJDWPSession should be released.
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(60) // long timeout
        )
        process.startMonitoring()
        yieldUntil { process.properties.completed }
        delay(500) // give JDWP session holder time to launch

        // Assert: We don't keep JDWP session since app boot stage was set to `A_GO`
        assertFalse(process.isJdwpSessionRetained)
        assertFalse(process.properties.isWaitingForDebugger)
        assertEquals(AppStage.A_GO.value, process.properties.stage?.value)
    }

    @Test
    fun startMonitoringNeverEndsIfLongTimeoutAndAppBootStageIsNamd() = runBlockingWithTimeout {
        // Prepare
        val (fakeAdb, _, process) = createJdwpProcess(deviceApi = 34, waitForDebugger = false)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.SUPPORT_STAG_PACKETS,
            true
        )
        val clientState = fakeAdb.device(process.device.serialNumber).getClient(process.pid)!!
        clientState.setStage(AppStage.NAMD)

        // Act
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(50) // long timeout
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
        assertFalse(properties.isWaitingForDebugger)
        assertEquals(AppStage.NAMD.value, process.properties.stage?.value)
    }

    @Test
    fun startMonitoringWaitsUntilAppStageA_go() = runBlockingWithTimeout {
        // Prepare
        val (fakeAdb, _, process) = createJdwpProcess(deviceApi = 34, waitForDebugger = false)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.SUPPORT_STAG_PACKETS,
            true
        )
        val clientState = fakeAdb.device(process.device.serialNumber).getClient(process.pid)!!
        clientState.setStage(AppStage.BOOT)
        // Set up to send updated stage for transitions to AppStage.ATCH and AppStage.A_GO
        clientState.sendStagCommandAfterHelo.addAll(
            listOf(
                Duration.ofMillis(500),
                Duration.ofMillis(500)
            )
        )

        // Act: Start monitoring
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(60) // long timeout
        )
        process.startMonitoring()
        yieldUntil { process.properties.stage?.value == AppStage.BOOT.value }

        // Assert
        assertFalse(process.properties.completed)

        // Act
        clientState.setStage(AppStage.ATCH)
        yieldUntil { process.properties.stage?.value == AppStage.ATCH.value }

        // Assert
        assertFalse(process.properties.completed)

        // Act: When the stage is A_GO the collection should become completed
        clientState.setStage(AppStage.A_GO)
        yieldUntil { process.properties.completed }

        // Assert
        val properties = process.properties
        assertProcessPropertiesComplete(properties)
        assertFalse(properties.isWaitingForDebugger)
        assertEquals(AppStage.A_GO.value, process.properties.stage?.value)
    }

    @Test
    fun startMonitoringWaitsUntilAppStageDebg() = runBlockingWithTimeout {
        // Prepare (Note waitForDebugger is false since we rely only on AppStage in this test)
        val (fakeAdb, _, process) = createJdwpProcess(deviceApi = 34, waitForDebugger = false)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.SUPPORT_STAG_PACKETS,
            true
        )
        val clientState = fakeAdb.device(process.device.serialNumber).getClient(process.pid)!!
        clientState.setStage(AppStage.BOOT)
        // Set up to send updated stage for transition to AppStage.DEBG
        clientState.sendStagCommandAfterHelo.addAll(listOf(Duration.ofMillis(500)))

        // Act: Start monitoring
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(60) // long timeout
        )
        process.startMonitoring()
        yieldUntil { process.properties.stage?.value == AppStage.BOOT.value }

        // Assert
        assertFalse(process.properties.completed)

        // Act: When the stage is DEBG the collection should become completed
        clientState.setStage(AppStage.DEBG)
        yieldUntil { process.properties.completed }

        // Assert
        // The JDWP session should still be in-use, since app boot stage is `DEBG`
        assertTrue(process.isJdwpSessionRetained)
        assertEquals(AppStage.DEBG.value, process.properties.stage?.value)
        val properties = process.properties
        assertProcessPropertiesComplete(properties)
        assertTrue(properties.isWaitingForDebugger)
    }

    @Test
    fun startMonitoringIgnoresAppStageInHeloResponseIfStagPacketsNotEnabledByProperty() =
        runBlockingWithTimeout {
            // Prepare: Have a stage to be set to A_GO
            val (fakeAdb, _, process) = createJdwpProcess(deviceApi = 34)
            setHostPropertyValue(
                process.device.session.host,
                AdbLibToolsProperties.SUPPORT_STAG_PACKETS,
                false
            )
            val clientState = fakeAdb.device(process.device.serialNumber).getClient(process.pid)!!
            clientState.setStage(AppStage.A_GO)
            // Delay a WAIT command by half a second
            clientState.sendWaitCommandAfterHelo = Duration.ofMillis(500)

            // Act: Start monitoring
            setHostPropertyValue(
                process.device.session.host,
                AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
                Duration.ofSeconds(60) // long timeout
            )
            process.startMonitoring()
            yieldUntil { process.properties.processName != null }

            // Assert: Stage is ignored
            assertNull(process.properties.stage)
            assertFalse(process.properties.isWaitingForDebugger)
            assertFalse(process.properties.completed)

            // Act: Wait until properties completed
            yieldUntil { process.properties.completed }

            // Assert
            assertTrue(process.properties.isWaitingForDebugger)
            assertProcessPropertiesComplete(process.properties)
        }

    @Test
    fun startMonitoringIgnoresAppStageInStagCommandIfStagPacketsNotEnabledByProperty() =
        runBlockingWithTimeout {
            // Prepare
            val (fakeAdb, _, process) = createJdwpProcess(deviceApi = 34)
            setHostPropertyValue(
                process.device.session.host,
                AdbLibToolsProperties.SUPPORT_STAG_PACKETS,
                false
            )
            val clientState = fakeAdb.device(process.device.serialNumber).getClient(process.pid)!!
            clientState.setStage(AppStage.BOOT)
            // Delay a app stage A_GO by 250ms and WAIT command by half a second
            clientState.sendStagCommandAfterHelo.addAll(listOf(Duration.ofMillis(250)))
            clientState.sendWaitCommandAfterHelo = Duration.ofMillis(500)

            // Act: Start monitoring
            setHostPropertyValue(
                process.device.session.host,
                AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
                Duration.ofSeconds(60) // long timeout
            )
            process.startMonitoring()
            yieldUntil { process.properties.processName != null }

            // Assert: Stage is ignored
            assertFalse(process.properties.isWaitingForDebugger)
            assertFalse(process.properties.completed)

            // Prepare
            clientState.setStage(AppStage.A_GO)

            // Act: Wait until properties completed
            yieldUntil { process.properties.completed }

            // Assert: A_GO was ignored and WAIT was interpreted correctly
            assertTrue(process.properties.isWaitingForDebugger)
            assertNull(process.properties.stage)
            assertProcessPropertiesComplete(process.properties)
        }

    private suspend fun createJdwpProcess(
        deviceApi: Int = 30,
        waitForDebugger: Boolean = true
    ): Triple<FakeAdbServerProvider, ConnectedDevice, JdwpProcessImpl> {
        val fakeDevice = addFakeDevice(fakeAdb, deviceApi)
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

    private fun timeoutExceeded(waitTime: Duration, timeout: Duration): Boolean {
        // To deal with potential "corner case" of timeout expiring slightly ahead of time,
        // we assume timeout has been exceeded if the wait was 90% of the timeout.
        // Note: This works only for "short" duration, i.e. [Duration.toNanos] returns a valid value.
        val timeoutLimit = Duration.ofNanos((timeout.toNanos() * 0.9).toLong())
        return waitTime >= timeoutLimit
    }
}
