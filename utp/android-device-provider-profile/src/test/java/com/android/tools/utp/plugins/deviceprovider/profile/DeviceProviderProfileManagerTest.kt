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

package com.android.tools.utp.plugins.deviceprovider.profile

import com.android.tools.utp.plugins.deviceprovider.profile.proto.DeviceProviderProfileProto
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File
import java.time.Clock
import java.time.Instant

class DeviceProviderProfileManagerTest {

    @get:Rule
    val mockito: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Mock
    lateinit var mockClock: Clock

    lateinit var outputFolder: File
    lateinit var profileManager: DeviceProviderProfileManager

    @Before
    fun setup() {
        outputFolder = temporaryFolder.newFolder()
        profileManager = DeviceProviderProfileManager.forOutputDirectory(
            outputFolder.toString()
        )
        DeviceProviderProfileManager.clock = mockClock
    }

    private fun getProfilingFile(): File =
        outputFolder.resolve("profiling/device_provider_profile.pb")

    @Test
    fun testRecordProvision() {
        `when`(mockClock.instant()).thenReturn(
            Instant.ofEpochMilli(100),
            Instant.ofEpochMilli(2000))

        profileManager.recordDeviceProvision {}

        val profile = getProfilingFile()
        assertThat(profile.exists()).isTrue()

        profile.inputStream().use { file ->
            val profileProto = DeviceProviderProfileProto.DeviceProviderProfile.parseFrom(file)
            assertThat(profileProto.deviceProvision.spanBeginMs).isEqualTo(100)
            assertThat(profileProto.deviceProvision.spanEndMs).isEqualTo(2000)

            assertThat(profileProto.deviceRelease.spanBeginMs).isEqualTo(0)
            assertThat(profileProto.deviceRelease.spanEndMs).isEqualTo(0)
        }
    }

    @Test
    fun testRecordProvisionAndRelease() {
        `when`(mockClock.instant()).thenReturn(
            Instant.ofEpochMilli(100),
            Instant.ofEpochMilli(2000),
            Instant.ofEpochMilli(5000),
            Instant.ofEpochMilli(5010))

        profileManager.recordDeviceProvision {}

        profileManager.recordDeviceRelease {}


        val profile = getProfilingFile()
        assertThat(profile.exists()).isTrue()

        profile.inputStream().use { file ->
            val profileProto = DeviceProviderProfileProto.DeviceProviderProfile.parseFrom(file)
            assertThat(profileProto.deviceProvision.spanBeginMs).isEqualTo(100)
            assertThat(profileProto.deviceProvision.spanEndMs).isEqualTo(2000)

            assertThat(profileProto.deviceRelease.spanBeginMs).isEqualTo(5000)
            assertThat(profileProto.deviceRelease.spanEndMs).isEqualTo(5010)
        }
    }
}
