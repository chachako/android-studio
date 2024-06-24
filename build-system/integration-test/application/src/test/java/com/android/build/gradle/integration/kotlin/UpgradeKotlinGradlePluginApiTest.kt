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
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_TESTS
import org.junit.Rule
import org.junit.Test

class UpgradeKotlinGradlePluginApiTest {

    @get:Rule
    val project = createGradleProjectBuilder {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            plugins.add(PluginType.ANDROID_BUILT_IN_KOTLIN)
            android {
                setUpHelloWorld()
            }
            appendToBuildFile { builtInKotlinSupportDependencies }
        }
    }.withBuiltInKotlinSupport(true)
        .withKotlinGradlePlugin(true)
        .create()

    /**
     * Test that users can upgrade the version of kotlin-gradle-plugin-api by adding KGP to their
     * buildscript classpath with the desired version.
     */
    @Test
    fun testUpgradingKotlinBaseApiPlugin() {
        val app = project.getSubproject(":app")
        app.getMainSrcDir("java")
            .resolve("AppFoo.kt")
            .let {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package com.foo.application
                        class AppFoo
                        """.trimIndent()
                )
            }
        app.executor().run(":app:assembleDebug")
        val result = project.executor().run("buildEnvironment")
        ScannerSubject.assertThat(result.stdout)
            .contains("org.jetbrains.kotlin:kotlin-gradle-plugin-api:$KOTLIN_VERSION_FOR_TESTS")
    }
}
