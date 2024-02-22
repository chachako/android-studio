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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.MavenRepoGenerator
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibraryCompileAndRuntimeClasspathTest {
    @JvmField
    @Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(
            MinimalSubProject.lib(),
        ).withAdditionalMavenRepo(MavenRepoGenerator(listOf(
            MavenRepoGenerator.Library("com.example:package:1.0-runtimeOnly"),
            MavenRepoGenerator.Library("com.example:package:2.0-compileOnly"),
            MavenRepoGenerator.Library("com.example:package:3.0-androidTestRuntimeOnly"),
            MavenRepoGenerator.Library("com.example:package:4.0-androidTestCompileOnly"),
            MavenRepoGenerator.Library("com.example:package:5.0-testRuntimeOnly"),
            MavenRepoGenerator.Library("com.example:package:6.0-testCompileOnly"),
        ))).create()

    @Before
    fun setUpDependencies() {
        project.buildFile.appendText("""
            |dependencies {
            |    testCompileOnly 'com.example:package:6.0-testCompileOnly'
            |    testRuntimeOnly 'com.example:package:5.0-testRuntimeOnly'
            |    androidTestCompileOnly 'com.example:package:4.0-androidTestCompileOnly'
            |    androidTestRuntimeOnly 'com.example:package:3.0-androidTestRuntimeOnly'
            |    compileOnly 'com.example:package:2.0-compileOnly'
            |}
            |""".trimMargin())
    }

    @Test
    fun `debugAndroidTestCompileClasspath - constraints unsatisfiable`() {
        assertDependenciesOutput(
            "debugAndroidTestCompileClasspath", """
            |debugAndroidTestCompileClasspath - Resolved configuration for compilation for variant: debugAndroidTest
            |+--- com.example:package:4.0-androidTestCompileOnly FAILED
            |+--- project : (*)
            |\--- com.example:package:{strictly 3.0-androidTestRuntimeOnly} FAILED
        """
        )
    }

    @Test
    fun `all other cases - constraints unsatisfiable`() {
        // Adding this causes the debugAndroidTestRuntimeClasspath to fail, which in turn makes the
        // debugAndroidTestCompileClasspath not applicable in this case, so that's a separate
        // test case without the 1.0-runtimeOnly dependency
        project.buildFile.appendText("""
            |dependencies {
            |    runtimeOnly 'com.example:package:1.0-runtimeOnly'
            |}
            |""".trimMargin())

        assertDependenciesOutput("debugCompileClasspath", """
            |debugCompileClasspath - Resolved configuration for compilation for variant: debug
            |+--- com.example:package:2.0-compileOnly FAILED
            |\--- com.example:package:{strictly 1.0-runtimeOnly} FAILED
        """)
        assertDependenciesOutput("debugUnitTestCompileClasspath", """
            |debugUnitTestCompileClasspath - Resolved configuration for compilation for variant: debugUnitTest
            |+--- project : (*)
            |+--- com.example:package:6.0-testCompileOnly FAILED
            |\--- com.example:package:{strictly 5.0-testRuntimeOnly} FAILED
        """)

        // This won't fails because the androidTest runtimeClasspath is no longer aligned to main
        // runtimeClasspath.
        assertDependenciesOutput("debugAndroidTestRuntimeClasspath", """
            |debugAndroidTestRuntimeClasspath - Resolved configuration for runtime for variant: debugAndroidTest
            |+--- com.example:package:3.0-androidTestRuntimeOnly
            |+--- project : (*)
            |\--- com.example:package:1.0-runtimeOnly -> 3.0-androidTestRuntimeOnly
        """)
    }

    @Test
    fun `debugAndroidTestCompileClasspath - succeeds with constraints disabled`() {
        project.gradlePropertiesFile.appendText(
            "${BooleanOption.EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS.propertyName}=true"
        )

        assertDependenciesOutput("debugAndroidTestCompileClasspath", """
            |debugAndroidTestCompileClasspath - Resolved configuration for compilation for variant: debugAndroidTest
            |+--- com.example:package:4.0-androidTestCompileOnly
            |\--- project : (*)
        """)
    }


    @Test
    fun `all other cases - succeeds with constraints disabled`() {
        project.gradlePropertiesFile.appendText(
            "${BooleanOption.EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS.propertyName}=true"
        )

        // Adding this causes the debugAndroidTestRuntimeClasspath to fail, which in turn makes the
        // debugAndroidTestCompileClasspath not applicable in this case, so that's a separate
        // test case without the 1.0-runtimeOnly dependency
        project.buildFile.appendText("""
            |dependencies {
            |    runtimeOnly 'com.example:package:1.0-runtimeOnly'
            |}
            |""".trimMargin())

        assertDependenciesOutput("debugCompileClasspath", """
            |debugCompileClasspath - Resolved configuration for compilation for variant: debug
            |\--- com.example:package:2.0-compileOnly
        """)
        assertDependenciesOutput("debugUnitTestCompileClasspath", """
            |debugUnitTestCompileClasspath - Resolved configuration for compilation for variant: debugUnitTest
            |+--- project : (*)
            |\--- com.example:package:6.0-testCompileOnly
        """)

        assertDependenciesOutput("debugAndroidTestRuntimeClasspath", """
            |debugAndroidTestRuntimeClasspath - Resolved configuration for runtime for variant: debugAndroidTest
            |+--- com.example:package:3.0-androidTestRuntimeOnly
            |+--- project : (*)
            |\--- com.example:package:1.0-runtimeOnly -> 3.0-androidTestRuntimeOnly
        """)
    }

    private fun assertDependenciesOutput(configurationName: String, expectedOutput: String) {
        val result = project.executor().withArguments(listOf("dependencies","--configuration", configurationName)).run()
        result.stdout.use {
            ScannerSubject.assertThat(it).contains(expectedOutput.trimMargin())
        }
    }
}
