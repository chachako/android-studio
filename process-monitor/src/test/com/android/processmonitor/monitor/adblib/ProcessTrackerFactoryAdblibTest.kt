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
package com.android.processmonitor.monitor.adblib

import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceState.ONLINE
import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.devices.Abi
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests for [com.android.processmonitor.monitor.adblib.ProcessTrackerFactoryAdblib]
 */
class ProcessTrackerFactoryAdblibTest {

    private val adbSession = AdbSession.create(AdbSessionHost())
    private val logger = FakeAdbLoggerFactory().logger

    @Test
    fun providesData(): Unit = runBlocking {
        val device = DeviceState.Connected(deviceProperties(25, Abi.X86), mockDevice("device1"))
        val factory = ProcessTrackerFactoryAdblib(adbSession, null, logger)

        assertThat(factory.getDeviceSerialNumber(device)).isEqualTo("device1")
        assertThat(factory.getDeviceApiLevel(device)).isEqualTo(25)
        assertThat(factory.getDeviceAbi(device)).isEqualTo("x86")
        assertThat(factory.createProcessTracker(device)).isInstanceOf(JdwpProcessTracker::class.java)
    }
}

@Suppress("SameParameterValue")
private fun deviceProperties(aplLevel: Int, abi: Abi) =
    DeviceProperties.build {
        androidVersion = AndroidVersion(aplLevel)
        this.abi = abi
    }

@Suppress("SameParameterValue")
private fun mockDevice(serialNumber: String): ConnectedDevice = mock<ConnectedDevice>().apply {
    whenever(deviceInfoFlow).thenReturn(
        MutableStateFlow(DeviceInfo(serialNumber, ONLINE))
    )
}
