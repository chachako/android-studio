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
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests for [ProcessTrackerFactoryDdmlib]
 */
class ProcessTrackerFactoryDdmlibTest {

    private val adbSession = AdbSession.create(AdbSessionHost())
    private val adbAdapter = FakeAdbAdapter()
    private val logger = FakeAdbLoggerFactory().logger

    @Test
    fun providesData(): Unit = runBlocking {
        val device = mockDevice("device1", apiLevel = 25, abi = "x86")
        val factory = ProcessTrackerFactoryDdmlib(adbSession, adbAdapter, null, logger)

        assertThat(factory.getDeviceSerialNumber(device)).isEqualTo("device1")
        assertThat(factory.getDeviceApiLevel(device)).isEqualTo(25)
        assertThat(factory.getDeviceAbi(device)).isEqualTo("x86")
        assertThat(factory.createProcessTracker(device)).isInstanceOf(ClientProcessTracker::class.java)
    }
}
