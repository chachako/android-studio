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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.v2.ide.LibraryType
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Sanity test for automatic namespacing of dependencies.
 *
 * Verifies that the AARs in the model appear namespaced and that the project builds.
 */
class AutoNamespaceTest {
    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("namespacedApp")
        .create()

    @Test
    fun checkNamespacedApp() {
        val variantDeps = project.modelV2()
            .with(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES, true)
            .fetchVariantDependencies("debug")
            .container.getProject()
            .variantDependencies!!

        val libraries = variantDeps.libraries
        assertThat(libraries).isNotEmpty()

        libraries
            .filter { lib -> lib.value.type == LibraryType.ANDROID_LIBRARY }
            .forEach { lib -> assertThat(lib.value.androidLibraryData?.resStaticLibrary).exists() }

        project.executor().run("assembleDebug")

        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        // Sanity check the final APK.
        assertThat(apk).containsClass("Landroid/support/constraint/Guideline;")
        assertThat(apk).containsClass("Landroid/support/constraint/R\$attr;")
    }
}
