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

import com.android.declarative.internal.model.ModuleInfo
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import org.tomlj.Toml

class ModuleParserTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.LENIENT)

    @Test
    fun testSimpleDeclaration() {
        val parser = ModuleParser()
        val toml = Toml.parse(
            """
            [includes.lib1]
        """.trimIndent()
        )
        val moduleInfos = parser.parseToml(toml.getTable("includes")!!)
        Truth.assertThat(moduleInfos).hasSize(1)
        Truth.assertThat(moduleInfos[0].path).isEqualTo(":lib1")
    }

    @Test
    fun testMultipleSimpleDeclaration() {
        val parser = ModuleParser()
        val toml = Toml.parse(
            """
            [includes.lib1]
            [includes.lib2]
            [includes.lib3]
        """.trimIndent()
        )
        val moduleInfos = parser.parseToml(toml.getTable("includes")!!)
        Truth.assertThat(moduleInfos).hasSize(3)
        Truth.assertThat(moduleInfos.map(ModuleInfo::path)).containsExactly(
            ":lib1", ":lib2", ":lib3")
    }

    @Test
    fun testExpandedDeclaration() {
        val parser = ModuleParser()
        val toml = Toml.parse(
            """
            [includes]
            lib1 = ":lib1"
        """.trimIndent()
        )
        val moduleInfos = parser.parseToml(toml.getTable("includes")!!)
        Truth.assertThat(moduleInfos).hasSize(1)
        Truth.assertThat(moduleInfos[0].path).isEqualTo(":lib1")
    }

    @Test
    fun testMultipleExpandedDeclaration() {
        val parser = ModuleParser()
        val toml = Toml.parse(
            """
            [includes]
            lib1 = ":lib1"
            lib2 = ":lib2"
            lib3 = ":lib3"
        """.trimIndent()
        )
        val moduleInfos = parser.parseToml(toml.getTable("includes")!!)
        Truth.assertThat(moduleInfos).hasSize(3)
        Truth.assertThat(moduleInfos.map(ModuleInfo::path)).containsExactly(
            ":lib1", ":lib2", ":lib3")
    }
}
