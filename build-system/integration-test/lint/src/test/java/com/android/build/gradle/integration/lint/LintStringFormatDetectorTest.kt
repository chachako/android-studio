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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test

class LintStringFormatDetectorTest {

    private val app =
        MinimalSubProject.app("com.example.app")
            .withFile(
                "src/main/java/com/example/app/MainActivity.java",
                // language=java
                """package com.example.app;

                import android.app.Activity;

                public class MainActivity extends Activity {
                    public void foo() {
                        String.format(getString(com.example.lib.R.string.hello), 5);
                    }
                }""")
            .appendToBuild(
                // language=groovy
                """
                    android {
                        lint {
                            abortOnError false
                            textOutput file("lint-results.txt")
                        }
                    }
                """.trimIndent()
            )

    private val lib =
        MinimalSubProject.lib("com.example.lib")
            .withFile(
                "src/main/res/values/strings.xml",
                // language=XML
                """<?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="hello">hello %s</string>
                </resources>""")

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":lib", lib)
                    .dependency(app, lib)
                    .build()
            )
            .create()

    /**
     * Regression test for b/303215439.
     *
     * Previously, this scenario would result in a LintError because lint would try to resolve the
     * library module's strings.xml source file during the app's lint analysis.
     */
    @Test
    fun testNoLintError() {
        project.executor().run(":app:lintDebug")

        val file = project.getSubproject("app").file("lint-results.txt")
        PathSubject.assertThat(file).exists()
        PathSubject.assertThat(file).contains(
            "MainActivity.java:7: Error: Suspicious argument type for formatting argument"
        )
        val expectedPath = FileUtils.toSystemDependentPath("lib/src/main/res/values/strings.xml")
        PathSubject.assertThat(file).contains(
            "$expectedPath: Conflicting argument declaration here"
        )
        PathSubject.assertThat(file).contains("1 error")
    }
}
