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

import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

class CompileRClassTest {
    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("compileRClasses")
        .addGradleProperties("android.enableAppCompileTimeRClass=true")
        .addGradleProperties("android.nonTransitiveRClass=true")
        .create()

    @Test
    fun cannotAccessTransitiveResource() {
        val result = project.executor()
            .expectFailure()
            .run(":lib:compileDebugAndroidTestKotlin")

        ScannerSubject.assertThat(result.stderr).contains(
            "Unresolved reference 'transitiveDependencyLib'."
        )
    }

    @Test
    fun transitiveResourceAccessWhenFlagDisabled() {
        TestFileUtils.searchAndReplace(
            project.gradlePropertiesFile,
            "android.enableAppCompileTimeRClass=true",
            "android.enableAppCompileTimeRClass=false"
        )

        project.executor().run(":lib:compileDebugAndroidTestKotlin")
    }

    @Test
    fun transitiveResourceAccessWhenApiDependency() {
        // lib -> (impl) dependencyLib -> (api) transitiveDependencyLib
        TestFileUtils.searchAndReplace(
            project.getSubproject("dependencyLib").ktsBuildFile,
            """implementation(project(":transitiveDependencyLib"))""",
            """api(project(":transitiveDependencyLib"))""",
        )

        project.executor().run(":lib:compileDebugAndroidTestKotlin")
    }
}
