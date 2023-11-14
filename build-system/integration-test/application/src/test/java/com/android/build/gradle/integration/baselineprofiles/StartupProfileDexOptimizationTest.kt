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

package com.android.build.gradle.integration.baselineprofiles

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.LoggingLevel
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class StartupProfileDexOptimizationTest(
    // should the startup profile dex optimization be turned on ?
    private val startupProfileDexOptimization: Boolean,
    // should the startup profile file be created ?
    private val includeStartupProfile: Boolean
) {
    companion object {
        @Parameterized.Parameters(
            name = "withStartupProfileDexOptimization_{0}_withStartupProfile_{1}"
        )
        @JvmStatic
        fun setups() =
            listOf(
                arrayOf(true, true),
                arrayOf(false, true),
                arrayOf(true, false),
                arrayOf(false, false),
            )
    }

    private val app =
        HelloWorldApp.forPluginWithNamespace("com.android.application", "com.example.app").also {
            it.addFile(
            "src/main/java/com/example/app/Foo.java",
            """
                package com.example.app;
                public class Foo {
                    public void foo() {
                        System.out.println("foo !");
                    }
                }
                """.trimIndent()
            )
            it.addFile(
                "src/main/java/com/example/app/Bar.java",
                """
                package com.example.app;
                public class Bar {
                    public void bar() {
                        System.out.println("bar !");
                    }
                }
                """.trimIndent()
            )
            if (includeStartupProfile) {
                it.addFile(
                    "src/main/baselineProfiles/startup-prof.txt",
                    """
                    Lcom/example/app/Foo;->foo()V
                """.trimIndent()
                )
            }

            val rewrittenFile = it.getFile("src/main/java/com/example/app/HelloWorld.java")
                .rewriteContent(
                """
                package com.example.app;

                import android.app.Activity;
                import android.os.Bundle;

                public class HelloWorld extends Activity {
                    /** Called when the activity is first created. */
                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.main);
                        // onCreate
                        new Foo().foo();
                        new Bar().bar();
                    }
                }
                """.trimIndent())
            it.removeFile("src/main/java/com/example/app/HelloWorld.java")
            it.addFile(rewrittenFile)
            it.appendToBuild("""
                    android.defaultConfig.minSdkVersion = 26
                    android.buildTypes.release.minifyEnabled = true
                    androidComponents {
                        // and turn on the feature if necessary
                        onVariants(selector().withName("release"), { variant ->
                            variant.experimentalProperties.put(
                                "${ModulePropertyKey.BooleanWithDefault.R8_DEX_STARTUP_OPTIMIZATION.key}",
                                $startupProfileDexOptimization
                            )
                        })
                    }
            """.trimIndent())
        }

    @JvmField
    @Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .build()
            ).create()

    @Test
    fun testStartupProfile() {
        // if feature is turned on but no startup baseline profile is provided, debug log should
        // be present
        val expectLog = startupProfileDexOptimization && !includeStartupProfile
        val buildResult =
            project.executor().withLoggingLevel(LoggingLevel.DEBUG).run("assembleRelease")
        if (expectLog) {
            assertThat(buildResult.didWorkTasks).contains(":app:minifyReleaseWithR8")
            ScannerSubject.assertThat(buildResult.stdout).contains(
                "Dex optimization based on startup profile is enabled, but there are no input " +
                "baseline profiles found in the baselineProfiles sources.")
            return
        }
        // every thing else should succeed.
        val apk = project.getSubproject("app").getApk(
            GradleTestProject.ApkType.RELEASE
        )
        // if dex optimization is turned on, there should be 2 dexes, otherwise only one since
        // everything fits into a single one.
        assertThat(apk.allDexes).hasSize(if (startupProfileDexOptimization) 2 else 1)
        // if dex optimization is turned on, there should only be 1 class in the main dex since our
        // startup profile contained only Foo. When it is off, the main dex files will contain all
        // 4 classes.
        assertThat(apk.mainDexFile.get().classes)
            .hasSize(if (startupProfileDexOptimization) 1 else 4)
    }

    @Test
    fun testVariantStartupProfile() {
        // To test if the variant startup profile source is working, only run when dex optimization
        // is enabled and main startup profile is not included
        if (!startupProfileDexOptimization || includeStartupProfile) return

        FileUtils.createFile(
            project.getSubproject("app")
                .file("src/release/baselineProfiles/startup-prof.txt"),
            """
                Lcom/example/app/Foo;->foo()V
            """.trimIndent()
        )
        project.executor().run("assembleRelease")
        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.RELEASE)
        // dex optimization is turned on so there should be 2 dexes
        assertThat(apk.allDexes).hasSize(2)
        // there should only be 1 class in the main dex since our startup profile contains only Foo
        assertThat(apk.mainDexFile.get().classes).hasSize(1)
    }

    @Test
    fun testGeneratedStartupProfile() {
        // To test if the variant startup profile source is working, only run when dex optimization
        // is enabled and main startup profile is not included
        if (!startupProfileDexOptimization || includeStartupProfile) return

        // Add the generated baseline profile as a source set
        // Normally, the baseline profile generator will do this, but this test is just validating
        // that the startup profile is collected from the generated folder when it has already been
        // generated as a source set
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                android.sourceSets {
                    getByName("main") {
                        baselineProfiles.srcDir("src/release/generated/baselineProfiles")
                    }
                }
            """.trimIndent()
        )

        FileUtils.createFile(
            project.getSubproject("app")
                .file("src/release/generated/baselineProfiles/startup-prof.txt"),
            """
                Lcom/example/app/Foo;->foo()V
            """.trimIndent()
        )
        project.executor().run("assembleRelease")
        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.RELEASE)
        // dex optimization is turned on so there should be 2 dexes
        assertThat(apk.allDexes).hasSize(2)
        // there should only be 1 class in the main dex since our startup profile contains only Foo
        assertThat(apk.mainDexFile.get().classes).hasSize(1)
    }

    @Test
    fun testMultipleStartupProfiles() {
        // To test multiple startup profile sources, only run when dex optimization is enabled and
        // main startup profile is included
        if (!startupProfileDexOptimization || !includeStartupProfile) return

        // Add the generated baseline profile as a source set to simulate the baseline profile
        // generator plugin
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                android.sourceSets {
                    getByName("main") {
                        baselineProfiles.srcDir("src/release/generated/baselineProfiles")
                    }
                }
            """.trimIndent()
        )
        FileUtils.createFile(
            project.getSubproject("app")
                .file("src/release/generated/baselineProfiles/startup-prof.txt"),
            """
                Lcom/example/app/Bar;->bar()V
            """.trimIndent()
        )
        project.executor().run("assembleRelease")
        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.RELEASE)
        // dex optimization is turned on so there should be 2 dexes
        assertThat(apk.allDexes).hasSize(2)
        // there are two startup profiles with one class each, meaning the classes size should be 2
        assertThat(apk.mainDexFile.get().classes).hasSize(2)
    }
}
