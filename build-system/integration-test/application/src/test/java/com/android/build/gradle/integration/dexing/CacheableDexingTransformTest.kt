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

package com.android.build.gradle.integration.dexing

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils.searchAndReplace
import org.gradle.api.JavaVersion
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheableDexingTransformTest {

    @get:Rule
    val buildCacheDir = TemporaryFolder()

    @get:Rule
    val projectCopy1 = createProject("projectCopy1")

    @get:Rule
    val projectCopy2 = createProject("projectCopy2")

    private fun createProject(name: String) = createGradleProject(name) {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                defaultCompileSdk()
                minSdk = 24
            }
            dependencies {
                implementation(project(":lib"))
            }
        }
        subProject(":lib") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }
            }
            addFile(
                "src/main/java/com/example/lib/JavaClassWithNestedClass.java",
                """
                package com.example.lib;
                public class JavaClassWithNestedClass {
                    public class NestedClass {
                        // This line will be changed later
                    }
                }
                """.trimIndent()
            )
        }
    }

    @Before
    fun setUp() {
        listOf(projectCopy1, projectCopy2).forEach { project ->
            project.settingsFile.appendText("\n" +
                """
                |buildCache {
                |    local {
                |        directory = "${buildCacheDir.root.path.replace("\\", "\\\\")}"
                |    }
                |}
                """.trimMargin()
            )
        }
    }

    @Test
    fun `Bug 266599585 - test incremental build after cache hit`() {
        val result1 =
            projectCopy1.executor().withArgument("--build-cache").run(":app:mergeLibDexDebug")
        assertThat(result1.getTask(":app:mergeLibDexDebug")).didWork()
        result1.assertOutputContains("Running dexing transform non-incrementally")

        // Building the same project from a different location should get a cache hit
        val result2 =
            projectCopy2.executor().withArgument("--build-cache").run(":app:mergeLibDexDebug")
        assertThat(result2.getTask(":app:mergeLibDexDebug")).wasFromCache()
        result2.assertOutputDoesNotContain("Running dexing transform")

        // Make a change to a nested class (regression test for bug 266599585)
        searchAndReplace(
            File(
                projectCopy2.getSubproject(":lib").mainSrcDir,
                "com/example/lib/JavaClassWithNestedClass.java"
            ),
            "// This line will be changed later",
            "public void newMethodInNestedClass() { }"
        )

        // The next build after cache hit should be incremental
        val result3 =
            projectCopy2.executor().withArgument("--build-cache").run(":app:mergeLibDexDebug")
        assertThat(result3.getTask(":app:mergeLibDexDebug")).didWork()
        result3.assertOutputContains("Running dexing transform incrementally")
    }

}
