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
package com.android.processmonitor.monitor.ddmlib

import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.processmonitor.agenttracker.AgentProcessTracker
import com.android.processmonitor.agenttracker.AgentProcessTrackerConfig
import com.android.processmonitor.monitor.MergedProcessTracker
import com.android.testutils.TestResources
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [com.android.processmonitor.monitor.ddmlib.ProcessTrackerFactoryDdmlib]
 */
class ProcessTrackerFactoryDdmlibTest {

    private val adbSession = AdbSession.create(AdbSessionHost())
    private val adbAdapter = FakeAdbAdapter()
    private val trackerAgentPath = TestResources.getDirectory("/agent").toPath()
    private val trackerAgentInterval = 1000
    private val logger = FakeAdbLoggerFactory().logger

    @Test
    fun withAgentConfig_usesAgentProcessTracker() {
        val factory =
            ProcessTrackerFactoryDdmlib(
                adbSession,
                adbAdapter,
                AgentProcessTrackerConfig(trackerAgentPath, trackerAgentInterval),
                logger
            )

        val tracker = factory.createProcessTracker(mockDevice("device1")) as? MergedProcessTracker
        assertThat(tracker?.trackers?.map { it::class })
            .containsExactly(
                ClientProcessTracker::class,
                AgentProcessTracker::class,
            )
    }

    @Test
    fun withoutAgentConfig_doesNotUseAgentProcessTracker() {
        val factory =
            ProcessTrackerFactoryDdmlib(
                adbSession,
                adbAdapter,
                agentConfig = null,
                logger
            )

        val tracker = factory.createProcessTracker(mockDevice("device1"))

        assertThat(tracker).isInstanceOf(ClientProcessTracker::class.java)
    }
}
