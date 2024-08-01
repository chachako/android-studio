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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.ANDROIDTEST_DEBUG
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.DEBUG
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.dsl.ModulePropertyKey.BooleanWithDefault.SCREENSHOT_TEST
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_TESTS
import com.android.testutils.truth.PathSubject
import org.junit.Rule
import org.junit.Test

class BuiltInKotlinForAppTest {

    @get:Rule
    val project = createGradleProjectBuilder {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            plugins.add(PluginType.ANDROID_BUILT_IN_KOTLIN)
            android {
                setUpHelloWorld()
            }
            dependencies {
                api(project(":lib"))
            }
            appendToBuildFile { builtInKotlinSupportDependencies }
        }
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
    fun testKotlinClassesInApk() {
        val app = project.getSubproject(":app")
        app.getMainSrcDir("java")
            .resolve("AppFoo.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.application
                        class AppFoo
                        """.trimIndent()
                )
            }
        app.getMainSrcDir("kotlin")
            .resolve("KotlinAppFoo.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.application
                        class KotlinAppFoo
                        """.trimIndent()
                )
            }

        app.executor().run(":app:assembleDebug")
        app.getApk(DEBUG).use {
            assertThat(it).hasClass("Lcom/foo/application/AppFoo;")
            assertThat(it).hasClass("Lcom/foo/application/KotlinAppFoo;")
        }
    }

    @Test
    fun testKotlinClassesInTestApk() {
        val app = project.getSubproject(":app")
        app.file("src/androidTest/java/AppFooTest.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.application
                        class AppFooTest
                        """.trimIndent()
                )
            }
        app.file("src/androidTest/kotlin/KotlinAppFooTest.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.application
                        class KotlinAppFooTest
                        """.trimIndent()
                )
            }

        app.executor().run(":app:assembleDebugAndroidTest")
        app.getApk(ANDROIDTEST_DEBUG).use {
            assertThat(it).hasClass("Lcom/foo/application/AppFooTest;")
            assertThat(it).hasClass("Lcom/foo/application/KotlinAppFooTest;")
        }
    }

    @Test
    fun testUnitTests() {
        val app = project.getSubproject(":app")
        TestFileUtils.appendToFile(
            app.buildFile,
            """
                dependencies {
                  testImplementation "junit:junit:4.12"
                }
                """.trimIndent()
        )
        app.file("src/test/kotlin/AppFooTest.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.application.test

                        class AppFooTest {
                          @org.junit.Test
                          fun testSample() {}
                        }
                        """.trimIndent()
                )
            }

        app.executor().run(":app:testDebug")
        val testResults =
            app.buildDir
                .resolve(
                    "test-results/testDebugUnitTest/TEST-com.foo.application.test.AppFooTest.xml"
                )
        PathSubject.assertThat(testResults).exists()
    }

    /**
     * Test that general built-in Kotlin support is compatible with Kotlin support for screenshot
     * testing, in contrast to [BuiltInKotlinForScreenshotTestTest], which tests Kotlin support for
     * screenshot testing in isolation
     */
    @Test
    fun testWithScreenshotTestEnabled() {
        val app = project.getSubproject(":app")
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_SCREENSHOT_TEST.propertyName}=true"
        )
        TestFileUtils.appendToFile(
            app.buildFile,
            """
                android.experimentalProperties["${SCREENSHOT_TEST.key}"] = true

                dependencies {
                    screenshotTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:$KOTLIN_VERSION_FOR_TESTS")
                }
                """.trimIndent()
        )
        app.file("src/screenshotTest/kotlin/AppScreenshotTestFoo.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.application
                    class AppScreenshotTestFoo
                    """.trimIndent()
            )
        }
        app.getMainSrcDir("java").resolve("AppFoo.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.application
                    class AppFoo
                    """.trimIndent()
            )
        }
        app.executor().run(":app:compileDebugScreenshotTestKotlin")
        app.executor().run(":app:assembleDebug")
    }

    @Test
    fun testInternalModifierAccessibleFromTests() {
        val app = project.getSubproject(":app")
        app.getMainSrcDir("java")
            .resolve("AppFoo.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.application
                        class AppFoo {
                          internal fun bar() {}
                        }
                        """.trimIndent()
                )
            }
        app.file("src/test/AppFooTest.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.application
                        class AppFooTest {
                          init { AppFoo().bar() }
                        }
                        """.trimIndent()
                )
            }
        app.executor().run(":app:assembleDebugUnitTest")
    }

    @Test
    fun testAppCompilesAgainstKotlinClassesFromDependency() {
        val lib = project.getSubproject(":lib")
        lib.getMainSrcDir("java")
            .resolve("LibFoo.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.library
                        open class LibFoo
                        """.trimIndent()
                )
            }
        val app = project.getSubproject(":app")
        app.getMainSrcDir("kotlin")
            .resolve("AppFoo.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.application
                        class AppFoo: com.foo.library.LibFoo()
                        """.trimIndent()
                )
            }
        app.executor().run(":app:assembleDebug")
        app.getApk(DEBUG).use {
            assertThat(it).hasClass("Lcom/foo/application/AppFoo;")
            assertThat(it).hasClass("Lcom/foo/library/LibFoo;")
        }
    }

    @Test
    fun testKotlinAndJavaCrossReferences() {
        val app = project.getSubproject(":app")
        app.getMainSrcDir("java")
            .let {
                it.mkdirs()
                it.resolve("AppJavaFoo.java")
                    .writeText(
                        """
                            package com.foo.application;
                            public class AppJavaFoo {
                              String prop = new AppKotlinBar().getAppJavaFooClassName();
                            }
                            """.trimIndent()
                    )
                it.resolve("AppKotlinFoo.kt")
                    .writeText(
                        """
                            package com.foo.application
                            class AppKotlinFoo: AppJavaFoo()
                            """.trimIndent()
                    )
                it.resolve("AppKotlinBar.kt")
                    .writeText(
                        """
                            package com.foo.application
                            class AppKotlinBar {
                              val appJavaFooClassName = AppJavaFoo::class.java.name
                            }
                            """.trimIndent()
                    )
            }
        app.executor().run(":app:assembleDebug")
        app.getApk(DEBUG).use {
            assertThat(it).hasClass("Lcom/foo/application/AppJavaFoo;")
            assertThat(it).hasClass("Lcom/foo/application/AppKotlinFoo;")
            assertThat(it).hasClass("Lcom/foo/application/AppKotlinBar;")
        }
    }

    @Test
    fun testKotlinStdLibMissing() {
        for (moduleName in listOf(":app", ":lib")) {
            val module = project.getSubproject(moduleName)
            module.buildFile
                .let { buildFile ->
                    TestFileUtils.searchAndReplace(
                        buildFile,
                        "api(\"org.jetbrains.kotlin:kotlin-stdlib",
                        "//api(\"org.jetbrains.kotlin:kotlin-stdlib"
                    )
                }
        }
        val app = project.getSubproject(":app")
        app.getMainSrcDir("java")
            .resolve("AppFoo.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.application
                        class AppFoo
                        """.trimIndent()
                )
            }
        val result = app.executor().expectFailure().run(":app:assembleDebug")
        result.assertErrorContains("Kotlin standard library is missing")
    }

    @Test
    fun testErrorWhenBuiltInKotlinSupportAndKagpUsedInSameModule() {
        val app = project.getSubproject(":app")
        with(app.buildFile) {
            val current = readText()

            writeText(
                """
                    plugins {
                      id("${PluginType.KOTLIN_ANDROID.id}")
                    }
                    $current
                """.trimIndent()
            )
        }

        val result = app.executor().expectFailure().run(":app:assembleDebug")
        result.assertErrorContains(
            "The \"org.jetbrains.kotlin.android\" plugin has been applied, but it is not compatible"
        )
    }

    @Test
    fun testBuiltInKotlinSupportAndKagpUsedInDifferentModules() {
        val lib = project.getSubproject(":lib")
        TestFileUtils.searchAndReplace(
            lib.buildFile,
            PluginType.ANDROID_BUILT_IN_KOTLIN.id,
            PluginType.KOTLIN_ANDROID.id
        )
        TestFileUtils.appendToFile(
            lib.buildFile,
            """
                android.kotlinOptions.jvmTarget = "1.8"
                """.trimIndent()
        )
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
        val app = project.getSubproject(":app")
        app.getMainSrcDir("kotlin")
            .resolve("AppFoo.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.application
                        val l = com.foo.library.LibFoo()
                        """.trimIndent()
                )
            }
        project.executor().withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .run(":app:assembleDebug")
        app.getApk(DEBUG).use {
            assertThat(it).hasClass("Lcom/foo/application/AppFooKt;")
            assertThat(it).hasClass("Lcom/foo/library/LibFoo;")
        }
    }
}

internal val builtInKotlinSupportDependencies =
    """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:$KOTLIN_VERSION_FOR_TESTS")
            androidTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:$KOTLIN_VERSION_FOR_TESTS")
            testImplementation("org.jetbrains.kotlin:kotlin-stdlib:$KOTLIN_VERSION_FOR_TESTS")
        }
        """.trimIndent()
