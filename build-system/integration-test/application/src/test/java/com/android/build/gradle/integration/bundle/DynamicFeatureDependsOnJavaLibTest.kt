/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DynamicFeatureDependsOnJavaLibTest {
    @JvmField
    @Rule
    val tmp = TemporaryFolder()

    val app = MinimalSubProject.app("com.example.test.app")
        .appendToBuild("android.dynamicFeatures = [':feature']")
    val feature = MinimalSubProject.dynamicFeature("com.example.test.feature")
    val javaLib = MinimalSubProject.javaLibrary()

    @JvmField
    @Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .subproject(":feature", feature)
                .subproject(":lib", javaLib)
                .dependency(feature, app)
                .dependency(feature, javaLib)
                .build()
        )
        // Enforcing unique package names to prevent regressions. Remove when b/116109681 fixed.
        .addGradleProperties("${BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES.propertyName}=true")
        .create()

    /** Regression test for b/79660649. */
    @Test()
    fun checkItBuilds() {
        project.executor().run(":feature:assembleDebug")
    }
}
