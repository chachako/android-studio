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

package com.android.build.gradle.integration.library

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExpandArtProfileWildcardsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val app = HelloWorldApp.forPluginWithNamespace(
        "com.android.application", "com.example.app")
    private val lib =
        HelloWorldApp.forPluginWithNamespace(
            "com.android.library", "com.example.lib")

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .subproject(":lib", lib)
                .dependency(app, lib)
                .build()
        ).create()

    @Test
    fun validateNoOpWithoutMinifyEnabled() {
        // Do not specify minifyEnabled in build file, which disables it
        // The expand art profile wildcards task should not run
        val app = project.getSubproject("app").also {
            it.buildFile.appendText(
                """
                    android {
                        defaultConfig {
                            minSdkVersion = 33
                        }
                    }
                """.trimIndent()
            )
        }

        FileUtils.createFile(app.file("src/main/baselineProfiles/file.txt"), "L*;")

        val result = project.executor().run(":app:assembleRelease", ":app:bundleRelease")

        Truth.assertThat(result.didWorkTasks)
            .doesNotContain(":app:expandReleaseArtProfileWildcards")

        val mergedFile = FileUtils.join(
            app.buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.MERGED_ART_PROFILE.getFolderName(),
            "release",
            SdkConstants.FN_ART_PROFILE)

        // Validate that art profile is the same as the input, without wildcards expanded
        Truth.assertThat(mergedFile.readText()).isEqualTo("L*;")
    }

    @Test
    fun testExpandArtProfileWildcardsTaskWithR8Rewriting() {
        testExpandWildcardsTask(r8Rewriting = true, expectedArtProfile =
            """
                Lcom/example/app/HelloWorld;
                La/a;
                Lcom/example/lib/HelloWorld;
                Lb/a;

            """.trimIndent())
    }

    @Test
    fun testExpandArtProfileWildcardsTaskWithoutR8Rewriting() {
        testExpandWildcardsTask(r8Rewriting = false, expectedArtProfile =
            """
                Lcom/example/app/HelloWorld;
                Lcom/example/app/R${'$'}id;
                Lcom/example/app/R${'$'}layout;
                Lcom/example/app/R${'$'}string;
                Lcom/example/app/R;
                Lcom/example/lib/Foo;
                Lcom/example/lib/HelloWorld;
                Lcom/example/lib/R${'$'}id;
                Lcom/example/lib/R${'$'}layout;
                Lcom/example/lib/R${'$'}string;
                Lcom/example/lib/R;

            """.trimIndent())
    }

    private fun testExpandWildcardsTask(r8Rewriting: Boolean, expectedArtProfile: String) {
        // Set minifyEnabled to true so ExpandArtProfileWildcardsTask runs
        val app = project.getSubproject("app").also {
            it.buildFile.appendText(
                """
                    android {
                        defaultConfig {
                            minSdkVersion = 33
                        }
                    }

                    androidComponents {
                        beforeVariants(selector().withBuildType("release"), { variant ->
                            variant.setMinifyEnabled(true);
                        })
                        onVariants(selector().withName("release"), { variant ->
                            variant.experimentalProperties.put(
                                "android.experimental.art-profile-r8-rewriting",
                                $r8Rewriting
                            )
                        })
                    }
                """.trimIndent()
            )
        }

        // Add a file whose class name will be included in the output after wildcards are expanded
        File(project.getSubproject("lib").mainSrcDir, "com/example/lib/Foo.java").writeText(
            """
                package com.example.lib;
                public class Foo {
                    public int m(int i, int j) {
                        return i;
                    }
                }
            """.trimIndent()
        )

        // Add a wildcard that matches to any amount of characters in a class or method name,
        // including package separator ('/')
        FileUtils.createFile(app.file("src/main/baselineProfiles/file.txt"), "L**;")

        val result = project.executor().run(":app:assembleRelease", ":app:bundleRelease")

        Truth.assertThat(result.failedTasks).isEmpty()
        Truth.assertThat(result.didWorkTasks).contains(":app:expandReleaseArtProfileWildcards")

        val mergedFile = FileUtils.join(
            app.buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.MERGED_ART_PROFILE.getFolderName(),
            "release",
            SdkConstants.FN_ART_PROFILE
        )

        // The output contains the expansion of the following sources:
        // - App classes (HelloWorld class)
        // - App resources (R.jar)
        // - Lib classes (classes.jar containing HelloWorld and Foo classes)
        Truth.assertThat(mergedFile.readText()).isEqualTo(expectedArtProfile)
    }
}
