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

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.options.BooleanOption.LINT_ANALYSIS_PER_COMPONENT
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Integration test class for [LintModelWriterTask]
 */
@RunWith(Parameterized::class)
class LintModelWriterTaskTest(private val lintAnalysisPerComponent: Boolean) {

    private val javaLib1 = MinimalSubProject.javaLibrary()
    private val javaLib2 = MinimalSubProject.javaLibrary()

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":javaLib1", javaLib1)
                    .subproject(":javaLib2", javaLib2)
                    .dependency(javaLib1, javaLib2)
                    .dependency("testImplementation", javaLib2, javaLib1)
                    .build()
            ).create()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "lintAnalysisPerComponent_{0}")
        fun params() = listOf(true, false)
    }

    @Before
    fun before() {
        listOf(":javaLib1", ":javaLib2").forEach {
            project.getSubproject(it).buildFile.appendText("\napply plugin: 'com.android.lint'\n")
        }
    }

    /**
     * Regression test for b/291934867 - "Lint model tasks have circular dependencies"
     */
    @Test
    fun testUnusualDependencyStructure() {
        getExecutor().run("lint")
    }

    private fun getExecutor(): GradleTaskExecutor {
        return project.executor().with(LINT_ANALYSIS_PER_COMPONENT, lintAnalysisPerComponent)
    }
}
