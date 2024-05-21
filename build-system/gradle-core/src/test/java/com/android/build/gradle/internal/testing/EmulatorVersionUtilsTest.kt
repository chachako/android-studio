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

package com.android.build.gradle.internal.testing;

import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EmulatorVersionUtilsTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val emulatorDir: File by lazy(LazyThreadSafetyMode.NONE) { tmpFolder.newFolder() }

    @Test
    fun testEmulatorVersionMetadata() {
        val packageFile = emulatorDir.resolve("package.xml")
        packageFile.writeText(
            """
                <major>31</major><minor>0</minor><micro>0</micro>
            """.trimIndent()
        )
        var metadata = getEmulatorMetadata(emulatorDir)
        assertThat(metadata.canUseForceSnapshotLoad).isFalse()

        packageFile.writeText(
            """
                <major>34</major><minor>2</minor><micro>14</micro>
            """.trimIndent()
        )
        metadata = getEmulatorMetadata(emulatorDir)
        assertThat(metadata.canUseForceSnapshotLoad).isTrue()

        packageFile.writeText(
            """
                <major>30</major><minor>6</minor><micro>1</micro>
            """.trimIndent()
        )
        var e = assertThrows (RuntimeException::class.java) {
            metadata = getEmulatorMetadata(emulatorDir)
        }
        assertThat(e).hasMessageThat().contains(
            "Emulator needs to be updated in order to use managed devices."
        )

        packageFile.writeText("")
        e = assertThrows (RuntimeException::class.java) {
            metadata = getEmulatorMetadata(emulatorDir)
        }
        assertThat(e).hasMessageThat().contains(
            "Could not determine version of Emulator"
        )
    }
}
