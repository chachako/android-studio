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

package com.android.build.gradle.integration.compose

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.internal.CompileOptions.Companion.DEFAULT_JAVA_VERSION
import com.android.build.gradle.options.BooleanOption
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests Compose plugin options for KotlinCompile. */
class ComposePluginOptionsTest {

    @get:Rule
    val project = createGradleProject {
        withKotlinPlugin = true
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            plugins.add(PluginType.KOTLIN_ANDROID)
            android {
                defaultCompileSdk()
                minSdk = 24
                buildFeatures {
                    compose = true
                }
                kotlinOptions {
                    jvmTarget = DEFAULT_JAVA_VERSION.toString()
                }
            }
            addFile(
                "src/main/java/com/example/KotlinClass.kt",
                // language=kotlin
                """
                class KotlinClass
                """.trimIndent()
            )
        }
        gradleProperties {
            set(BooleanOption.USE_ANDROID_X, true)
        }
    }

    @Before
    fun setUp() {
        project.getSubproject(":app").buildFile.appendText("\n" +
            """
            android {
                kotlinOptions {
                    languageVersion = "1.9"
                    freeCompilerArgs += ["-P", "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true"]
                }
                composeOptions {
                    kotlinCompilerExtensionVersion = "+"
                }
                dependencies {
                    implementation("androidx.compose.runtime:runtime:+")
                }
            }
            """.trimIndent()
        )
    }

    /** Regression test for b/318384658. */
    @Test
    fun `test AGP does not override user-specified plugin options`() {
        project.getSubproject(":app").buildFile.appendText("\n" +
            """
            android {
                kotlinOptions {
                    freeCompilerArgs += [ "-P", "plugin:androidx.compose.compiler.plugins.kotlin:sourceInformation=false"]
                }
            }
            """.trimIndent()
        )

        project.execute(":app:compileDebugKotlin")
    }
}
