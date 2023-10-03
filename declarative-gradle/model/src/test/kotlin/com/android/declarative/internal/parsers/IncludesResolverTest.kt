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

package com.android.declarative.internal.parsers

import com.android.declarative.internal.IssueLogger
import com.android.declarative.internal.model.ModuleInfo
import com.android.utils.ILogger
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File
import java.nio.file.Paths

class IncludesResolverTest {

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @Test
    fun testApiUsages() {
        writeSettingsFile(listOf("javaLib1", "lib1", "app"))
        val issueLogger = IssueLogger(false, Mockito.mock(ILogger::class.java))
        val parsedDcl = DeclarativeFileParser(issueLogger).parseDeclarativeFile(
            Paths.get(temporaryFolderRule.root.absolutePath, "settings.gradle.toml")
        )
        val dependencies = IncludesResolver().getIncludedModules(parsedDcl)

        Truth.assertThat(dependencies).containsExactly(
            ModuleInfo(":javaLib1"),
            ModuleInfo(":lib1"),
            ModuleInfo(":app")
        )
    }

    private fun writeSettingsFile(modulesList: List<String>) {
        File(temporaryFolderRule.root, "settings.gradle.toml").also { settingsFile ->
            val stringBuilder = StringBuilder("include = [")
            modulesList.forEach {
                stringBuilder.append("\":").append(it).append("\",\n")
            }
            stringBuilder.append("]")
            settingsFile.writeText(stringBuilder.toString())
        }
    }
}
