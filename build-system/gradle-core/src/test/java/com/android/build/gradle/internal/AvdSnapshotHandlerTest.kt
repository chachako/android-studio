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

package com.android.build.gradle.internal

import com.android.build.gradle.internal.testing.AdbHelper
import com.android.testutils.MockitoKt.any
import com.android.utils.ILogger
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import java.io.File
import java.io.InputStream

@RunWith(JUnit4::class)
class AvdSnapshotHandlerTest {

    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Mock
    lateinit var mockProcessBuilder: ProcessBuilder

    @Mock
    lateinit var mockProcess: Process

    @Mock
    lateinit var mockAdbHelper: AdbHelper

    @Mock
    lateinit var mockLogger: ILogger

    private val emulatorExec: File by lazy(LazyThreadSafetyMode.NONE) { tmpFolder.newFile() }
    private val avdDirectory: File by lazy(LazyThreadSafetyMode.NONE) { tmpFolder.newFolder() }

    @Before
    fun setupMocks() {
        `when`(mockProcessBuilder.start()).thenReturn(mockProcess)
        `when`(mockProcessBuilder.environment()).thenReturn(mutableMapOf())
        `when`(mockProcess.inputStream).thenReturn(InputStream.nullInputStream())
        `when`(mockProcess.errorStream).thenReturn(InputStream.nullInputStream())
        `when`(mockProcess.waitFor(any(), any())).thenReturn(true)
        `when`(mockProcess.isAlive).thenReturn(true)
        `when`(mockAdbHelper.findDeviceSerialWithId(any(), any())).thenReturn("myTestDeviceSerial")
        `when`(mockAdbHelper.isBootCompleted(any(), any())).thenReturn(true)
        `when`(mockAdbHelper.isPackageManagerStarted(any(), any())).thenReturn(true)
    }

    @Test
    fun generateSnapshot() {
        val handler = AvdSnapshotHandler(
                showEmulatorKernelLogging = true,
                deviceBootAndSnapshotCheckTimeoutSec = 1234,
                mockAdbHelper,
                extraWaitAfterBootCompleteMs = 0L,
                MoreExecutors.directExecutor(),
        ) {
            mockProcessBuilder
        }

        handler.generateSnapshot(
                "myTestAvdName",
                emulatorExec,
                avdDirectory,
                emulatorGpuFlag = "",
                mockLogger)

        assertThat(mockProcessBuilder.environment())
                .containsEntry("ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL", "1234")
    }
}
