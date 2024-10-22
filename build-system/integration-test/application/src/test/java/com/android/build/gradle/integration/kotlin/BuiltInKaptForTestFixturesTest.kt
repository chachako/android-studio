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
import com.android.build.gradle.integration.common.fixture.GradleProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.VERSION_CATALOG
import com.android.build.gradle.integration.common.fixture.app.AnnotationProcessorLib
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KAPT_PLUGIN_ID
import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID
import com.android.build.gradle.internal.utils.KOTLIN_ANDROID_PLUGIN_ID
import com.android.build.gradle.internal.utils.KOTLIN_KAPT_PLUGIN_ID
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils
import com.android.testutils.apk.Aar
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BuiltInKaptForTestFixturesTest(private val kotlinVersion: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "kotlinVersion_{0}")
        fun parameters() = listOf(TestUtils.KOTLIN_VERSION_FOR_TESTS, "1.9.22")
    }

    @Rule
    @JvmField
    val project: GradleTestProject =
        GradleTestProject.builder().fromTestApp(
            MultiModuleTestProject(
                mapOf<String, GradleProject>(
                    ":app" to HelloWorldApp.forPlugin("com.android.application"),
                    ":lib" to AnnotationProcessorLib.createLibrary(),
                    ":lib-compiler" to AnnotationProcessorLib.createCompiler()
                )
            )
        ).withKotlinGradlePlugin(true)
            .withBuiltInKotlinSupport(kotlinVersion == TestUtils.KOTLIN_VERSION_FOR_TESTS)
            .create()

    @Before
    fun setUp() {
        TestFileUtils.searchAndReplace(
            project.projectDir.parentFile.resolve(VERSION_CATALOG),
            "version('kotlinVersion', '${TestUtils.KOTLIN_VERSION_FOR_TESTS}')",
            "version('kotlinVersion', '$kotlinVersion' )"
        )
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT.propertyName}=true"
        )
        val app = project.getSubproject(":app")
        app.buildFile.appendText(
            """
                android.testFixtures.enable = true

                $builtInKotlinSupportDependencies
                dependencies {
                    testFixturesImplementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
                    testFixturesImplementation project(':lib')
                    kaptTestFixtures project(':lib-compiler')
                }
                """.trimIndent()
        )
        with(app.projectDir.resolve("src/testFixtures/java/com/example/Foo.kt")) {
            parentFile.mkdirs()
            writeText(
                """
                    package com.example

                    import com.example.annotation.ProvideString

                    @ProvideString
                    class Foo
                    """.trimIndent()
            )
        }
    }

    @Test
    fun testAnnotationProcessingWithAgpKaptPlugin() {
        Assume.assumeTrue(kotlinVersion == TestUtils.KOTLIN_VERSION_FOR_TESTS)
        val app = project.getSubproject(":app")
        TestFileUtils.searchAndReplace(
            app.buildFile,
            "apply plugin: 'com.android.application'",
            """
                apply plugin: 'com.android.application'
                apply plugin: '$ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID'
                apply plugin: '$ANDROID_BUILT_IN_KAPT_PLUGIN_ID'
                """.trimIndent(),
        )
        project.executor()
            .withConfigurationCaching(
                if (kotlinVersion == TestUtils.KOTLIN_VERSION_FOR_TESTS) {
                    BaseGradleExecutor.ConfigurationCaching.PROJECT_ISOLATION
                } else {
                    BaseGradleExecutor.ConfigurationCaching.ON
                }
            )
            .run("app:assembleDebugTestFixtures")
        val aar = app.outputDir.resolve("aar").listFiles()!!.single()
        Aar(aar).use {
            assertThat(it).containsMainClass("Lcom/example/FooStringValue;")
            assertThat(it).containsMainClass("Lcom/example/Foo\$\$InnerClass;")
        }
    }

    @Test
    fun testAnnotationProcessingWithJetbrainsKaptPlugin() {
        val app = project.getSubproject(":app")
        TestFileUtils.searchAndReplace(
            app.buildFile,
            "apply plugin: 'com.android.application'",
            """
                apply plugin: 'com.android.application'
                apply plugin: '$KOTLIN_ANDROID_PLUGIN_ID'
                apply plugin: '$KOTLIN_KAPT_PLUGIN_ID'

                kotlin {
                    jvmToolchain(17)
                }
                """.trimIndent(),
        )
        project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            // Version 1.9.22 of the jetbrains KAPT plugin uses deprecated Gradle features
            .withFailOnWarning(kotlinVersion == TestUtils.KOTLIN_VERSION_FOR_TESTS)
            .run("app:assembleDebugTestFixtures")
        val aar = app.outputDir.resolve("aar").listFiles()!!.single()
        Aar(aar).use {
            assertThat(it).containsMainClass("Lcom/example/FooStringValue;")
            assertThat(it).containsMainClass("Lcom/example/Foo\$\$InnerClass;")
        }
    }

    @Test
    fun testKaptDslWithJetbrainsKaptPlugin() {
        val app = project.getSubproject(":app")
        TestFileUtils.searchAndReplace(
            app.buildFile,
            "apply plugin: 'com.android.application'",
            """
                apply plugin: 'com.android.application'
                apply plugin: '$KOTLIN_ANDROID_PLUGIN_ID'
                apply plugin: '$KOTLIN_KAPT_PLUGIN_ID'

                kotlin {
                    jvmToolchain(17)
                }

                kapt {
                    useBuildCache = true
                }
                """.trimIndent(),
        )
        TestFileUtils.appendToFile(project.gradlePropertiesFile, "org.gradle.caching=true")

        val executor =
            project.executor()
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
                // Version 1.9.22 of the jetbrains KAPT plugin uses deprecated Gradle features
                .withFailOnWarning(kotlinVersion == TestUtils.KOTLIN_VERSION_FOR_TESTS)
        // test for caching when useBuildCache = true
        assertThat(
            executor.run("app:assembleDebugTestFixtures").didWorkTasks
        ).contains(":app:kaptDebugTestFixturesKotlin")
        assertThat(
            executor.run("clean", "app:assembleDebugTestFixtures").fromCacheTasks
        ).contains(":app:kaptDebugTestFixturesKotlin")

        // test no caching when useBuildCache = false
        TestFileUtils.searchAndReplace(
            app.buildFile,
            "useBuildCache = true",
            "useBuildCache = false"
        )

        executor.run("app:assembleDebugTestFixtures")
        assertThat(
            executor.run("clean", "app:assembleDebugTestFixtures").fromCacheTasks
        ).doesNotContain(":app:kaptDebugTestFixturesKotlin")
    }
}
