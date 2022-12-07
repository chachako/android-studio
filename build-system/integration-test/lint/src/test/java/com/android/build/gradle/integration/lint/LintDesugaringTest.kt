/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.testutils.truth.PathSubject
import org.junit.Rule
import org.junit.Test

/** Integration test for lint analyzing library desugaring from Gradle.  */
class LintDesugaringTest {
    @get:Rule
    val project = builder()
        .fromTestProject("lintDesugaring")
        .create()

    @Test
    fun checkFindErrors() {
        project.executor().run(":app:clean", ":app:lintDebug", ":library:lintDebug")
        val appReport = project.file("app/build/reports/lint-results.txt")
        PathSubject.assertThat(appReport).contains("No issues found.")
        val libReport = project.file("library/build/reports/lint-results.txt")
        PathSubject.assertThat(libReport).contains("No issues found.")
    }
}
