/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.integration.kotlin

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.VERSION_CATALOG
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.dsl.ModulePropertyKey.BooleanWithDefault.SCREENSHOT_TEST
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils
import com.android.testutils.apk.Aar
import com.android.testutils.truth.PathSubject
import org.junit.Rule
import org.junit.Test

class BuiltInKotlinSupportTest {

    @get:Rule
    val project =
        createGradleProjectBuilder {
            subProject(":lib") {
                plugins.add(PluginType.ANDROID_LIB)
                plugins.add(PluginType.KOTLIN_ANDROID)
                android {
                    setUpHelloWorld()
                }
            }
        }.withKotlinGradlePlugin(true)
            .create()

    @Test
    fun testTestFixturesKotlinSupport() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT.propertyName}=true"
        )
        val lib = project.getSubproject(":lib")
        lib.buildFile.appendText(
            """
                android.testFixtures.enable = true

                kotlin {
                    jvmToolchain(17)
                }

                dependencies {
                    testFixturesImplementation("org.jetbrains.kotlin:kotlin-stdlib:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
                }
                """.trimIndent()
        )
        lib.file("src/testFixtures/kotlin/LibTestFixtureFoo.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library
                    class LibTestFixtureFoo
                    """.trimIndent()
            )
        }
        lib.executor().run(":lib:assembleDebugTestFixtures")
        val aar = lib.outputDir.resolve("aar").listFiles().single()
        Aar(aar).use {
            assertThat(it).containsMainClass("Lcom/foo/library/LibTestFixtureFoo;")
        }
    }

    @Test
    fun testScreenshotTest() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_SCREENSHOT_TEST.propertyName}=true"
        )
        val lib = project.getSubproject(":lib")
        lib.buildFile.appendText(
            """
                android.experimentalProperties["${SCREENSHOT_TEST.key}"] = true

                kotlin {
                    jvmToolchain(17)
                }

                dependencies {
                    testFixturesImplementation("org.jetbrains.kotlin:kotlin-stdlib:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
                }
                """.trimIndent()
        )
        lib.file("src/screenshotTest/kotlin/LibScreenshotTest.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library
                    class LibScreenshotTest
                    """.trimIndent()
            )
        }
        lib.executor().run(":lib:compileDebugScreenshotTestKotlin")
        val screenshotTestClassFile =
            project.getSubproject("lib")
                .getIntermediateFile(
                    "kotlinc",
                    "debugScreenshotTest",
                    "compileDebugScreenshotTestKotlin",
                    "classes",
                    "com",
                    "foo",
                    "library",
                    "LibScreenshotTest.class"
                )
        PathSubject.assertThat(screenshotTestClassFile).exists()
    }

    @Test
    fun testInternalModifierAccessibleFromTestFixtures() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT.propertyName}=true"
        )
        val lib = project.getSubproject(":lib")
        lib.buildFile.appendText(
            """
                android.testFixtures.enable = true

                kotlin {
                    jvmToolchain(17)
                }

                dependencies {
                    testFixturesImplementation("org.jetbrains.kotlin:kotlin-stdlib:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
                }
                """.trimIndent()
        )
        lib.file("src/testFixtures/kotlin/LibTestFixtureFoo.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library
                    class LibTestFixtureFoo {
                      init { LibFoo().bar() }
                    }
                    """.trimIndent()
            )
        }
        lib.getMainSrcDir("java").resolve("LibFoo.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library
                    class LibFoo {
                      internal fun bar() {}
                    }
                """.trimIndent()
            )
        }
        lib.executor().run(":lib:assembleDebugTestFixtures")
    }

    @Test
    fun testInternalModifierAccessibleFromScreenshotTest() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_SCREENSHOT_TEST.propertyName}=true"
        )
        val lib = project.getSubproject(":lib")
        lib.buildFile.appendText(
            """
                android.experimentalProperties["${SCREENSHOT_TEST.key}"] = true

                kotlin {
                    jvmToolchain(17)
                }

                dependencies {
                    testFixturesImplementation("org.jetbrains.kotlin:kotlin-stdlib:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
                }
                """.trimIndent()
        )
        lib.file("src/screenshotTest/kotlin/LibScreenshotTestFoo.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library
                    class LibScreenshotTestFoo {
                      init { LibFoo().bar() }
                    }
                    """.trimIndent()
            )
        }
        lib.getMainSrcDir("java").resolve("LibFoo.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library
                    class LibFoo {
                      internal fun bar() {}
                    }
                """.trimIndent()
            )
        }
        lib.executor().run(":lib:compileDebugScreenshotTestKotlin")
    }

    @Test
    fun testLowKotlinVersionWithTestFixturesKotlinSupport() {
        TestFileUtils.searchAndReplace(
            project.projectDir.parentFile.resolve(VERSION_CATALOG),
            "version('kotlinVersion', '${TestUtils.KOTLIN_VERSION_FOR_TESTS}')",
            "version('kotlinVersion', '1.8.10')"
        )
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT.propertyName}=true"
        )
        val lib = project.getSubproject(":lib")
        lib.buildFile.appendText(
            """
                android.testFixtures.enable = true

                kotlin {
                    jvmToolchain(17)
                }

                dependencies {
                    testFixturesImplementation("org.jetbrains.kotlin:kotlin-stdlib:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
                }
                """.trimIndent()
        )
        val result = lib.executor().expectFailure().run(":lib:assembleDebugTestFixtures")
        result.assertErrorContains(
            "The current Kotlin Gradle plugin version (1.8.10) is below the required"
        )
    }

    @Test
    fun testLowKotlinVersionWithScreenshotTest() {
        TestFileUtils.searchAndReplace(
            project.projectDir.parentFile.resolve(VERSION_CATALOG),
            "version('kotlinVersion', '${TestUtils.KOTLIN_VERSION_FOR_TESTS}')",
            "version('kotlinVersion', '1.8.10')"
        )
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_SCREENSHOT_TEST.propertyName}=true"
        )
        val lib = project.getSubproject(":lib")
        lib.buildFile.appendText(
            """
                android.experimentalProperties["${SCREENSHOT_TEST.key}"] = true

                kotlin {
                    jvmToolchain(17)
                }

                dependencies {
                    testFixturesImplementation("org.jetbrains.kotlin:kotlin-stdlib:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
                }
                """.trimIndent()
        )
        val result = lib.executor().expectFailure().run(":lib:compileDebugScreenshotTest")
        result.assertErrorContains(
            "The current Kotlin Gradle plugin version (1.8.10) is below the required"
        )
    }

    @Test
    fun testLowKotlinVersionWithNoBuiltInKotlinSupport() {
        TestFileUtils.searchAndReplace(
            project.projectDir.parentFile.resolve(VERSION_CATALOG),
            "version('kotlinVersion', '${TestUtils.KOTLIN_VERSION_FOR_TESTS}')",
            "version('kotlinVersion', '1.8.10')"
        )
        val lib = project.getSubproject(":lib")
        lib.buildFile.appendText(
            """
                android.testFixtures.enable = true
                """.trimIndent()
        )
        // We expect no build failure in this case.
        // Set failOnWarning to false because Gradle warns about deprecated feature(s) used by KGP 1.8.10.
        lib.executor().withFailOnWarning(false).run(":lib:assembleDebugTestFixtures")
    }
}
