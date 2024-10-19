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

import com.android.build.gradle.internal.AvdSnapshotHandler.EmulatorSnapshotCannotCreatedException
import com.android.build.gradle.internal.testing.AdbHelper
import com.android.build.gradle.internal.testing.EmulatorVersionMetadata
import com.android.build.gradle.internal.testing.QemuExecutor
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.Mockito.contains
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.junit.MockitoJUnit
import java.io.File
import java.io.InputStream
import java.lang.RuntimeException

@RunWith(JUnit4::class)
class AvdSnapshotHandlerTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val mockAdbHelper: AdbHelper = mock()

    private val mockAvdManager: AvdManager = mock()

    private val mockAvdInfo: AvdInfo = mock()

    private val mockLogger: ILogger = mock()

    private val emulatorDirectoryProvider: Provider<Directory> = mock()

    private val emulatorDir: Directory = mock()

    private val qemuExecutor: QemuExecutor = mock()

    private val emulatorDirectory: File by lazy(LazyThreadSafetyMode.NONE) { tmpFolder.newFolder() }
    private val avdDirectory: File by lazy(LazyThreadSafetyMode.NONE) { tmpFolder.newFolder() }

    @Before
    fun setupMocks() {
        whenever(emulatorDirectoryProvider.orNull).thenReturn(emulatorDir)
        whenever(emulatorDir.asFile).thenReturn(emulatorDirectory)
        whenever(mockAdbHelper.findDeviceSerialWithId(any(), any())).thenReturn("myTestDeviceSerial")
        whenever(mockAdbHelper.isBootCompleted(any(), any())).thenReturn(true)
        whenever(mockAdbHelper.isPackageManagerStarted(any(), any())).thenReturn(true)
        whenever(mockAvdManager.getAvd(any(), any())).thenReturn(mockAvdInfo)
        val dataFolder = tmpFolder.newFolder()
        whenever(mockAvdInfo.dataFolderPath).thenReturn(dataFolder.toPath())
    }

    private fun createMockProcessBuilder(
            stdout: String = "",
            env: MutableMap<String, String> = mutableMapOf()): ProcessBuilder {
        val mockProcessBuilder = mock<ProcessBuilder>()
        val mockProcess = mock<Process>()
        whenever(mockProcessBuilder.start()).thenReturn(mockProcess)
        whenever(mockProcessBuilder.environment()).thenReturn(env)
        whenever(mockProcess.inputStream).thenReturn(stdout.byteInputStream())
        whenever(mockProcess.errorStream).thenReturn(InputStream.nullInputStream())
        whenever(mockProcess.waitFor(any(), any())).thenReturn(true)
        whenever(mockProcess.isAlive).thenReturn(true)
        return mockProcessBuilder
    }

    @Test
    fun generateSnapshot() {
        val env = mutableMapOf<String, String>()
        val handler = AvdSnapshotHandler(
                showEmulatorKernelLogging = true,
                deviceBootAndSnapshotCheckTimeoutSec = 1234,
                mockAdbHelper,
                emulatorDirectoryProvider,
                qemuExecutor,
                extraWaitAfterBootCompleteMs = 0L,
                MoreExecutors.directExecutor(),
                { _ -> EmulatorVersionMetadata(true) }
        ) { commands ->
            if (commands.contains("-check-snapshot-loadable")) {
                createMockProcessBuilder(stdout = "Loadable")
            } else {
                createMockProcessBuilder(env = env)
            }
        }

        handler.generateSnapshot(
                "myTestAvdName",
                avdDirectory,
                emulatorGpuFlag = "",
                mockAvdManager,
                mockLogger)

        assertThat(env).containsEntry("ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL", "1234")
    }

    @Test
    fun generateSnapshotFailed() {
        val handler = AvdSnapshotHandler(
                showEmulatorKernelLogging = true,
                deviceBootAndSnapshotCheckTimeoutSec = 1234,
                mockAdbHelper,
                emulatorDirectoryProvider,
                qemuExecutor,
                extraWaitAfterBootCompleteMs = 0L,
                MoreExecutors.directExecutor(),
                { _ -> EmulatorVersionMetadata(true) }
        ) { _ -> createMockProcessBuilder() }

        val e = assertThrows(EmulatorSnapshotCannotCreatedException::class.java) {
            handler.generateSnapshot(
                    "myTestAvdName",
                    avdDirectory,
                    emulatorGpuFlag = "",
                    mockAvdManager,
                    mockLogger)
        }

        assertThat(e).hasMessageThat().contains(
            "Snapshot setup for myTestAvdName ran successfully, " +
            "but the snapshot failed to be created.")
        verify(mockLogger)
            .warning(contains("Deleting unbootable snapshot for device: myTestAvdName"))
        verify(qemuExecutor).deleteSnapshot(
            eq("myTestAvdName"),
            any(),
            eq("default_boot"),
            any()
        )

    }
}
