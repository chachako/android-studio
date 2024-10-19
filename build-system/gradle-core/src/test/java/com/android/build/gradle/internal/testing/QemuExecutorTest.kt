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

package com.android.build.gradle.internal.testing

import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class QemuExecutorTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val logger: ILogger = mock()

    private val processBuilder: ProcessBuilder = mock()

    private val process: Process = mock()

    private val emulatorDirectoryProvider: Provider<Directory> = mock()

    private val emulatorDirectory: Directory = mock()

    lateinit var qcowFile: File
    lateinit var emulatorFolder: File
    lateinit var qemuImageExecutable: File
    lateinit var deviceDirectory: File
    lateinit var snapshotFile: File

    @Before
    fun setup() {
        whenever(processBuilder.start()).thenReturn(process)
        whenever(process.waitFor(any(), any())).thenReturn(true)

        deviceDirectory = tmpFolder.newFolder()
        qcowFile = deviceDirectory.resolve("test.qcow2").also {
            it.writeText("""hello""")
        }
        snapshotFile = FileUtils.mkdirs(
            FileUtils.join(deviceDirectory, "snapshots", "default_boot"))

        emulatorFolder = tmpFolder.newFolder()
        qemuImageExecutable = emulatorFolder.resolve("qemu-img")

        whenever(emulatorDirectoryProvider.get()).thenReturn(emulatorDirectory)
        whenever(emulatorDirectory.asFile).thenReturn(emulatorFolder)
    }

    @Test
    fun testDeleteSnapshot() {
        val capturedArgs: MutableList<List<String>> = mutableListOf()

        val executor = QemuExecutor(
            emulatorDirectoryProvider
        ) { argList ->
            capturedArgs.add(argList)
            processBuilder
        }

        assertThat(snapshotFile).exists()

        executor.deleteSnapshot(
            "testDevice",
            deviceDirectory,
            "default_boot",
            logger
        )

        assertThat(capturedArgs).hasSize(1)
        assertThat(capturedArgs[0]).containsExactly(
            qemuImageExecutable.absolutePath,
            "snapshot",
            "-d",
            "default_boot",
            qcowFile.absolutePath
        ).inOrder()

        assertThat(snapshotFile).doesNotExist()
    }
}
