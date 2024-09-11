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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.model.ReferenceModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class CompileSdkViaSettingsInAppModelTest {
    @get:Rule
    val project = createGradleProject {
        settings {
            plugins.add(PluginType.ANDROID_SETTINGS)
            android {
                compileSdk = DEFAULT_COMPILE_SDK_VERSION
            }
        }
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld(setupDefaultCompileSdk = false)
            }
        }
    }

    @Test
    fun `test compileTarget`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        val androidDsl = result.container.getProject().androidDsl
            ?: throw RuntimeException("Failed to get AndroidDsl Model")

        Truth.assertWithMessage("compile target hash")
            .that(androidDsl.compileTarget)
            .isEqualTo("android-$DEFAULT_COMPILE_SDK_VERSION")
    }
}

/**
 * This tests uses a reference project where the compile SDK is set through the settings
 * plugin to 24.
 *
 * Then the project is changed to override this at the project level with the current default
 * API level.
 */
class CompileSdkViaSettingsOverriddenInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        settings {
            plugins.add(PluginType.ANDROID_SETTINGS)
            android {
                compileSdk = 24
            }
        }
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld(setupDefaultCompileSdk = false)
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                compileSdk = GradleTestProject.DEFAULT_COMPILE_SDK_VERSION.toInt()
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}
