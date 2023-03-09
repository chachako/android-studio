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
        val warning =
            result.stderr.getLegacyConfigurationWarning()
                ?: result.stdout.getLegacyConfigurationWarning()
        warning?.let {
            fail("Unexpected legacy configuration warning: \n$it")
        }
    }

    /**
     * Returns the first legacy configuration warning, or null if none is found
     */
    private fun Scanner.getLegacyConfigurationWarning(): String? {
        var warning: String? = null
        this.forEachLine {
            if (!it.contains(":classpath ") && it.contains("legacy configuration")) {
                warning = it
                return@forEachLine
            }
        }
        return warning
    }
}

