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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.internal.CompileOptions
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.appendText
import kotlin.io.path.deleteExisting
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Tests related to the incremental behavior of [MergeJavaResourceTask].
 */
class MergeJavaResourceIncrementalTest {

    @get:Rule
    val project =
            createGradleProject {
                withKotlinPlugin = true
                subProject(":foo:lib") {
                    useNewPluginsDsl = true
                    plugins += PluginType.ANDROID_LIB
                    plugins += PluginType.KOTLIN_ANDROID
                    android {
                        defaultCompileSdk()
                        kotlinOptions {
                            jvmTarget = CompileOptions.DEFAULT_JAVA_VERSION.toString()
                        }
                    }
                    addFile("src/main/resources/res1.txt", "res 1 from foo")
                }
                subProject(":bar:lib") {
                    useNewPluginsDsl = true
                    plugins += PluginType.ANDROID_LIB
                    plugins += PluginType.KOTLIN_ANDROID
                    android {
                        defaultCompileSdk()
                        kotlinOptions {
                            jvmTarget = CompileOptions.DEFAULT_JAVA_VERSION.toString()
                        }
                    }
                    addFile("src/main/resources/res1.txt", "res 1 from bar")
                }
                subProject(":app") {
                    useNewPluginsDsl = true
                    plugins += PluginType.ANDROID_APP
                    plugins += PluginType.KOTLIN_ANDROID
                    android {
                        defaultCompileSdk()
                        kotlinOptions {
                            jvmTarget = CompileOptions.DEFAULT_JAVA_VERSION.toString()
                        }
                        appendToBuildFile { //language=groovy
                            """
                                android {
                                    packagingOptions {
                                        resources {
                                            pickFirsts += "res1.txt"
                                        }
                                    }
                                }
                            """.trimIndent()
                        }
                    }
                    dependencies {
                        implementation(project(":foo:lib"))
                        implementation(project(":bar:lib"))
                    }
                }
            }


    /**
     * Checks that the java resource merger can handle changes to multiple files with the same
     * normalized path (and therefore result in conflicting changes in the APK resources).
     *
     * For now this is implemented by falling back to a non-incremental run of the java resource merger.
     *
     * Regression test for b/284003132: when adding and removing kotlin files to projects which use
     * the same kotlin module name, this can lead to conflicting changes of the java resource
     * META-INF/<library_name>.kotlin_module
     */
    @Test
    fun testIncrementalChanges() {
        project.execute(":app:assembleDebug")
        project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            assertThat(apk).containsJavaResource("res1.txt")
            assertThat(apk.getJavaResource("res1.txt").readText().trim()).isEqualTo("res 1 from foo")
        }
        val fooRes = project.file("foo/lib/src/main/resources/res1.txt").toPath()
        val barRes = project.file("bar/lib/src/main/resources/res1.txt").toPath()
        fooRes.deleteExisting()
        barRes.appendText(" edited")
        project.execute(":app:assembleDebug")
        project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            assertThat(apk).containsJavaResource("res1.txt")
            assertThat(apk.getJavaResource("res1.txt").readText().trim()).isEqualTo("res 1 from bar edited")
        }
        fooRes.writeText("res 1 from foo added back")
        barRes.appendText(" twice")
        project.execute(":app:assembleDebug")
        project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            assertThat(apk).containsJavaResource("res1.txt")
            assertThat(apk.getJavaResource("res1.txt").readText().trim()).isEqualTo("res 1 from foo added back")
        }
    }
}
