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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.lint.LINT_XML_CONFIG_FILE_NAME
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tests for the correct handling of lint.xml files by AGP
 */
class LintXmlConfigurationTest {

    private val app =
        MinimalSubProject.app("com.example.test").appendToBuild("// STOPSHIP")

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(MultiModuleTestProject.builder().subproject(":app", app).build())
            .create()

    /**
     * Regression test for Issue 211012777
     */
    @Test
    fun testChangedLintXmlCausesLintToRunAgain() {
        // Add lint.xml file setting the severity of the "StopShip" lint issue to "warning"
        val projectLintXml = File(project.projectDir, LINT_XML_CONFIG_FILE_NAME)
        projectLintXml.writeText(
            // language=XML
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <lint>
                    <issue id="StopShip" severity="warning" />
                </lint>
            """.trimIndent()
        )
        project.executor().run(":app:lintRelease")
        // Change lint.xml file to set the severity of the "StopShip" lint issue to "error", which
        // should cause lint analysis to run again when the lint task is invoked.
        TestFileUtils.searchAndReplace(projectLintXml, "warning", "error")
        project.executor().expectFailure().run(":app:lintRelease")
    }
}

