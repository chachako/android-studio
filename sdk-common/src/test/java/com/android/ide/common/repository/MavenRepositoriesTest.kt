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
package com.android.ide.common.repository

import com.android.ide.common.gradle.Module
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Files.createDirectories

class MavenRepositoriesTest {

    @Test
    fun `test get all versions non existent directory`() {
        val repo = Jimfs.newFileSystem(Configuration.windows()).getPath("C:\\src\\out\\repo")
        assertThat(MavenRepositories.getAllVersions(repo, Module.parse("com.example:lib"))).isEmpty()
    }

    @Test
    fun `test get all versions`() {
        val repo = Jimfs.newFileSystem(Configuration.windows()).getPath("C:\\src\\out\\repo")
        val dir = repo.resolve("com/example/lib")
        createDirectories(dir)
        createDirectories(dir.resolve("8.0.0-alpha02"))
        createDirectories(dir.resolve("8.0.0"))
        createDirectories(dir.resolve("8.0.0-beta01"))
        Files.write(dir.resolve("10"), byteArrayOf())
        assertThat(MavenRepositories.getAllVersions(repo, Module.parse("com.example:lib")).map { it.toString() })
            .containsExactly("8.0.0-alpha02", "8.0.0-beta01", "8.0.0")
            .inOrder()
    }
}
