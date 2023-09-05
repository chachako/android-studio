/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.desugar

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator.jarWithClasses
import org.junit.Rule
import org.junit.Test

/** Tests desugaring for libraries that have compileOnly dependencies (see bug 230454566). */
class DesugarCompileOnlyDependencyTest {

    private val app = MinimalSubProject.app().apply {
        appendToBuild(
            """
            android.defaultConfig.minSdkVersion = 21
            dependencies {
                implementation 'com.example:lib1:1'
                implementation 'com.example:lib2:1'
            }
            """.trimIndent()
        )
    }

    private val mavenRepo = MavenRepoGenerator(
        listOf(
            // lib1 has a compileOnly dependency on lib2
            MavenRepoGenerator.Library(
                "com.example:lib1:1",
                jarWithClasses(listOf(ImplOfInterfaceWithDefaultMethod::class.java))
            ),
            MavenRepoGenerator.Library(
                "com.example:lib2:1",
                jarWithClasses(listOf(InterfaceWithDefaultMethod::class.java))
            )
        )
    )

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(app)
        .withAdditionalMavenRepo(mavenRepo)
        .create()

    /** Regression test for bug 230454566. */
    @Test
    fun `desugar library having compileOnly dependency`() {
        project.executor()
            .with(BooleanOption.USE_FULL_CLASSPATH_FOR_DEXING_TRANSFORM, true)
            .run("assembleDebug")
        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            assertThat(apk)
                .hasClass(ImplOfInterfaceWithDefaultMethod::class.java)
                .that()
                .hasMethod("myDefaultMethod")
        }
    }

}

private interface InterfaceWithDefaultMethod {
    fun myDefaultMethod() {}
}

private class ImplOfInterfaceWithDefaultMethod : InterfaceWithDefaultMethod
