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
package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test
import java.util.Scanner

class LintErrorTest {

    @get:Rule
    val project =
        builder().fromTestApp(MinimalSubProject.app("com.example.app"))
            .withHeap("100m")
            // Disable system health checks inside Gradle daemon, we are going for an OOM:
            // https://docs.gradle.org/current/userguide/gradle_daemon.html#performance_monitoring
            .addGradleProperties("systemProp.org.gradle.daemon.performance.enable-monitoring=false")
            .create()

    /**
     * Regression test for b/297095583. An OutOfMemoryError when running lint should result in a
     * build failure instead of a LintError being added to the lint baseline file.
     */
    @Test
    fun testOutOfMemoryErrorCausesBuildFailureWhenUpdatingLintBaseline() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    lint {
                        baseline = file('lint-baseline.xml')
                    }
                }
            """.trimIndent()
        )
        val executor = project.executor()
        executor
            .crashOnOutOfMemory() // Avoids hangs such as https://github.com/gradle/gradle/issues/15621
            .expectFailure()
            .run("updateLintBaseline")

        ScannerSubject.assertThat(Scanner(executor.jvmErrorLog))
            .contains("fatal error: OutOfMemory encountered: Java heap space")
    }
}
