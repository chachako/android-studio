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

import com.android.build.gradle.integration.common.fixture.GradleProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.ANDROIDTEST_DEBUG
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.DEBUG
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.VERSION_CATALOG
import com.android.build.gradle.integration.common.fixture.app.AnnotationProcessorLib
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.TaskManager.Companion.COMPOSE_UI_VERSION
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KAPT_PLUGIN_ID
import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.SyncIssue
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject
import com.android.utils.appendCapitalized
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BuiltInKaptTest {

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
        ).withBuiltInKotlinSupport(true)
            .withKotlinGradlePlugin(true)
            .create()

    @Before
    fun setUp() {
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
        app.buildFile.appendText(
            """
                $builtInKotlinSupportDependencies
                dependencies {
                    api project(':lib')
                    kapt project(':lib-compiler')
                    kaptAndroidTest project(':lib-compiler')
                    kaptTest project(':lib-compiler')
                }
                """.trimIndent()
        )
        with(app.mainSrcDir.resolve("com/example/Foo.kt")) {
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
        with(app.mainSrcDir.resolve("com/example/JavaFoo.java")) {
            parentFile.mkdirs()

            writeText(
                """
                    package com.example;

                    import com.example.annotation.ProvideString;

                    @ProvideString
                    class JavaFoo {}
                    """.trimIndent()
            )
        }
        with(app.projectDir.resolve("src/androidTest/java/com/example/AndroidTestFoo.kt")) {
            parentFile.mkdirs()

            writeText(
                """
                    package com.example

                    import com.example.annotation.ProvideString

                    @ProvideString
                    class AndroidTestFoo
                    """.trimIndent()
            )
        }
        with(app.projectDir.resolve("src/test/java/com/example/TestFoo.kt")) {
            parentFile.mkdirs()

            writeText(
                """
                    package com.example

                    import com.example.annotation.ProvideString

                    @ProvideString
                    class TestFoo
                    """.trimIndent()
            )
        }
    }

    @Test
    fun testAnnotationProcessing() {
        project.executor()
            .run(
                "app:assembleDebug",
                "app:assembleDebugAndroidTest",
                "app:compileDebugUnitTestJavaWithJavac"
            )
        val app = project.getSubproject(":app")
        app.getApk(DEBUG).use { apk ->
            assertThat(apk).hasClass("Lcom/example/FooStringValue;")
            assertThat(apk).hasClass("Lcom/example/Foo\$\$InnerClass;")
            assertThat(apk).hasClass("Lcom/example/JavaFooStringValue;")
            assertThat(apk).hasClass("Lcom/example/JavaFoo\$\$InnerClass;")
        }
        app.getApk(ANDROIDTEST_DEBUG).use { apk ->
            assertThat(apk).hasClass("Lcom/example/AndroidTestFooStringValue;")
            assertThat(apk).hasClass("Lcom/example/AndroidTestFoo\$\$InnerClass;")
        }
        val kaptGeneratedTestDir =
            app.buildDir.resolve("generated/source/kapt/test/debug/com/example")
        PathSubject.assertThat(kaptGeneratedTestDir.resolve("TestFooStringValue.java")).exists()
        PathSubject.assertThat(kaptGeneratedTestDir.resolve("TestFoo\$\$InnerClass.java")).exists()
    }

    @Test
    fun testWithAnnotationProcessorConfiguration() {
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "kapt project(':lib-compiler')",
            "annotationProcessor project(':lib-compiler')"
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "kaptAndroidTest project(':lib-compiler')",
            "androidTestAnnotationProcessor project(':lib-compiler')"
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "kaptTest project(':lib-compiler')",
            "testAnnotationProcessor project(':lib-compiler')"
        )

        project.executor()
            .run(
                "app:assembleDebug",
                "app:assembleDebugAndroidTest",
                "app:compileDebugUnitTestJavaWithJavac"
            )
        val app = project.getSubproject(":app")
        app.getApk(DEBUG).use { apk ->
            assertThat(apk).hasClass("Lcom/example/FooStringValue;")
            assertThat(apk).hasClass("Lcom/example/Foo\$\$InnerClass;")
            assertThat(apk).hasClass("Lcom/example/JavaFooStringValue;")
            assertThat(apk).hasClass("Lcom/example/JavaFoo\$\$InnerClass;")
        }
        app.getApk(ANDROIDTEST_DEBUG).use { apk ->
            assertThat(apk).hasClass("Lcom/example/AndroidTestFooStringValue;")
            assertThat(apk).hasClass("Lcom/example/AndroidTestFoo\$\$InnerClass;")
        }
        val kaptGeneratedTestDir =
            app.buildDir.resolve("generated/source/kapt/test/debug/com/example")
        PathSubject.assertThat(kaptGeneratedTestDir.resolve("TestFooStringValue.java")).exists()
        PathSubject.assertThat(kaptGeneratedTestDir.resolve("TestFoo\$\$InnerClass.java")).exists()
    }

    @Test
    fun testGeneratedSourcesModel() {
        val appModel =
            project.modelV2()
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchModels()
                .container
                .getProject(":app")
        appModel.androidProject!!.variants.forEach { variant ->
            val kaptTaskName = "kapt".appendCapitalized(variant.name, "kotlin")
            val kaptGeneratedClasses =
                InternalArtifactType.BUILT_IN_KAPT_CLASSES_DIR
                    .getOutputDir(project.getSubproject(":app").buildDir)
                    .resolve("${variant.name}/$kaptTaskName")
            val kaptGeneratedJava =
                project.getSubproject(":app")
                    .buildDir
                    .resolve("generated/source/kapt/${variant.name}")
            val kaptGeneratedKotlin =
                project.getSubproject(":app")
                    .buildDir
                    .resolve("generated/source/kaptKotlin/${variant.name}")

            assertThat(variant.mainArtifact.classesFolders).contains(kaptGeneratedClasses)
            assertThat(variant.mainArtifact.generatedClassPaths.values).contains(
                kaptGeneratedClasses
            )
            assertThat(variant.mainArtifact.generatedSourceFolders).containsAtLeast(
                kaptGeneratedJava,
                kaptGeneratedKotlin
            )

            project.executor().run(":app:$kaptTaskName")
            PathSubject.assertThat(kaptGeneratedClasses).exists()
            PathSubject.assertThat(kaptGeneratedJava).exists()
            PathSubject.assertThat(kaptGeneratedKotlin).exists()
        }
    }

    @Test
    fun testErrorWhenBuiltInKaptAndExternalKaptUsedInSameModule() {
        val app = project.getSubproject(":app")
        with(app.buildFile) {
            val current = readText()

            writeText(
                """
                    plugins {
                      id("${PluginType.KAPT.id}")
                    }
                    $current
                """.trimIndent()
            )
        }

        val result = app.executor().expectFailure().run(":app:assembleDebug")
        result.assertErrorContains(
            "The \"org.jetbrains.kotlin.kapt\" plugin has been applied, but it is not compatible"
        )
    }

    @Test
    fun testErrorWhenBuiltInKaptAppliedWithoutBuiltInKotlin() {
        val app = project.getSubproject(":app")
        TestFileUtils.searchAndReplace(
            app.buildFile,
            "apply plugin: '$ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID'",
            ""
        )

        val result = app.executor().expectFailure().run(":app:assembleDebug")
        result.assertErrorContains("The \"$ANDROID_BUILT_IN_KAPT_PLUGIN_ID\" plugin requires the")
    }

    /**
     * Test to ensure that the built-in KaptGenerateStubs task handles basic compose code
     */
    @Test
    fun testBuiltInKaptWithCompose() {
        TestFileUtils.searchAndReplace(
            project.projectDir.parentFile.resolve(VERSION_CATALOG),
            "version('kotlinVersion', '${TestUtils.KOTLIN_VERSION_FOR_TESTS}')",
            "version('kotlinVersion', '${TestUtils.KOTLIN_VERSION_FOR_COMPOSE_TESTS}')"
        )
        val app = project.getSubproject(":app")
        TestFileUtils.appendToFile(
            app.buildFile,
            """
                android {
                    defaultConfig {
                        minSdk = 24
                    }
                    buildFeatures {
                        compose true
                    }
                    composeOptions {
                        kotlinCompilerExtensionVersion = "${TestUtils.COMPOSE_COMPILER_FOR_TESTS}"
                    }
                }

                dependencies {
                    implementation("androidx.compose.ui:ui-tooling:$COMPOSE_UI_VERSION")
                    implementation("androidx.compose.material:material:$COMPOSE_UI_VERSION")
                }
            """.trimIndent()
        )
        val composeSourceFile = app.mainSrcDir.resolve("com/example/Compose.kt")
        composeSourceFile.parentFile.mkdirs()
        TestFileUtils.appendToFile(
            composeSourceFile,
            """
                package com.example

                import androidx.compose.foundation.layout.Column
                import androidx.compose.material.Text
                import androidx.compose.runtime.Composable

                @Composable
                fun Compose() {
                    Column {
                        Text(text = "Hello World")
                    }
                }

            """.trimIndent()
        )

        project.executor().with(BooleanOption.USE_ANDROID_X, true).run("app:assembleDebug")
    }
}
