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

package com.android.build.gradle.integration.analytics

import com.android.build.gradle.integration.common.fixture.ConfigurationCacheReportParser
import com.android.build.gradle.integration.common.fixture.ConfigurationCacheReportParser.Error
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * This test class checks for correct configuration caching behavior when analytics is disabled.
 */
class ConfigurationCachingWithoutAnalyticsTest {

    @get:Rule
    var project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    // Regression test for b/278767328
    @Test
    fun testNoAnalyticsSettingsFileInConfigCacheReport() {
        // Parsing configuration cache reports from tests on Windows isn't supported
        assumeFalse(System.getProperty("os.name").lowercase(Locale.US).contains("windows"))
        val analyticsSettingsFile =
            FileUtils.join(
                project.projectDir.parentFile,
                "android_prefs_root",
                "analytics.settings"
            )
        FileUtils.deleteIfExists(analyticsSettingsFile)
        val analyticsSettingsWarning =
            Error.fileSystemEntry(
                location = "android_prefs_root/analytics.settings",
                name = "analytics.settings"
            )
        project.executor().withPerTestPrefsRoot(true).run("assembleDebug")
        project.buildResult.assertConfigurationCacheMiss()
        getConfigCacheErrorsAndWarnings().let {
            assertThat(it).isNotEmpty()
            assertThat(it).doesNotContain(analyticsSettingsWarning)
        }
        project.executor().withPerTestPrefsRoot(true).run("assembleDebug")
        project.buildResult.assertConfigurationCacheHit()
        getConfigCacheErrorsAndWarnings().let {
            assertThat(it).isNotEmpty()
            assertThat(it).doesNotContain(analyticsSettingsWarning)
        }

        // As a control, check that we *do* see the warning when analytics is enabled.
        project.executor()
            .with(BooleanOption.ENABLE_PROFILE_JSON, true)
            .withPerTestPrefsRoot(true)
            .run("assembleDebug")
        project.buildResult.assertConfigurationCacheMiss()
        getConfigCacheErrorsAndWarnings().let {
            assertThat(it).isNotEmpty()
            assertThat(it).contains(analyticsSettingsWarning)
        }
    }

    private fun getConfigCacheErrorsAndWarnings(): List<Error> =
        File(project.buildDir, "reports").walk()
            .filter { it.isFile }
            .filter { it.name != "configuration-cache.html" }
            .flatMap { ConfigurationCacheReportParser(it).getErrorsAndWarnings().asSequence() }
            .toList()
}
