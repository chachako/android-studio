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
import com.android.declarative.internal.model.DependencyInfo
import com.android.declarative.internal.model.DependencyInfo.ExtensionFunction
import com.android.declarative.internal.model.DependencyType
import com.android.declarative.internal.model.DependencyInfo.Files
import com.android.declarative.internal.model.DependencyInfo.Maven
import com.android.declarative.internal.model.DependencyInfo.Notation
import com.android.declarative.internal.model.DependencyInfo.Platform
import com.android.declarative.internal.toml.InvalidTomlException
import com.android.utils.ILogger
import com.google.common.truth.Truth.*
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.tomlj.Toml

class DependencyParserTest {

    @Test
    fun testProjectDependency() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            [dependencies]
            implementation = [
                { project = ":lib1" },
            ]
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(1)
        result.single().also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.PROJECT)
            assertThat(it).isInstanceOf(Notation::class.java)
            (it as Notation).also { dependencyInfo ->
                assertThat(dependencyInfo.notation).isEqualTo(":lib1")
            }
        }
    }

    @Test
    fun testMultipleShortFormProjectDependency() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            [dependencies]
            implementation = [
               { project = ":lib1" }
            ]
            testImplementation = [
               { project = ":lib2" }
            ]
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(2)
        result[0].also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.PROJECT)
            assertThat(it).isInstanceOf(Notation::class.java)
            (it as Notation).also { dependencyInfo ->
                assertThat(dependencyInfo.notation).isEqualTo(":lib1")
            }
        }
        result[1].also {
            assertThat(it.configuration).isEqualTo("testImplementation")
            assertThat(it.type).isEqualTo(DependencyType.PROJECT)
            assertThat(it).isInstanceOf(Notation::class.java)
            (it as Notation).also { dependencyInfo ->
                assertThat(dependencyInfo.notation).isEqualTo(":lib2")
            }
        }
    }

    @Test
    fun testProjectDependencies() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            dependencies.implementation = [
                { project = ":core:testing" },
                { project = ":core:datastore-test" },
            ]
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(2)
        result[0].also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.PROJECT)
            assertThat(it).isInstanceOf(Notation::class.java)
            (it as Notation).also { dependencyInfo ->
                assertThat(dependencyInfo.notation).isEqualTo(":core:testing")
            }
        }
        result[1].also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.PROJECT)
            assertThat(it).isInstanceOf(Notation::class.java)
            (it as Notation).also { dependencyInfo ->
                assertThat(dependencyInfo.notation).isEqualTo(":core:datastore-test")
            }
        }
    }

    @Test
    fun testExternalDependencyAsArray() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            [dependencies]
            implementation = [
                { notation = "org.mockito:mockito-core:4.8.0" },
                { notation = "org.junit:junit:5.1.0" },
            ]
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(2)
        result[0].also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.NOTATION)
            assertThat(it).isInstanceOf(Notation::class.java)
            (it as Notation).also { dependencyInfo ->
                assertThat(dependencyInfo.notation).isEqualTo("org.mockito:mockito-core:4.8.0")
            }
        }
        result[1].also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.NOTATION)
            assertThat(it).isInstanceOf(Notation::class.java)
            (it as Notation).also { dependencyInfo ->
                assertThat(dependencyInfo.notation).isEqualTo("org.junit:junit:5.1.0")
            }
        }
    }

    @Test
    fun testProjectCatalogDependencyAsArray() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            dependencies.implementation = [
                { notation = "libs.junit" },
            ]
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(1)
        result.single().also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.NOTATION)
            assertThat(it).isInstanceOf(Notation::class.java)
            (it as Notation).also { dependencyInfo ->
                assertThat(dependencyInfo.notation).isEqualTo("libs.junit")
            }
        }
    }

    @Test
    fun testKotlinDependencyAsArray() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            dependencies.testImplementation = [
                { extension = "kotlin", module = "test" },
            ]
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(1)
        result.single().also {
            assertThat(it.configuration).isEqualTo("testImplementation")
            assertThat(it.type).isEqualTo(DependencyType.EXTENSION_FUNCTION)
            assertThat(it).isInstanceOf(ExtensionFunction::class.java)
            (it as ExtensionFunction).also { dependencyInfo ->
                assertThat(dependencyInfo.extension).isEqualTo("kotlin")
                assertThat(dependencyInfo.parameters).containsExactly(
                    "module", "test"
                )
            }
        }
    }

    @Test
    fun testPartialExternalDependency() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            [dependencies]
            implementation = [
                { group = "org.mockito", name = "mockito-core", version = "4.8.0" },
            ]
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(1)
        result.single().also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.LIBRARY)
            assertThat(it).isInstanceOf(Maven::class.java)
            (it as Maven).also { dependencyInfo ->
                assertThat(dependencyInfo.group).isEqualTo("org.mockito")
                assertThat(dependencyInfo.name).isEqualTo("mockito-core")
                assertThat(dependencyInfo.version).isEqualTo("4.8.0")
            }
        }
    }

    @Test
    fun testVersionCatalogDependency() {
        val parser = createDependenciesParser()
        val toml = Toml.parse(
            """
            [dependencies]
            implementation = [
                { notation = "libs.junit" }
            ]
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(1)
        result.single().also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.NOTATION)
            assertThat(it).isInstanceOf(Notation::class.java)
            (it as Notation).also { dependencyInfo ->
                assertThat(dependencyInfo.notation).isEqualTo("libs.junit")
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
            implementation = [
                { files = "local.jar" },
                { files = [ "some.jar", "something.else", "final.one" ] },
            ]
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(2)
        result[0].also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.FILES)
            assertThat(it).isInstanceOf(Files::class.java)
            (it as Files).also { dependencyInfo ->
                assertThat(dependencyInfo.files).isEqualTo(listOf("local.jar"))
            }
        }
        result[1].also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.FILES)
            assertThat(it).isInstanceOf(Files::class.java)
            (it as Files).also { dependencyInfo ->
                assertThat(dependencyInfo.files).isEqualTo(listOf("some.jar", "something.else", "final.one" ))
            }
        }
    }

    @Test
    fun testPlatformDependency() {
        val parser = DependencyParser(
            IssueLogger(lenient = true, logger = Mockito.mock(ILogger::class.java))
        )
        val toml = Toml.parse(
            """
            dependencies.implementation = [
                { platform = "libs.firebase.bom" },
            ]
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(1)
        result[0].also {
            assertThat(it.configuration).isEqualTo("implementation")
            assertThat(it.type).isEqualTo(DependencyType.PLATFORM)
            assertThat(it).isInstanceOf(Platform::class.java)
            (it as Platform).also { dependencyInfo ->
                assertThat(dependencyInfo.name).isEqualTo("libs.firebase.bom")
            }
        }
    }

    @Test
    fun testTableInsteadOfArray() {
        val logger = Mockito.mock(ILogger::class.java)
        val parser = DependencyParser(IssueLogger(lenient = true, logger))
        val toml = Toml.parse(
            """
            [dependencies]
            implementation = {
                { files = "local.jar" },
                { files = [ "some.jar", "something.else", "final.one" ] },
            }
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(0)
        val arg1Captor = ArgumentCaptor.forClass(InvalidTomlException::class.java)
        val arg2Captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(logger).error(arg1Captor.capture(), arg2Captor.capture())
        assertThat(arg2Captor.value).contains(
            "Dependencies cannot be expressed with a Toml table, use an array instead")
        assertThat(arg1Captor.value.location?.line()).isEqualTo(2)
        assertThat(arg1Captor.value.location?.column()).isEqualTo(1)
    }

    @Test
    fun testAnotherIncorrectUsage() {
        val logger = Mockito.mock(ILogger::class.java)
        val parser = DependencyParser(IssueLogger(lenient = true, logger))
        val toml = Toml.parse(
            """
            [dependencies]
            implementation = [
               project = ":lib1"
            ]
            testImplementation = [
               project = ":lib2"
            ]
        """.trimIndent()
        )
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(0)
        val arg1Captor  = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(logger).warning(arg1Captor.capture())
        assertThat(arg1Captor.value).contains(
            "Warning: line 2, column 1 : Empty implementation dependencies declaration")

    }

    @Test
    fun testComplicatedDependencies() {
        val parser = createDependenciesParser()

        val toml = Toml.parse(
            """
                dependencies.implementation = [
                    { project = ":feature:interests" },
                    { project = ":feature:for-you"},
                    { project = ":feature:bookmarks"},
                    { project = ":feature:topic"},
                    { project = ":feature:search"},
                    { project = ":feature:settings"},

                    { project = ":core:common"},
                    { project = ":core:ui"},
                    { project = ":core:design-system"},
                    { project = ":core:data"},
                    { project = ":core:model"},
                    { project = ":core:analytics"},

                    { project = ":sync:work"},

                    { notation = "libs.androidx.activity.compose" },
                    { notation = "libs.androidx.appcompat" },
                    { notation = "libs.androidx.core.ktx" },
                    { notation = "libs.androidx.core.splashscreen" },
                    { notation = "libs.androidx.compose.runtime" },
                    { notation = "libs.androidx.lifecycle.runtimeCompose" },
                    { notation = "libs.androidx.compose.runtime.tracing" },
                    { notation = "libs.androidx.compose.material3.windowSizeClass" },
                    { notation = "libs.androidx.hilt.navigation.compose" },
                    { notation = "libs.androidx.navigation.compose" },
                    { notation = "libs.androidx.window.manager" },
                    { notation = "libs.androidx.profile-installer" },
                    { notation = "libs.kotlinx.coroutines.guava" },
                    { notation = "libs.coil.kt" },
                    { notation = "libs.work.testing" },
                ]

                dependencies.androidTestImplementation = [
                    { project = ":core:testing" },
                    { project = ":core:datastore-test" },
                    { project = ":core:data-test" },
                    { project = ":core:network" },
                    { notation = "libs.androidx.navigation.testing"},
                    { extension = "kotlin", module = "test" },
                ]

                dependencies.debugImplementation = [
                    { project = ":ui-test-hilt-manifest" },
                    { notation = "libs.androidx.compose.ui.testManifest"}
                ]

                dependencies.testImplementation = [
                    { project = ":core:testing" },
                    { project = ":core:datastore-test" },
                    { project = ":core:data-test" },
                    { project = ":core:network" },
                    { notation = "libs.androidx.navigation.testing" },
                    { notation = "libs.accompanist.test-harness" },
                    { extension = "kotlin", module = "test"}
                ]

                dependencies.kaptTest = [
                    { notation = "libs.hilt.compiler" },
                ]
            """.trimIndent())
        val result = parser.parseToml(toml.getTable("dependencies")!!)
        assertThat(result).hasSize(44)

        val implementationDependencies = result
            .filter {it.configuration == "implementation" }
        assertThat(implementationDependencies).hasSize(28)
        assertThat(implementationDependencies.filter {it.type == DependencyType.PROJECT }).hasSize(13)
        assertThat(implementationDependencies.filter {it.type == DependencyType.NOTATION }).hasSize(15)

        val androidTestImplementationDependencies = result
            .filter { it.configuration == "androidTestImplementation" }
        assertThat(androidTestImplementationDependencies).hasSize(6)
        assertThat(androidTestImplementationDependencies.filter {it.type == DependencyType.PROJECT }).hasSize(4)
        assertThat(androidTestImplementationDependencies.filter {it.type == DependencyType.NOTATION }).hasSize(1)
        assertThat(androidTestImplementationDependencies.filter {it.type == DependencyType.EXTENSION_FUNCTION }).hasSize(1)

        val testImplementationDependencies = result
            .filter { it.configuration == "testImplementation"}
        assertThat(testImplementationDependencies).hasSize(7)
        assertThat(testImplementationDependencies.filter {it.type == DependencyType.PROJECT }).hasSize(4)
        assertThat(testImplementationDependencies.filter {it.type == DependencyType.NOTATION }).hasSize(2)
        assertThat(testImplementationDependencies.filter {it.type == DependencyType.EXTENSION_FUNCTION }).hasSize(1)
    }

    private fun createDependenciesParser() =
        DependencyParser(
            IssueLogger(lenient = false, logger = Mockito.mock(ILogger::class.java))
        )
}
