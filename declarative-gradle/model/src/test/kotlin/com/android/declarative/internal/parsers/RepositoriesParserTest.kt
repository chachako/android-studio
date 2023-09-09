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
import com.android.declarative.internal.model.PreDefinedRepositoryInfo
import com.android.declarative.internal.model.RepositoryType
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import org.tomlj.Toml

class RepositoriesParserTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var logger: IssueLogger

    @Test
    fun testSingleRepository() {

        val parser = PluginManagementParser(logger)
        val toml = Toml.parse(
            """
            [[pluginManagement.repositories]]
            name = "google"
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("pluginManagement")!!)
        Truth.assertThat(result.repositories).hasSize(1)
        result.repositories.single().also {
            Truth.assertThat(it.type).isEqualTo(RepositoryType.PRE_DEFINED)
            Truth.assertThat(it).isInstanceOf(PreDefinedRepositoryInfo::class.java)
            Truth.assertThat((it as PreDefinedRepositoryInfo).name).isEqualTo("google")
        }
    }

    @Test
    fun testMultipleRepositories() {

        val parser = PluginManagementParser(logger)
        val toml = Toml.parse(
            """
            [[pluginManagement.repositories]]
            name = "google"

            [[pluginManagement.repositories]]
            name = "mavenCentral"

            [[pluginManagement.repositories]]
            name = "mavenLocal"
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("pluginManagement")!!)
        Truth.assertThat(result.repositories).hasSize(3)
        result.repositories.forEach {
            Truth.assertThat(it.type).isEqualTo(RepositoryType.PRE_DEFINED)
            Truth.assertThat(it).isInstanceOf(PreDefinedRepositoryInfo::class.java)
        }
        Truth.assertThat(result.repositories.map { (it as PreDefinedRepositoryInfo).name })
            .containsExactly("google", "mavenCentral", "mavenLocal")
    }

    @Test
    fun testInvalidRepository() {

        val parser = PluginManagementParser(logger)
        val toml = Toml.parse(
            """
            [[pluginManagement.repositories]]
            invalid = "google"
        """.trimIndent()
        )
        try {
            val result = parser.parseToml(toml.getTable("pluginManagement")!!)
        } catch (e: RuntimeException) {
            Truth.assertThat(e.message).contains("2:1 -> Unsupported repository declaration : [invalid]")
        }
    }

    @Test
    fun testMissingRepository() {

        val parser = PluginManagementParser(logger)
        val toml = Toml.parse(
            """
            [[pluginManagement.repositories]]

            [[pluginManagement.repositories]]
            name = "google"
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("pluginManagement")!!)
        Truth.assertThat(result.repositories).hasSize(1)
        result.repositories.single().also {
            Truth.assertThat(it.type).isEqualTo(RepositoryType.PRE_DEFINED)
            Truth.assertThat(it).isInstanceOf(PreDefinedRepositoryInfo::class.java)
            Truth.assertThat((it as PreDefinedRepositoryInfo).name).isEqualTo("google")
        }
    }
}
