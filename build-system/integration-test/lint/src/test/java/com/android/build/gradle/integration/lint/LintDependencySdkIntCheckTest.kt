/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.ANDROIDX_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption.LINT_ANALYSIS_PER_COMPONENT
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Test that partial results from module dependencies are passed to lint during analysis.
 */
class LintDependencySdkIntCheckTest {

    private val app = MinimalSubProject.app("com.example.app")
    private val lib = MinimalSubProject.lib("com.example.lib")

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":lib", lib)
                    .dependency(app, lib)
                    .build()
            ).create()

    private val libSourceFile by lazy {
        project.getSubproject(":lib").file("src/main/java/com/example/bar/Lib.java")
    }

    @Before
    fun before() {
        project.getSubproject(":app")
            .buildFile
            .appendText(
                """
                    android {
                        lintOptions {
                            abortOnError false
                            textOutput file("lint-results.txt")
                            checkDependencies false
                        }
                    }

                    dependencies {
                        implementation "androidx.annotation:annotation:$ANDROIDX_VERSION"
                    }
                """.trimIndent()
            )

        project.gradlePropertiesFile.appendText("\nandroid.useAndroidX=true\n")

        val appSourceFile =
            project.getSubproject(":app").file("src/main/java/com/example/foo/App.java")
        appSourceFile.parentFile.mkdirs()
        appSourceFile.writeText(
            //language=JAVA
            """
                package com.example.foo;

                import androidx.annotation.RequiresApi;
                import com.example.bar.Lib;

                public class App {

                    @RequiresApi(26)
                    public void foo() {}

                    public void bar() {
                        if (Lib.isOk()) {
                            foo();
                        }
                    }
                }
            """.trimIndent()
        )

        libSourceFile.parentFile.mkdirs()
        libSourceFile.writeText(
            //language=JAVA
            """
                package com.example.bar;

                import android.os.Build;

                public class Lib {
                    public static boolean isOk() {
                        return Build.VERSION.SDK_INT >= 25;
                    }

                }
            """.trimIndent()
        )
    }

    @Test
    fun testDependencySdkIntCheck() {
        // First check that we get a NewApi lint error, as expected, when isOK() only checks that
        // the API level is at least 25.
        getExecutor().run(":app:lintDebug")
        assertThat(project.buildResult.getTask(":lib:lintAnalyzeDebug")).didWork()
        val reportFile = File(project.getSubproject("app").projectDir, "lint-results.txt")
        assertThat(reportFile).exists()
        assertThat(reportFile).contains("NewApi")

        // Then change isOk() to check that the API level is at least 26, which should result in no
        // NewApi lint error.
        TestFileUtils.searchAndReplace(libSourceFile, "25", "26")
        getExecutor().run(":app:lintDebug")
        assertThat(project.buildResult.getTask(":lib:lintAnalyzeDebug")).didWork()
        assertThat(reportFile).exists()
        assertThat(reportFile).doesNotContain("NewApi")
    }

    private fun getExecutor() = project.executor().with(LINT_ANALYSIS_PER_COMPONENT, true)
}
