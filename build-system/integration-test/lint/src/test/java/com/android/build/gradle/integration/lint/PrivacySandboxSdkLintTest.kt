/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox.privacySandboxSampleProject
import com.android.build.gradle.integration.common.truth.GradleTaskSubject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import java.io.File

class PrivacySandboxSdkLintTest {
    @get:Rule
    val project = privacySandboxSampleProject()

    private fun executor() = project.executor()
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
        .with(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT, true)
        .with(BooleanOption.PRIVACY_SANDBOX_SDK_ENABLE_LINT, true)
        .withFailOnWarning(false) // kgp uses deprecated api WrapUtil
        .withPerTestPrefsRoot(true)
        .with(BooleanOption.ENABLE_PROFILE_JSON, true) // Regression test for b/237278679

    @Test
    fun testTargetSdkVersionLintReporting() {
        val sdkProject = project.getSubproject(":privacy-sandbox-sdk")
        sdkProject.buildFile.appendText("\nandroid.targetSdk 32")

        val buildResult = executor().expectFailure().run(":privacy-sandbox-sdk:lint")
        GradleTaskSubject.assertThat(buildResult.getTask(":privacy-sandbox-sdk:lintAnalyze")).didWork()
        val lintTextReport = sdkProject.getReportsFile("lint-results-main.txt")
        assertThat(lintTextReport).exists()
        assertThat(lintTextReport).contains("""privacy-sandbox-sdk/build.gradle:26: Error: Google Play requires that apps target API level 33 or higher. [ExpiredTargetSdkVersion]
android.targetSdk 32
~~~~~~~~~~~~~~~~~~~~""".trimIndent())
    }


    @Test
    fun testSdkLintReporting() {
        executor().run(":privacy-sandbox-sdk:lint")
        val sdkProject = project.getSubproject(":privacy-sandbox-sdk")
        val lintTextReport = sdkProject.getReportsFile("lint-results-main.txt")
        assertThat(lintTextReport).exists()
        assertThat(lintTextReport).contains("""sdk-impl-a/src/main/res/values/strings.xml:2: Warning: The resource R.string.string_from_sdk_impl_a appears to be unused [UnusedResources]
                <string name="string_from_sdk_impl_a">fromSdkImplA</string>
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        """.trimIndent())
        val lintHtmlReport = sdkProject.getReportsFile("lint-results-main.html")
        assertThat(lintHtmlReport).exists()
        assertThat(lintHtmlReport).contains("The resource <code>R.string.string_from_sdk_impl_a</code> appears to be unused")
        val lintXmlReport = sdkProject.getReportsFile("lint-results-main.xml")
        assertThat(lintXmlReport).exists()
        assertThat(lintXmlReport).contains("message=\"The resource `R.string.string_from_sdk_impl_a` appears to be unused\"")
    }

    @Test
    fun checkUpdateLintBaseline() {
        val sdkProject = project.getSubproject(":privacy-sandbox-sdk")
        TestFileUtils.appendToFile(
            sdkProject.buildFile,
            """
                android {
                    lint {
                       baseline = file('lint-baseline.xml')
                    }
                }
            """.trimIndent()
        )

        // First test the case when there is no existing baseline.
        val baselineFile = File(sdkProject.projectDir, "lint-baseline.xml")
        assertThat(baselineFile).doesNotExist()
        val result = executor().run(":privacy-sandbox-sdk:updateLintBaseline")
        GradleTaskSubject.assertThat(result.getTask(":android-lib:lintAnalyzeDebug")).didWork()
        GradleTaskSubject.assertThat(result.getTask(":sdk-impl-a:lintAnalyzeDebug")).didWork()
        GradleTaskSubject.assertThat(result.getTask(":privacy-sandbox-sdk:updateLintBaseline")).didWork()
        assertThat(baselineFile).exists()
        // Check if the baseline contains an existing lint issue.
        assertThat(baselineFile).contains("""The resource `R.string.string_from_sdk_impl_a` appears to be unused""")

        // Run lint and ensure that this issue is not reported again because it is recorded in the baseline.
        executor().run(":privacy-sandbox-sdk:lint")
        val lintXmlReport = sdkProject.getReportsFile("lint-results-main.xml")
        assertThat(lintXmlReport).exists()
        assertThat(lintXmlReport).doesNotContain("""The resource `R.string.string_from_sdk_impl_a` appears to be unused""")
        assertThat(lintXmlReport).contains("Baseline Applied")
    }
    @Test
    fun checkLintVital() {
        val result = executor().run(":privacy-sandbox-sdk:assemble")
        GradleTaskSubject.assertThat(result.getTask(":android-lib:lintVitalAnalyzeDebug")).didWork()
        GradleTaskSubject.assertThat(result.getTask(":sdk-impl-a:lintVitalAnalyzeDebug")).didWork()
        GradleTaskSubject.assertThat(result.getTask(":privacy-sandbox-sdk:lintVital")).didWork()
        val lintVitalReport = project.getSubproject("privacy-sandbox-sdk")
            .getIntermediateFile("lint_vital_intermediate_text_report", "single", "lintVitalReport", "lint-results-main.txt")
        assertThat(lintVitalReport).exists()
        assertThat(lintVitalReport).contains("No issues found.")
    }

    @Test
    fun checkLintFix() {
        val androidLibProject = project.getSubproject(":android-lib")
        val sdkProject = project.getSubproject(":privacy-sandbox-sdk")
        TestFileUtils.appendToFile(
            androidLibProject.buildFile,
            "\nandroid.lint.error += \"SyntheticAccessor\"\n"
        )

        TestFileUtils.appendToFile(
            sdkProject.buildFile,
            "\nandroid.lint.error += \"SyntheticAccessor\"\n"
        )

        val sourceTestFile = androidLibProject.file("src/main/java/com/example/androidlib/AccessTest.java")
        FileUtils.createFile(sourceTestFile,
            """
                package com.example.androidlib;

                class AccessTest {
                    private AccessTest() {}
                    class Inner {
                        private void innerMethod() {
                            new AccessTest();
                        }
                    }
                    public void f2() {}
                }

            """.trimIndent())

        val result = executor().expectFailure().run(":privacy-sandbox-sdk:lintFix")
        assertThat(result.stderr)
            .contains(
                "Aborting build since sources were modified to apply quickfixes after compilation"
            )
        // Make sure quickfixes worked too
        assertThat(sourceTestFile).doesNotContain("private AccessTest()")
        assertThat(sourceTestFile).contains("AccessTest()")
    }
}
