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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Integration test testing the proper handling of checkDependencies
 */
class LintCheckDependenciesTest {

    private val app = MinimalSubProject.app("com.example.app")
    private val lib = MinimalSubProject.lib("com.example.lib")
    // lib2 is a runtimeOnly dependency of app (see b/294279964)
    private val lib2 = MinimalSubProject.lib("com.example.lib2")
    private val javaLib = MinimalSubProject.javaLibrary()

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":lib", lib)
                    .subproject(":lib2", lib2)
                    .subproject(":javaLib", javaLib)
                    .dependency(app, lib)
                    .dependency("runtimeOnly", app, lib2)
                    .dependency(app, javaLib)
                    .build()
            ).create()

    @Before
    fun before() {
        project.getSubproject(":app")
            .buildFile
            .appendText(
                """
                    android {
                        lintOptions {
                            abortOnError false
                            enable 'StopShip'
                            textOutput file("lint-results.txt")
                            checkDependencies false
                        }
                    }
                """.trimIndent()
            )

        project.getSubproject(":lib")
            .buildFile
            .appendText(
                """
                    android {
                        lintOptions {
                            enable 'StopShip'
                        }
                    }
                """.trimIndent()
            )

        project.getSubproject(":lib2")
            .buildFile
            .appendText(
                """
                    android {
                        lintOptions {
                            enable 'StopShip'
                        }
                    }
                """.trimIndent()
            )

        val appSourceFile =
            project.getSubproject(":app").file("src/main/java/com/example/foo/App.java")
        appSourceFile.parentFile.mkdirs()
        appSourceFile.writeText(
            """
                package com.example.foo;

                public class App {
                    // STOPSHIP
                }
            """.trimIndent()
        )

        val libSourceFile =
            project.getSubproject(":lib").file("src/main/java/com/example/bar/Lib.java")
        libSourceFile.parentFile.mkdirs()
        libSourceFile.writeText(
            """
                package com.example.bar;

                public class Lib {
                    // STOPSHIP
                }
            """.trimIndent()
        )

        val lib2SourceFile =
            project.getSubproject(":lib2").file("src/main/java/com/example/baz/LibTwo.java")
        lib2SourceFile.parentFile.mkdirs()
        lib2SourceFile.writeText(
        """
                    package com.example.baz;

                    public class LibTwo {
                        // STOPSHIP
                    }
                """.trimIndent()
        )
    }

    @Test
    fun testCheckDependencies() {
        // First run with checkDependencies false and check that lib's STOPSHIP issue is not
        // included in app's lint report.
        project.executor().run(":app:lintRelease")
        assertThat(project.buildResult.getTask(":lib:lintAnalyzeRelease")).didWork()
        val reportFile = File(project.getSubproject("app").projectDir, "lint-results.txt")
        assertThat(reportFile).exists()
        assertThat(reportFile).contains("App.java:4: Error: STOPSHIP comment found")
        assertThat(reportFile).doesNotContain("Lib.java")
        assertThat(reportFile).doesNotContain("LibTwo.java")
        // Then run with checkDependencies true and check that lib's STOPSHIP issue *is* included
        // in app's lint report.
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "checkDependencies false",
            "checkDependencies true",
        )
        project.executor().run(":app:lintRelease")
        assertThat(reportFile).exists()
        assertThat(reportFile).containsAllOf(
            "App.java:4: Error: STOPSHIP comment found",
            "Lib.java:4: Error: STOPSHIP comment found",
            "LibTwo.java:4: Error: STOPSHIP comment found"
        )
    }

    @Test
    fun testCheckDependenciesLintVital() {
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").file("src/main/java/com/example/foo/App.java"),
            "// STOPSHIP",
            "",
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "abortOnError false",
            "abortOnError true",
        )
        // First run with checkDependencies false
        project.executor().run(":app:lintVitalRelease")
        ScannerSubject.assertThat(project.buildResult.stdout).contains("BUILD SUCCESSFUL")
        assertThat(project.buildResult.getTask(":lib:lintVitalAnalyzeRelease")).didWork()
        // Then run with checkDependencies true and check that lib's STOPSHIP issue *is* included
        // in app's lint report.
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "checkDependencies false",
            "checkDependencies true",
        )
        project.executor().expectFailure().run(":app:lintVitalRelease")
        ScannerSubject.assertThat(project.buildResult.stderr)
            .contains("Lib.java:4: Error: STOPSHIP comment found")
        ScannerSubject.assertThat(project.buildResult.stderr)
            .contains("LibTwo.java:4: Error: STOPSHIP comment found")
    }

    @Test
    fun testWarningForJavaLibDependencyWithoutLintPlugin() {
        val warning =
            "Warning: Lint will treat :javaLib as an external dependency and not analyze it."
        // We expect no warning when checkDependencies is false
        project.executor().run(":app:lintDebug")
        project.buildResult.stdout.use { ScannerSubject.assertThat(it).doesNotContain(warning) }
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "checkDependencies false",
            "checkDependencies true",
        )
        // We expect the warning when checkDependencies is true
        project.executor().run(":app:lintDebug")
        project.buildResult.stdout.use { ScannerSubject.assertThat(it).contains(warning) }
    }
}
