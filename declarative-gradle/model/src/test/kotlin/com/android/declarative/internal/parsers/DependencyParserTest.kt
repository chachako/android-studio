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
import com.android.declarative.internal.model.DependencyType
import com.android.declarative.internal.model.FilesDependencyInfo
import com.android.declarative.internal.model.MavenDependencyInfo
import com.android.declarative.internal.model.NotationDependencyInfo
import com.android.utils.ILogger
import com.google.common.truth.Truth.*
import org.junit.Test
import org.mockito.Mockito
import org.tomlj.Toml

@Suppress("UnstableApiUsage")
class DependencyParserTest {

    @Test
    fun testProjectDependency() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            [dependencies.implementation.lib1]
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(1)
        result.single().also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.PROJECT)
            assertThat(it).isInstanceOf(NotationDependencyInfo::class.java)
            (it as NotationDependencyInfo).also {
                assertThat(it.notation).isEqualTo(":lib1")
            }
        }
    }

    @Test
    fun testMultipleShortFormProjectDependency() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            [dependencies.implementation.lib1]
            [dependencies.testImplementation.lib2]
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(2)
        result.get(0).also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.PROJECT)
            assertThat(it).isInstanceOf(NotationDependencyInfo::class.java)
            (it as NotationDependencyInfo).also {
                assertThat(it.notation).isEqualTo(":lib1")
            }
        }
        result.get(1).also {
            assertThat(it.configuration).isEqualTo("testImplementation")
            assertThat(it.type).isEqualTo(DependencyType.PROJECT)
            assertThat(it).isInstanceOf(NotationDependencyInfo::class.java)
            (it as NotationDependencyInfo).also {
                assertThat(it.notation).isEqualTo(":lib2")
            }
        }
    }

    @Test
    fun testSimpleExternalDependency() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            [dependencies]
            mockito = { configuration = "implementation", notation="org.mockito:mockito-core:4.8.0" }
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(1)
        result.single().also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.NOTATION)
            assertThat(it).isInstanceOf(NotationDependencyInfo::class.java)
            (it as NotationDependencyInfo).also {
                assertThat(it.notation).isEqualTo("org.mockito:mockito-core:4.8.0")
            }
        }
    }

    @Test
    fun testExternalDependencyInDottedNames() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            [dependencies.implementation]
            mockito = "org.mockito:mockito-core:4.8.0"
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(1)
        result.single().also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.NOTATION)
            assertThat(it).isInstanceOf(NotationDependencyInfo::class.java)
            (it as NotationDependencyInfo).also {
                assertThat(it.notation).isEqualTo("org.mockito:mockito-core:4.8.0")
            }
        }
    }

    @Test
    fun testPartialExternalDependency() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            [dependencies]
            mockito = { configuration = "implementation", group = "org.mockito", name = "mockito-core", version = "4.8.0" }
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(1)
        result.single().also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.LIBRARY)
            assertThat(it).isInstanceOf(MavenDependencyInfo::class.java)
            (it as MavenDependencyInfo).also {
                assertThat(it.group).isEqualTo("org.mockito")
                assertThat(it.name).isEqualTo("mockito-core")
                assertThat(it.version).isEqualTo("4.8.0")
            }
        }
    }

    @Test
    fun testPartialExternalDependencyInDottedNames() {
        val parser = createDependenciesParser()

        val toml = Toml.parse(
            """
            [dependencies.testImplementation]
            mockito = { group = "org.mockito", name = "mockito-core", version = "4.8.0" }
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(1)
        result[0].also {
            assertThat(it.configuration).isEqualTo("testImplementation")
            assertThat(it.type).isEqualTo(DependencyType.LIBRARY)
            assertThat(it).isInstanceOf(MavenDependencyInfo::class.java)
            (it as MavenDependencyInfo).also {
                assertThat(it.group).isEqualTo("org.mockito")
                assertThat(it.name).isEqualTo("mockito-core")
                assertThat(it.version).isEqualTo("4.8.0")
            }
        }
    }

    @Test
    fun testVersionCatalogDependency() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            [dependencies]
            mockito = "libs.junit"
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(1)
        result.single().also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.NOTATION)
            assertThat(it).isInstanceOf(NotationDependencyInfo::class.java)
            (it as NotationDependencyInfo).also {
                assertThat(it.notation).isEqualTo("libs.junit")
            }
        }
    }

    @Test
    fun testFilesNotationDependency() {
        val parser = DependencyParser(
            IssueLogger(lenient = true, logger = Mockito.mock(ILogger::class.java))
        )
        val toml = Toml.parse(
            """
            [dependencies]
            localFiles = { configuration='implementation', files = "local.jar" }
            localFiles2 = { configuration='implementation', files = [ "some.jar", "something.else", "final.one" ] }
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(2)
        result[0].also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.FILES)
            assertThat(it).isInstanceOf(FilesDependencyInfo::class.java)
            (it as FilesDependencyInfo).also {
                assertThat(it.files).isEqualTo(listOf("local.jar"))
            }
        }
        result[1].also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.FILES)
            assertThat(it).isInstanceOf(FilesDependencyInfo::class.java)
            (it as FilesDependencyInfo).also {
                assertThat(it.files).isEqualTo(listOf("some.jar", "something.else", "final.one" ))
            }
        }
    }

    @Test
    fun testMultipleProjectDependency() {
        val parser = createDependenciesParser()

        val toml = Toml.parse(
            """
            [dependencies]
            lib1 = { configuration="implementation" }
            lib2 = { configuration="implementation" }
            otherLib = { configuration="implementation", project=":lib3" }
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(3)
        result[0].also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.PROJECT)
            assertThat(it).isInstanceOf(NotationDependencyInfo::class.java)
            (it as NotationDependencyInfo).also {
                assertThat(it.notation).isEqualTo(":lib1")
            }
        }
        result[1].also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.PROJECT)
            assertThat(it).isInstanceOf(NotationDependencyInfo::class.java)
            (it as NotationDependencyInfo).also {
                assertThat(it.notation).isEqualTo(":lib2")
            }
        }
        result[2].also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.PROJECT)
            assertThat(it).isInstanceOf(NotationDependencyInfo::class.java)
            (it as NotationDependencyInfo).also {
                assertThat(it.notation).isEqualTo(":lib3")
            }
        }
    }

    private fun createDependenciesParser() =
        DependencyParser(
            IssueLogger(lenient = true, logger = Mockito.mock(ILogger::class.java))
        )
}
