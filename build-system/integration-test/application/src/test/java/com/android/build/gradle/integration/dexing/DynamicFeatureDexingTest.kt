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

package com.android.build.gradle.integration.dexing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DynamicFeatureDexingTest {
    private val app = MinimalSubProject.app("com.example.app").also {
        it.appendToBuild("""

            android.defaultConfig.minSdkVersion = 25
            android.dynamicFeatures = [":feature"]
        """.trimIndent())
    }

    private val feature = MinimalSubProject.dynamicFeature("com.example.feature").also {
        it.appendToBuild("""

            android.defaultConfig.minSdkVersion = 25
            dependencies {
                implementation project('::app')
            }
        """.trimIndent())
    }

    @get:Rule
    val project = GradleTestProject.builder()
            .fromTestApp(
                    MultiModuleTestProject.builder()
                            .subproject("app", app)
                            .subproject("feature", feature)
                            .build()
            )
            .withKotlinGradlePlugin(true)
            .create()

    @Before
    fun setUp() {
        // add a kotlin library as a project dependency for dynamice feature module
        val kotlinLibrary = project.projectDir.resolve("kotlinLibrary")
        val buildFile = kotlinLibrary.resolve("build.gradle").also { it.parentFile.mkdirs() }
        val javaSource = kotlinLibrary.resolve("src/main/java/com/example/JavaClass.java").also {
            it.parentFile.mkdirs()
        }
        val kotlinSource =
                kotlinLibrary.resolve("src/main/kotlin/com/example/KotlinClass.kt").also {
                    it.parentFile.mkdirs()
                }
        buildFile.writeText(
                """
                    plugins { id 'org.jetbrains.kotlin.jvm' }
                """.trimIndent()
        )
        javaSource.writeText(
                """
                    package com.example;

                    public class JavaClass {
                    }
                """.trimIndent()
        )
        kotlinSource.writeText(
                """
                    package com.example

                    class KotlinClass {
                    }
                """.trimIndent()
        )
        project.getSubproject(":feature").buildFile.appendText(
                """
                    dependencies {
                        implementation project(":kotlinLibrary")
                    }
                """.trimIndent()
        )
        project.settingsFile.appendText(
                """
                    include ':kotlinLibrary'
                """.trimIndent()
        )
    }

    // Regression test for b/246326007
    @Test
    fun basicTest() {
        project.executor().run("assembleDebug")
        val featureApk = project.getSubproject(":feature").getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(featureApk).containsClass("Lcom/example/JavaClass;")
        assertThat(featureApk).containsClass("Lcom/example/KotlinClass;")
    }
}
