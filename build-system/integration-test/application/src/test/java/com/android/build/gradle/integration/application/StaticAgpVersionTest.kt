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

package com.android.build.gradle.integration.application

import com.android.build.api.extension.impl.CurrentAndroidGradlePluginVersion.CURRENT_AGP_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import org.junit.Rule
import org.junit.Test

class StaticAgpVersionTest {
    @JvmField
    @Rule
    val project = GradleTestProject.builder()
        .fromTestApp(MinimalSubProject.app("com.example")
            .appendToBuild(
                // language=groovy
                """
                import com.android.build.api.AndroidPluginVersion

                task checkAgpVersion {
                    doLast {
                        def currentPluginVersion = AndroidPluginVersion.getCurrent()
                        assert currentPluginVersion != null
                        println currentPluginVersion
                    }
                }
            """.trimIndent()))
        .create()

    @Test
    fun testStaticAgpVersion() {
        project.executor().run("checkAgpVersion")
            .assertOutputContains("$CURRENT_AGP_VERSION")
    }
}
