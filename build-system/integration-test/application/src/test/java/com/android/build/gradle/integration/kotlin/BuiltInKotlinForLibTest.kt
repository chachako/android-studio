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

import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.ANDROIDTEST_DEBUG
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils
import com.android.testutils.apk.Aar
import org.junit.Rule
import org.junit.Test

class BuiltInKotlinForLibTest {

    @get:Rule
    val project = createGradleProjectBuilder {
        subProject(":lib") {
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.ANDROID_BUILT_IN_KOTLIN)
            android {
                setUpHelloWorld()
            }
            appendToBuildFile { builtInKotlinSupportDependencies }
        }
    }.withBuiltInKotlinSupport(true)
        .create()

    @Test
    fun testKotlinClassesInAar() {
        val lib = project.getSubproject(":lib")
        lib.getMainSrcDir("java")
            .resolve("LibFoo.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.library
                        class LibFoo
                        """.trimIndent()
                )
            }
        lib.getMainSrcDir("kotlin")
            .resolve("KotlinLibFoo.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.library
                        class KotlinLibFoo
                        """.trimIndent()
                )
            }
        lib.executor().run(":lib:assembleDebug")
        lib.withAar("debug") {
            assertThat(this).containsMainClass("Lcom/foo/library/LibFoo;")
            assertThat(this).containsMainClass("Lcom/foo/library/KotlinLibFoo;")
        }
    }

    @Test
    fun testKotlinClassesInTestApk() {
        val lib = project.getSubproject(":lib")
        lib.getMainSrcDir("java")
            .resolve("LibFoo.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.library
                        class LibFoo
                        """.trimIndent()
                )
            }
        lib.getMainSrcDir("kotlin")
            .resolve("KotlinLibFoo.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.library
                        class KotlinLibFoo
                        """.trimIndent()
                )
            }
        lib.file("src/androidTest/java/LibFooTest.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.library
                        class LibFooTest
                        """.trimIndent()
                )
            }
        lib.file("src/androidTest/kotlin/KotlinLibFooTest.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.library
                        class KotlinLibFooTest
                        """.trimIndent()
                )
            }

        lib.executor().run(":lib:assembleDebugAndroidTest")
        lib.getApk(ANDROIDTEST_DEBUG).use {
            assertThat(it).hasClass("Lcom/foo/library/LibFoo;")
            assertThat(it).hasClass("Lcom/foo/library/KotlinLibFoo;")
            assertThat(it).hasClass("Lcom/foo/library/LibFooTest;")
            assertThat(it).hasClass("Lcom/foo/library/KotlinLibFooTest;")
        }
    }

    /**
     * Test that general built-in Kotlin support is compatible with Kotlin support for testFixtures,
     * in contrast to [BuiltInKotlinForTestFixturesTest], which tests Kotlin support for
     * testFixtures in isolation.
     */
    @Test
    fun testTestFixtures() {
        val lib = project.getSubproject(":lib")
        TestFileUtils.appendToFile(
            lib.buildFile,
            """
                android.testFixtures.enable = true

                dependencies {
                    testFixturesImplementation("org.jetbrains.kotlin:kotlin-stdlib:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
                }
                """.trimIndent()
        )
        lib.file("src/testFixtures/kotlin/LibFooTestFixture.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library

                    import com.foo.library.LibFoo

                    class LibFooTestFixture
                    """.trimIndent()
            )
        }
        lib.file("src/main/kotlin/LibFoo.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library
                    class LibFoo
                    """.trimIndent()
            )
        }
        lib.executor().run(":lib:assembleDebugTestFixtures")
        val aar = lib.outputDir.resolve("aar").listFiles()?.single()
        Aar(aar).use {
            assertThat(it).containsMainClass("Lcom/foo/library/LibFooTestFixture;")
        }

        // Kotlin support for testFixtures should work with or without the gradle property when
        // general built-in Kotlin support is enabled
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT.propertyName}=true"
        )
        lib.executor().run(":lib:assembleDebugTestFixtures")
        Aar(aar).use {
            assertThat(it).containsMainClass("Lcom/foo/library/LibFooTestFixture;")
        }
    }
}
