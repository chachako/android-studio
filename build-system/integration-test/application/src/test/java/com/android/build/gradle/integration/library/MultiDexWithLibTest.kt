/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.utils.AssumeBuildToolsUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/** Assemble tests for multiDexWithLib.  */
class MultiDexWithLibTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @get:Rule
    val project: GradleTestProject = builder()
            .fromTestProject("multiDexWithLib")
            .withHeap("2048M")
            .create()

    @Before
    @Throws(IOException::class, InterruptedException::class)
    fun setUp() {
        AssumeBuildToolsUtil.assumeBuildToolsAtLeast(21)
    }
    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun lint() {
        project.executor().run("clean", "assembleDebug", "assembleDebugAndroidTest", "lint")
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testAppVariantSettings() {
        project.getSubproject(":app").buildFile.appendText("""
            android.buildTypes.debug.multiDexEnabled=false

            androidComponents {
                beforeVariants(selector().withBuildType("debug"), { debugVariantBuilder ->
                    debugVariantBuilder.enableMultiDex = true
                })
            }
        """.trimIndent())
        project.execute("clean", "assembleDebug")
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testWithAndroidTestInLibraryVariantSettings() {
        project.getSubproject(":lib").buildFile.appendText("""
            android.buildTypes.debug.multiDexEnabled=false

            androidComponents {
                beforeVariants(selector().withBuildType("debug"), { debugVariantBuilder ->
                    debugVariantBuilder.androidTest.enableMultiDex = true
                })
            }
        """.trimIndent())
        project.execute("clean", "assembleDebugAndroidTest")
    }


}
