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

package com.android.build.gradle.integration.testing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_MIN_SDK
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.apk.Apk
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SeparateTestWithAarDependencyTest : ModelComparator() {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("separateTestModule")
        // Enforcing unique package names to prevent regressions. Remove when b/116109681 fixed.
        .addGradleProperties("${BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES.propertyName}=true")
        .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """
                apply plugin: "com.android.application"
                android {
                    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                    buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
                    defaultConfig {
                         minSdkVersion $SUPPORT_LIB_MIN_SDK
                    }
                    dependencies {
                        api 'androidx.appcompat:appcompat:1.6.1'
                        api 'androidx.legacy:legacy-support-v4:1.0.0'
                        api 'androidx.media:media:1.6.0'
                    }
                }
            """.trimIndent())

        project.executor().run("clean", "assemble")
    }

    @Test
    fun `test VariantDependencies model`() {
        val result = project.modelV2()
                .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(
            projectAction = { getProject(":test") }, goldenFile = "test_VariantDependencies"
        )
    }

    @Test
    fun checkAppDoesntContainTestAppCode() {
        val apk: Apk = project.getSubproject("test").getApk(GradleTestProject.ApkType.DEBUG)
        TruthHelper.assertThatApk(apk).doesNotContainClass("Lcom/android/tests/basic/Main;")
    }

    @Test
    fun checkAppDoesntContainTestAppLayout() {
        val apk: Apk = project.getSubproject("test").getApk(GradleTestProject.ApkType.DEBUG)
        TruthHelper.assertThatApk(apk).doesNotContainResource("layout/main.xml")
    }

    @Test
    fun checkAppDoesntContainTestAppDependencyLibCode() {
        val apk = project.getSubproject("test").getApk(GradleTestProject.ApkType.DEBUG)
        TruthHelper.assertThatApk(apk).doesNotContainClass("Landroid/support/v7/app/ActionBar;")
    }

    @Test
    fun checkAppDoesNotContainTestAppDependencyLibResources() {
        val apk = project.getSubproject("test").getApk(GradleTestProject.ApkType.DEBUG)
        TruthHelper.assertThatApk(apk).doesNotContainResource("layout/abc_action_bar_title_item.xml")
    }
}
