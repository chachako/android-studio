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

package com.android.tools.firebase.testlab.gradle.services.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.attribute.FileTime
import kotlin.io.path.setLastModifiedTime

class FileHashCacheTest {

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @Test
    fun testSameFileGeneratesHashOnce() {
        val file = temporaryFolderRule.newFile("hello")
        file.writeText("a")

        var hashRunCount = 0
        val cache = FileHashCache({ file ->
            ++hashRunCount
            file.name
        })

        var hash = cache.retrieveOrGenerateHash(file)

        assertThat(hashRunCount).isEqualTo(1)
        assertThat(hash).isEqualTo("hello")

        hash = cache.retrieveOrGenerateHash(file)

        assertThat(hashRunCount).isEqualTo(1)
        assertThat(hash).isEqualTo("hello")
    }

    @Test
    fun testDifferentFileDifferentHash() {
        val file1 = temporaryFolderRule.newFile("hello")
        val file2 = temporaryFolderRule.newFile("world")

        var hashRunCount = 0
        val cache = FileHashCache({ file ->
            ++hashRunCount
            file.name
        })

        val hash1 = cache.retrieveOrGenerateHash(file1)
        val hash2 = cache.retrieveOrGenerateHash(file2)

        assertThat(hashRunCount).isEqualTo(2)
        assertThat(hash1).isEqualTo("hello")
        assertThat(hash2).isEqualTo("world")
    }

    @Test
    fun testFileModificationGeneratesDifferentHash() {
        val file = temporaryFolderRule.newFile("test").apply {
            writeText("a")
        }
        // Need to set last modified manually, as the test may run too quick for
        // the timestamp to change.
        file.toPath().setLastModifiedTime(FileTime.fromMillis(2000000))

        var hashRunCount = 0
        val cache = FileHashCache({ file ->
            ++hashRunCount
            file.readLines().first()
        })

        var hash = cache.retrieveOrGenerateHash(file)

        assertThat(hashRunCount).isEqualTo(1)
        assertThat(hash).isEqualTo("a")

        file.writeText("b")
        file.toPath().setLastModifiedTime(FileTime.fromMillis(2000001))

        hash = cache.retrieveOrGenerateHash(file)

        assertThat(hashRunCount).isEqualTo(2)
        assertThat(hash).isEqualTo("b")
    }
}
