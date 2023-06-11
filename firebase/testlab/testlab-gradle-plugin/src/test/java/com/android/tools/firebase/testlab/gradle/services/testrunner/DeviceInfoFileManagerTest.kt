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

package com.android.tools.firebase.testlab.gradle.services.testrunner

import com.android.tools.utp.plugins.host.device.info.proto.AndroidTestDeviceInfoProto.AndroidTestDeviceInfo
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

class DeviceInfoFileManagerTest {

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    lateinit var resultsOutDir: File

    @Mock
    lateinit var device: TestDeviceData

    @Before
    fun setup() {
        `when`(device.name).thenReturn("device_name")
        `when`(device.apiLevel).thenReturn(33)
        `when`(device.deviceId).thenReturn("b0q")

        resultsOutDir = temporaryFolderRule.newFolder("results")
    }

    @Test
    fun test_createFile() {
        val result = DeviceInfoFileManager().createFile(resultsOutDir, device)

        assertThat(result.absolutePath).isEqualTo(
            resultsOutDir.resolve("device-info.pb").absolutePath)

        result.inputStream().use {
            AndroidTestDeviceInfo.parseFrom(it)
        }.apply {
            assertThat(name).isEqualTo("device_name")
            assertThat(apiLevel).isEqualTo("33")
            assertThat(gradleDslDeviceName).isEqualTo("device_name")
            assertThat(model).isEqualTo("b0q")
        }
    }


}
