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
import com.android.build.gradle.integration.common.fixture.app.AnnotationProcessorLib
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.dsl.ModulePropertyKey.BooleanWithDefault.SCREENSHOT_TEST
import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KAPT_PLUGIN_ID
import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID
import com.android.build.gradle.internal.utils.KOTLIN_ANDROID_PLUGIN_ID
import com.android.build.gradle.internal.utils.KOTLIN_KAPT_PLUGIN_ID
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BuiltInKaptForScreenshotTestTest {

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
            .withBuiltInKotlinSupport(true)
            .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_SCREENSHOT_TEST.propertyName}=true"
        )
        val app = project.getSubproject(":app")
        app.buildFile.appendText(
            """
                android.experimentalProperties["${SCREENSHOT_TEST.key}"] = true

                $builtInKotlinSupportDependencies
                dependencies {
                    screenshotTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
                    screenshotTestImplementation project(':lib')
                    kaptScreenshotTest project(':lib-compiler')
                }
                """.trimIndent()
        )
        with(app.projectDir.resolve("src/screenshotTest/java/com/example/Foo.kt")) {
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
        project.executor().run("app:compileDebugScreenshotTestJavaWithJavac")
        val kaptGeneratedTestDir =
            app.buildDir.resolve("generated/source/kapt/screenshotTest/debug/com/example")
        PathSubject.assertThat(kaptGeneratedTestDir.resolve("FooStringValue.java")).exists()
        PathSubject.assertThat(kaptGeneratedTestDir.resolve("Foo\$\$InnerClass.java")).exists()
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
            .run("app:compileDebugScreenshotTestJavaWithJavac")
        val kaptGeneratedTestDir =
            app.buildDir.resolve("generated/source/kapt/screenshotTest/debug/com/example")
        PathSubject.assertThat(kaptGeneratedTestDir.resolve("FooStringValue.java")).exists()
        PathSubject.assertThat(kaptGeneratedTestDir.resolve("Foo\$\$InnerClass.java")).exists()
    }
}
