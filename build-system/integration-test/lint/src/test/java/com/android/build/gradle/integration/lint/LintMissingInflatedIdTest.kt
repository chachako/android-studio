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
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_PARTIAL_RESULTS
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test

class LintMissingInflatedIdTest {

    private val app =
        MinimalSubProject.app("com.example.app")
            .withFile(
                "src/main/res/layout/app_main.xml",
                // language=XML
                """<?xml version="1.0" encoding="utf-8"?>
                <LinearLayout
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        android:orientation="vertical"
                        android:layout_width="fill_parent"
                        android:layout_height="fill_parent" >
                    <TextView
                            android:layout_width="fill_parent"
                            android:layout_height="wrap_content"
                            android:text="foo"
                            android:id="@+id/text" />
                </LinearLayout>""")
            .withFile(
                "src/main/java/com/example/app/MainActivity.java",
                // language=java
                """package com.example.app;

                import android.app.Activity;
                import android.os.Bundle;
                import android.widget.TextView;

                public class MainActivity extends Activity {

                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.app_main);
                        TextView tv = (TextView) findViewById(R.id.text);
                    }
                }""")
            .appendToBuild(
                // language=groovy
                """
                    android {
                        lint {
                            error "LintWarning"
                            enable "MissingInflatedId"
                        }
                    }
                """.trimIndent()
            )

    private val lib =
        MinimalSubProject.lib("com.example.lib")
            .withFile(
                "src/main/res/layout/lib_main.xml",
                // language=XML
                """<?xml version="1.0" encoding="utf-8"?>
                <LinearLayout
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        android:orientation="vertical"
                        android:layout_width="fill_parent"
                        android:layout_height="fill_parent" >
                    <TextView
                            android:layout_width="fill_parent"
                            android:layout_height="wrap_content"
                            android:text="bar"
                            android:id="@+id/text" />
                </LinearLayout>""")

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
     * Regression test for b/299602350.
     *
     * Test the case of the MissingInflatedIdDetector running on an app with a library module
     * dependency. Previously, this would cause a LintWarning because the library module would write
     * a resources.xml file, and then the app would try but fail to deserialize the library module's
     * resources.xml file.
     */
    @Test
    fun testNoLintWarningFromMissingInflatedIdDetector() {
        // First check that running lint analysis on lib causes the lint-resources.xml file to be
        // written
        project.executor().run(":lib:lintAnalyzeDebug")
        val libLintResourcesXml =
            FileUtils.join(
                project.getSubproject(":lib")
                    .getIntermediateFile(LINT_PARTIAL_RESULTS.getFolderName()),
                "debug",
                "lintAnalyzeDebug",
                "out",
                "lint-resources.xml"
            )
        PathSubject.assertThat(libLintResourcesXml).exists()
        // Check for the expected lib-specific path variable
        PathSubject.assertThat(libLintResourcesXml)
            .contains(":lib*debug*MAIN*sourceProvider*0*resDir*0")
        // Check that :app:lintDebug runs successfully even though there's a lib-specific path
        // variable in lib's lint-resources.xml
        project.executor().run(":app:lintDebug")
    }
}
