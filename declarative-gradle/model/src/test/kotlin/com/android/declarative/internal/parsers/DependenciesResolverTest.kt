/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.declarative.internal.model.ProjectDependenciesDAG
import com.android.utils.ILogger
import com.google.common.truth.Truth
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File
import java.nio.file.Paths

class DependenciesResolverTest {

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    val modulesList = mutableListOf<String>()

    @Test
    fun testApiUsages() {

        createModule("javaLib1") { "" }
        createModule("javaLib2") { "" }
        createModule("lib1") { """
            [dependencies]
            api = [
                { project = ":javaLib1" },
            ]
            implementation = [
                { project = ":javaLib2" },
            ]
            """.trimIndent() }
        createModule("lib2") { """
            [dependencies]
            implementation = [
                { project = ":lib1" },
            ]
            """.trimIndent() }
        createModule("lib3") { """
            [dependencies]
            implementation = [
                { project = ":lib2" },
            ]
            """.trimIndent()
        }
        writeSettingsFile()

        val issueLogger = IssueLogger(false, Mockito.mock(ILogger::class.java))
        val parsedDcl = DeclarativeFileParser(issueLogger).parseDeclarativeFile(
            Paths.get(temporaryFolderRule.root.absolutePath, "settings.gradle.toml")
        )

        val dependencies = ProjectDependenciesDAG.create(runBlocking {
            DependenciesResolver(issueLogger).readAllProjectsDependencies(
                { relativePath, fileName ->
                    File(File(temporaryFolderRule.root, relativePath.substring(1)), fileName).readText()
                },
                parsedDcl

            )
        })
        Truth.assertThat(
            dependencies.getNode(":javaLib2")
                ?.getIncomingReferences()
                ?.map(ProjectDependenciesDAG.Node::path)
        ).containsExactly(":lib1")

        Truth.assertThat(
            dependencies.getNode(":javaLib1")
                ?.getIncomingReferences()
                ?.map(ProjectDependenciesDAG.Node::path)
        ).containsExactly(":lib1", ":lib2")
    }

    private fun createModule(moduleName: String, buildFileProvider: () -> String) {
        temporaryFolderRule.newFolder(moduleName).also { lib1 ->
            File(lib1, "build.gradle.toml").writeText("""
                ${buildFileProvider()}
            """.trimIndent())
        }
        modulesList.add(moduleName)
    }

    private fun writeSettingsFile() {
        File(temporaryFolderRule.root, "settings.gradle.toml").also { settingsFile ->
            val stringBuilder = StringBuilder("[include]\n")
            modulesList.forEach {
                stringBuilder.append(it).append(" = \":").append(it).append("\"\n")
            }
            settingsFile.writeText(stringBuilder.toString())
        }
    }

}
