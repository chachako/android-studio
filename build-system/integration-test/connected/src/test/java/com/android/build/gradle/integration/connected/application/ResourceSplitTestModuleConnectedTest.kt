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

package com.android.build.gradle.integration.connected.application

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.build.gradle.options.BooleanOption
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class ResourceSplitTestModuleConnectedTest {

    companion object {
        @JvmField @ClassRule
        val emulator = getEmulator()
    }

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("separateTestModule")
        .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """
                android.splits {
                    abi {
                        enable true
                        reset()
                        include("x86", "x86_64", "arm64-v8a")
                        universalApk true
                    }
                }
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
            project.getSubproject("test").buildFile,
            """
                android {
                    defaultConfig {
                        testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'
                    }
                    dependencies {
                        implementation ('androidx.test:runner:1.4.0-alpha06', {
                          exclude group: 'com.android.support', module: 'support-annotations'
                        })
                    }
                }
            """
        )

        // fail fast if no response
        project.addAdbTimeout()
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .run("uninstallAll")
    }

    // Regression test for b/341266993
    @Test
    fun resourceSplitWithTestModuleAndroidTest() {
        project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .run(":test:connectedCheck")
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "universalApk true",
            "universalApk false"
        )
        project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .run(":test:connectedCheck")
    }
}
