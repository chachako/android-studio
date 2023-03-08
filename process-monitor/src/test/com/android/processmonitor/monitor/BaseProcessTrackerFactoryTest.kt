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

import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.processmonitor.agenttracker.AgentProcessTracker
import com.android.processmonitor.agenttracker.AgentProcessTrackerConfig
import com.android.processmonitor.common.FakeProcessTracker
import com.android.processmonitor.common.ProcessTracker
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.nio.file.Path

/**
 * Tests for [com.android.processmonitor.monitor.BaseProcessTrackerFactory]
 */
class BaseProcessTrackerFactoryTest {

    @Test
    fun withAgentConfig_usesAgentProcessTracker(): Unit = runBlocking {
        val factory = TestBaseProcessTrackerFactory(AgentProcessTrackerConfig(Path.of("agent"), 1))
        val device = TestDevice("device1", 30, "x86")

        val tracker = factory.createProcessTracker(device) as? MergedProcessTracker
        assertThat(tracker?.trackers?.map { it::class })
            .containsExactly(
                FakeProcessTracker::class,
                AgentProcessTracker::class,
            )
    }

    @Test
    fun withoutAgentConfig_doesNotUseAgentProcessTracker(): Unit = runBlocking {
        val factory = TestBaseProcessTrackerFactory(agentConfig = null)
        val device = TestDevice("device1", 30, "x86")

        val tracker = factory.createProcessTracker(device)

        assertThat(tracker).isInstanceOf(FakeProcessTracker::class.java)
    }

    @Test
    fun withApiUnder21_doesNotUseAgentProcessTracker(): Unit = runBlocking {
        val factory = TestBaseProcessTrackerFactory(agentConfig = null)
        val device = TestDevice("device1", 20, "x86")

        val tracker = factory.createProcessTracker(device)

        assertThat(tracker).isInstanceOf(FakeProcessTracker::class.java)
    }

    @Test
    fun withoutAbi_doesNotUseAgentProcessTracker(): Unit = runBlocking {
        val factory = TestBaseProcessTrackerFactory(agentConfig = null)
        val device = TestDevice("device1", 33, null)

        val tracker = factory.createProcessTracker(device)

        assertThat(tracker).isInstanceOf(FakeProcessTracker::class.java)
    }

    private class TestDevice(val serialNumber: String, val apiLevel: Int, val abi: String?)

    private inner class TestBaseProcessTrackerFactory(agentConfig: AgentProcessTrackerConfig?) :
        BaseProcessTrackerFactory<TestDevice>(
            AdbSession.create(AdbSessionHost()),
            agentConfig,
            FakeAdbLoggerFactory().logger
        ) {

        override fun createMainTracker(device: TestDevice): ProcessTracker = FakeProcessTracker()

        override suspend fun getDeviceApiLevel(device: TestDevice): Int = device.apiLevel

        override suspend fun getDeviceAbi(device: TestDevice): String? = device.abi

        override fun getDeviceSerialNumber(device: TestDevice): String = device.serialNumber
    }
}
