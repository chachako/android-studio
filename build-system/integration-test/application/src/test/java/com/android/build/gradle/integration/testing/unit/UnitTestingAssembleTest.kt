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

package com.android.build.gradle.integration.testing.unit

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File

class UnitTestingAssembleTest {

    @JvmField
    @Rule
    val project = builder().fromTestProject("unitTesting").create()

    @Test
    fun testAssembleTasks() {
        // android resources related tasks should not run but the rest should.
        var result = project.executor().run("assembleUnitTest")
        Truth.assertThat(result.didWorkTasks.contains("compileDebugUnitTest"))
        Truth.assertThat(result.didWorkTasks).doesNotContain(":packageDebugUnitTestForUnitTest")

        TestFileUtils.appendToFile(project.buildFile,
            """
                android.testOptions.unitTests.includeAndroidResources = true
            """.trimIndent()
        )

        result = project.executor().run("assembleUnitTest")
        // This is skipped because it only has other task dependencies (doesn't do any actual work)
        Truth.assertThat(result.skippedTasks).contains(":assembleDebugUnitTest")
        Truth.assertThat(result.didWorkTasks).contains(":packageDebugUnitTestForUnitTest")

        project.executor().run("assembleDebugUnitTest")
    }

    @Test
    fun testAssembleTaskRequiresCompilation() {
        TestFileUtils.appendToFile(
            File(File(project.mainTestDir, "java"), "SomeBuggyClass.kt"),
            """"
                public GARBAGE { }
            """)
        val result = project.executor().expectFailure().run("assembleUnitTest")
        Truth.assertThat(result.failedTasks).isNotEmpty()
        Truth.assertThat(result.failedTasks).containsExactly(":compileDebugUnitTestKotlin", ":compileReleaseUnitTestKotlin")
    }
}
