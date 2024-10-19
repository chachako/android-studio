/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.utp

import com.android.build.gradle.internal.testing.StaticTestData
import com.android.builder.testing.api.DeviceConnector
import com.android.ddmlib.MultiLineReceiver
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.junit.Before
import org.mockito.kotlin.any

/**
 * Unit tests for functions in AdditionalTestOutputUtils.kt.
 */
class AdditionalTestOutputUtilsTest {
    companion object {
        private val QUERY: String = """
            content query --uri content://media/external/file --projection _data --where "_data LIKE '%/Android'"
        """.trimIndent()
    }

    private val device: DeviceConnector = mock()
    private val managedDevice: UtpManagedDevice = mock()
    private val testData: StaticTestData = mock()

    @Before
    fun setupMocks() {
        whenever(testData.instrumentationTargetPackageId).thenReturn("testedApplicationId")
        whenever(device.executeShellCommand(eq(QUERY), any(), any(), any())).then {
            val receiver: MultiLineReceiver = it.getArgument(1)
            receiver.processNewLines(arrayOf("Row: 0 _data=/storage/emulated/0/Android"))
        }
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnDeviceWithApi15() {
        whenever(device.apiLevel).thenReturn(15)

        val dir = findAdditionalTestOutputDirectoryOnDevice(device, testData)

        assertThat(dir).isNull()
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnDeviceWhenQueryReturnsNull() {
        whenever(device.executeShellCommand(eq(QUERY), any(), any(), any())).then {
            val receiver: MultiLineReceiver = it.getArgument(1)
            receiver.processNewLines(arrayOf(""))
        }

        val dir = findAdditionalTestOutputDirectoryOnDevice(device, testData)

        assertThat(dir).isNull()
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnDeviceWithApi16() {
        whenever(device.apiLevel).thenReturn(16)

        val dir = findAdditionalTestOutputDirectoryOnDevice(device, testData)

        assertThat(dir).isEqualTo(
            "/storage/emulated/0/Android/data/testedApplicationId/files/test_data")
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnDeviceWithApi29() {
        whenever(device.apiLevel).thenReturn(29)

        val dir = findAdditionalTestOutputDirectoryOnDevice(device, testData)

        assertThat(dir).isEqualTo(
            "/sdcard/Android/media/testedApplicationId/additional_test_output")
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnManagedDeviceWithApi15() {
        whenever(managedDevice.api).thenReturn(15)

        val dir = findAdditionalTestOutputDirectoryOnManagedDevice(managedDevice, testData)

        assertThat(dir).isNull()
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnManagedDeviceWithApi16() {
        whenever(managedDevice.api).thenReturn(16)

        val dir = findAdditionalTestOutputDirectoryOnManagedDevice(managedDevice, testData)

        assertThat(dir).isNull()
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnManagedDeviceWithApi29() {
        whenever(managedDevice.api).thenReturn(29)

        val dir = findAdditionalTestOutputDirectoryOnManagedDevice(managedDevice, testData)

        assertThat(dir).isEqualTo(
            "/sdcard/Android/media/testedApplicationId/additional_test_output")
    }
}
