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

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.tools.debugging.JdwpProcessProfiler
import com.android.adblib.tools.debugging.ProfilerStatus
import com.android.adblib.tools.debugging.toByteArray
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.FakeJdwpCommandProgress
import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.ProfilerState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class JdwpProcessProfilerTest : AdbLibToolsTestBase() {

    @Test
    fun queryStatusWorksForOff() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val profiler = createJdwpProcessProfiler(fakeAdb)
        fakeAdb.device("1234").client(10).profilerState.status = ProfilerState.Status.Off
        val progress = FakeJdwpCommandProgress()

        // Act
        val status = profiler.queryStatus(progress)

        // Assert
        assertEquals(ProfilerStatus.Off, status)
        assertTrue(progress.beforeSendIsCalled)
        assertTrue(progress.afterSendIsCalled)
        assertTrue(progress.onReplyIsCalled)
        assertFalse(progress.onReplyTimeoutIsCalled)
    }

    @Test
    fun queryStatusWorksForInstrumentation() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val profiler = createJdwpProcessProfiler(fakeAdb)
        fakeAdb.device("1234").client(10).profilerState.status =
            ProfilerState.Status.Instrumentation

        // Act
        val status = profiler.queryStatus()

        // Assert
        assertEquals(ProfilerStatus.InstrumentationProfilerRunning, status)
    }

    @Test
    fun queryStatusWorksForSampling() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val profiler = createJdwpProcessProfiler(fakeAdb)
        fakeAdb.device("1234").client(10).profilerState.status = ProfilerState.Status.Sampling

        // Act
        val status = profiler.queryStatus()

        // Assert
        assertEquals(ProfilerStatus.SamplingProfilerRunning, status)
    }

    @Test
    fun startSampleProfilingWorks() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val profiler = createJdwpProcessProfiler(fakeAdb)
        val profilerState = fakeAdb.device("1234").client(10).profilerState
        val progress = FakeJdwpCommandProgress()

        // Act
        profiler.startSampleProfiling(
            interval = 2,
            intervalUnit = TimeUnit.MILLISECONDS,
            bufferSize = 8_192,
            progress = progress
        )

        // Assert
        assertEquals(ProfilerState.Status.Sampling, profilerState.status)
        assertEquals(8_192, profilerState.samplingData.bufferSize)
        assertEquals(0, profilerState.samplingData.flags)
        assertEquals(2_000, profilerState.samplingData.intervalMicros)

        assertTrue(progress.beforeSendIsCalled)
        assertTrue(progress.afterSendIsCalled)
        assertFalse(progress.onReplyIsCalled)
        assertTrue(progress.onReplyTimeoutIsCalled)
    }

    @Test
    fun stopSampleProfilingWorks() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val profiler = createJdwpProcessProfiler(fakeAdb)
        val samplingData = "This is a test".toByteArray(Charsets.UTF_8)
        val profilerState = fakeAdb.device("1234").client(10).profilerState
        profilerState.status = ProfilerState.Status.Sampling
        profilerState.samplingData.bytes = samplingData
        val progress = FakeJdwpCommandProgress()

        // Act
        val samplingResult = profiler.stopSampleProfiling(progress) { data, dataLength ->
            data.toByteArray(dataLength)
        }

        // Assert
        assertEquals(ProfilerState.Status.Off, profilerState.status)
        assertArrayEquals(samplingData, samplingResult)

        assertTrue(progress.beforeSendIsCalled)
        assertTrue(progress.afterSendIsCalled)
        assertFalse(progress.onReplyIsCalled)
        assertTrue(progress.onReplyTimeoutIsCalled)
    }

    private suspend fun createJdwpProcessProfiler(fakeAdb: FakeAdbServerProvider): JdwpProcessProfiler {
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
        val session = createHostServices(fakeAdb).session
        val connectedDevice = waitForOnlineConnectedDevice(session, fakeDevice.deviceId)
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val process = registerCloseable(JdwpProcessImpl(session, connectedDevice, 10))
        // Note: We don't currently need to collect process properties for the profiler API to
        // work, so we don't call "process.startMonitoring()" here
        return JdwpProcessProfilerImpl(process)
    }

    fun DeviceState.client(pid: Int): ClientState {
        return getClient(pid)
            ?: throw IllegalArgumentException("Client $pid does not exist on device ${this.deviceId}")
    }
}
