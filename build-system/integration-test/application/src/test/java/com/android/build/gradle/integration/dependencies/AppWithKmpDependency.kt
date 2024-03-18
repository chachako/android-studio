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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Rule
import org.junit.Test

class AppWithKmpDependency : ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                minSdk = 21
                setUpHelloWorld()
            }
            // this is a kmp dependency published with -android and -desktop variants
            dependencies {
                implementation("androidx.lifecycle:lifecycle-runtime:2.8.0-alpha02")
            }
        }
    }

    @Test
    fun `test VariantDependencies model with kotlin attribute`() {
        val result =
            project.modelV2()
                .with(BooleanOption.USE_ANDROID_X, true)
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(
            projectAction = { getProject(":app") }, goldenFile = "app_VariantDependencies_android"
        )
    }

    @Test
    fun `test VariantDependencies model without kotlin attribute`() {
        val result =
            project.modelV2()
                .with(BooleanOption.DISABLE_KOTLIN_ATTRIBUTE_SETUP, true)
                .with(BooleanOption.USE_ANDROID_X, true)
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(
            projectAction = { getProject(":app") }, goldenFile = "app_VariantDependencies_desktop"
        )
    }

    @Test
    fun checkPackagedClassesContainAndroidSpecificClass() {
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .run(":app:assembleDebug")

        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)

        TruthHelper.assertThat(apk).containsClass("Landroidx/lifecycle/ReportFragment;")
    }

    @Test
    fun checkPackagedClassesDoesntContainAndroidSpecificClass() {
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .with(BooleanOption.DISABLE_KOTLIN_ATTRIBUTE_SETUP, true)
            .run(":app:assembleDebug")

        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)

        TruthHelper.assertThat(apk).doesNotContainClass("Landroidx/lifecycle/ReportFragment;")
    }
}
