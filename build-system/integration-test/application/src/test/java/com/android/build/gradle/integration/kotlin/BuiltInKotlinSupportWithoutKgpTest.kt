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

package com.android.build.gradle.integration.kotlin

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.dsl.ModulePropertyKey.BooleanWithDefault.SCREENSHOT_TEST
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

class BuiltInKotlinSupportWithoutKgpTest {

    @get:Rule
    val project =
        createGradleProjectBuilder {
            subProject(":lib") {
                plugins.add(PluginType.ANDROID_LIB)
                android {
                    setUpHelloWorld()
                }
            }
        }.create()

    @Test
    fun testKgpMissingFromClasspath() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_SCREENSHOT_TEST.propertyName}=true"
        )
        val lib = project.getSubproject(":lib")
        lib.buildFile.appendText(
            """
                android.experimentalProperties["${SCREENSHOT_TEST.key}"] = true
                """.trimIndent()
        )
        val result = lib.executor().expectFailure().run(":lib:assembleDebug")
        result.assertErrorContains("The Kotlin Gradle plugin was not found")
    }
}
