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

package com.android.tools.firebase.testlab.gradle.tasks

import com.android.testutils.MockitoKt.mock
import com.google.api.services.storage.model.StorageObject
import com.google.common.truth.Truth.assertThat
import nl.jqno.equalsverifier.EqualsVerifier
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import java.nio.charset.StandardCharsets

class ExtraDeviceFilesManagerTest {

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    // checks a file is consistent after being read to and written from an object.
    @Test
    fun fileToFileConsistent() {
        val contents =
            """
                device_path1${"\t"}gs://bucket-name1/file1${"\t"}aabbcc
                device_path2${"\t"}gs://bucket-name2/file2${"\t"}ddeeff
            """.trimIndent()
        val contentFile = temporaryFolderRule.newFile().apply {
            writeText(contents)
        }

        val manager = ExtraDeviceFilesManager(contentFile)

        assertThat(manager.devicePathsToUrls()).containsExactlyEntriesIn(
            mapOf(
                "device_path1" to "gs://bucket-name1/file1",
                "device_path2" to "gs://bucket-name2/file2"
            )
        )

        val resultFile = temporaryFolderRule.newFile()

        manager.toFile(resultFile)

        assertThat(resultFile.readText(StandardCharsets.UTF_8)).isEqualTo(
            contents
        )
    }

    // Tests an object is consistent if written to and read from a file.
    @Test
    fun objectToObjectConsistent() {
        val object1: StorageObject = mock<StorageObject>().apply {
            `when`(this.bucket).thenReturn("bucket1")
            `when`(this.name).thenReturn("hello")
            `when`(this.md5Hash).thenReturn("aabbcc")
        }
        val object2: StorageObject = mock<StorageObject>().apply {
            `when`(this.bucket).thenReturn("bucket2")
            `when`(this.name).thenReturn("world")
            `when`(this.md5Hash).thenReturn("aabbcc")
        }

        val manager = ExtraDeviceFilesManager().apply {
            add("some_device_path", object1)
            add("some_other_path", object2)
        }

        assertThat(manager.devicePathsToUrls()).containsExactlyEntriesIn(
            mapOf(
                "some_device_path" to "gs://bucket1/hello",
                "some_other_path" to "gs://bucket2/world"
            )
        )

        val resultFile = temporaryFolderRule.newFile()

        manager.toFile(resultFile)


        val newManager = ExtraDeviceFilesManager(resultFile)
        assertThat(newManager).isEqualTo(manager)
        assertThat(newManager.hashCode()).isEqualTo(manager.hashCode())
    }

    @Test
    fun equalsConsistency() {
        EqualsVerifier.forClass(ExtraDeviceFilesManager::class.java).verify()
    }
}
