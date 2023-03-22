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

package com.android.build.gradle.internal.utils

import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GradleApiUtilsTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    @Test
    fun `test Directory#getOrderedFileTree`() {
        // The following file paths are crafted such that if `Directory.getOrderedFileTree()`
        // does not sort the files based on `invariantSeparatorsPath`, this test will fail on
        // Windows.
        val rootDir = tmpDir.root
        val files = listOf(
            File(rootDir, "ABC/D"),
            File(rootDir, "AB/CD"),
            File(rootDir, "AB/xy")
        )
        files.forEach {
            it.parentFile.mkdirs()
            it.createNewFile()
        }
        val project = ProjectBuilder.builder().build()
        val directory = project.objects.directoryProperty().fileValue(rootDir).get()

        assertThat(directory.getOrderedFileTree()).isEqualTo(listOf(
            File(rootDir, "AB/CD"),
            File(rootDir, "AB/xy"),
            File(rootDir, "ABC/D")
        ))
    }
}
