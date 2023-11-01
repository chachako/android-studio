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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.truth.AarSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OptionalAarTest : ModelComparator() {

    @get:Rule
    val project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create()

    @Before
    fun setUp() {
        project.setIncludedProjects("app", "library", "library2")
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                dependencies {
                    api project(":library")
                }
            """.trimIndent())

        TestFileUtils.appendToFile(
            project.getSubproject("library").buildFile,
            """
                dependencies {
                    compileOnly project(":library2")
                }
            """.trimIndent())

        project.executor().run("clean", ":app:assembleDebug", "library:assembleDebug")
    }

    @Test
    fun `test VariantDependencies model`() {
        val result =
            project.modelV2()
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(
            projectAction = { getProject(":app") }, goldenFile = "app_VariantDependencies"
        )
    }

    @Test
    fun checkAppDoesNotContainProvidedLibsLayout() {
        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).doesNotContainResource("layout/lib2layout.xml")
    }

    @Test
    fun checkAppDoesNotContainProvidedLibsCode() {
        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).doesNotContainClass("Lcom/example/android/multiproject/library2/PersonView2;")
    }

    @Test
    fun checkLibDoesNotContainProvidedLibsLayout() {
        project.getSubproject("library").testAar("debug") { aar: AarSubject ->
            aar.doesNotContainResource("layout/lib2layout.xml")
            aar.textSymbolFile().doesNotContain("int layout lib2layout")
            aar.textSymbolFile().contains("int layout liblayout")
        }
    }
}
