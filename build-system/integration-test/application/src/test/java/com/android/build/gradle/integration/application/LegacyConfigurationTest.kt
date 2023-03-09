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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.forEachLine
import junit.framework.TestCase.fail
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.util.Scanner

class LegacyConfigurationTest {

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

    /** Check for legacy configuration warnings. Regression test for b/268760654. */
    @Test
    fun testNoLegacyConfigurations() {
        val result = project.executor().run("assemble", "lint")
        val warning = "legacy configuration"
        val warnings =
            result.stderr.getConfigurationWarnings(warning) +
                    result.stdout.getConfigurationWarnings(warning)
        if (warnings.isNotEmpty()) {
            fail("Unexpected warning(s):\n${warnings.joinToString(separator = "\n")}")
        }
    }

    /** Check for incorrect combination warnings. Regression test for b/268760654. */
    @Ignore("b/268760654")
    @Test
    fun testNoIncorrectConfigurations() {
        val result = project.executor().run("assemble", "lint")
        val warning = "This combination is incorrect"
        val warnings =
            result.stderr.getConfigurationWarnings(warning) +
                    result.stdout.getConfigurationWarnings(warning)
        if (warnings.isNotEmpty()) {
            fail("Unexpected warning(s):\n${warnings.joinToString(separator = "\n")}")
        }
    }

    /**
     * Returns a list of scanner lines containing the given [warning], or an empty list if no lines
     * contain the given warning. Warnings for Gradle's classpath configuration are ignored.
     */
    private fun Scanner.getConfigurationWarnings(warning: String): List<String> {
        val warnings = mutableListOf<String>()
        this.forEachLine {
            if (!it.contains(":classpath ") && it.contains(warning)) {
                warnings.add(it)
            }
        }
        return warnings.toList()
    }
}

