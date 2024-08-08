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
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

class LintErrorTest {

    @get:Rule
    val project = builder().fromTestProject("lintCustomRules").create()

    /**
     * Regression test for b/230685896. An exception when running lint should cause the lint
     * analysis task to fail to prevent flaky exceptions from being saved in the build cache.
     */
    @Test
    fun testExceptionCausesLintAnalysisFailure() {
        TestFileUtils.searchAndReplace(
            project.getSubproject("lint")
                .mainSrcDir
                .resolve("com/example/google/lint/MainActivityDetector.java"),
            "// placeholder",
            "throw new RuntimeException(\"test123\");"
        )
        project.executor().expectFailure().run(":app:lintAnalyzeDebug")
        ScannerSubject.assertThat(project.buildResult.stderr).contains("test123")
    }
}
