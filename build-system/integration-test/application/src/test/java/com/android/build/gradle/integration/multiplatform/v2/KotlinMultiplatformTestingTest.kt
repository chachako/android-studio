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

package com.android.build.gradle.integration.multiplatform.v2

import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test

class KotlinMultiplatformTestingTest {

    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .create()

    /**
     * Regression test for b/369130174
     */
    @Test
    fun testReturnDefaultValues() {
        val kmpFirstLib = project.getSubproject("kmpFirstLib")
        // Sst isReturnDefaultValues to true
        TestFileUtils.appendToFile(
            kmpFirstLib.ktsBuildFile,
            """
                kotlin.androidLibrary.compilations.withType(
                    com.android.build.api.dsl.KotlinMultiplatformAndroidHostTestCompilation::class.java
                ) {
                    isReturnDefaultValues = true
                }
            """.trimIndent()
        )

        // Add a test that requires isReturnDefaultValues to be true
        val testFile =
            FileUtils.join(
                kmpFirstLib.projectDir,
                "src",
                "androidTestOnJvm",
                "kotlin",
                "com",
                "example",
                "kmpfirstlib",
                "ReturnDefaultValuesTest.kt"
            )
        testFile.parentFile.mkdirs()
        // This is similar to the unit test file in the unitTestingDefaultValues test project.
        TestFileUtils.appendToFile(
            testFile,
            """
                package com.example.kmpfirstlib

                import android.opengl.Matrix
                import android.os.Debug
                import android.util.ArrayMap
                import org.junit.Assert
                import org.junit.Test

                class ReturnDefaultValuesTest {
                    @Test
                    fun defaultValues() {
                        val map = ArrayMap<Any, Any>()

                        // Check different return types.
                        map.clear()
                        Assert.assertEquals(0, map.size)
                        Assert.assertEquals(false, map.isEmpty())
                        Assert.assertNull(map.keys)

                        // Check a static method as well.
                        Assert.assertEquals(0, Debug.getGlobalAllocCount())

                        // Check a native method converted to a non-native one in the mockable jar.
                        val result = FloatArray(16)
                        val operand = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
                        Matrix.multiplyMM(result, 0, operand, 0, operand, 0)
                        Assert.assertArrayEquals(FloatArray(16), result, 0f)
                    }
                }
            """.trimIndent()
        )

        // Check that the test runs successfully
        project.executor().run(":kmpFirstLib:testAndroidTestOnJvm")

        // Check that the test fails as expected after toggling isReturnDefaultValues to false
        TestFileUtils.searchAndReplace(
            kmpFirstLib.ktsBuildFile,
            "isReturnDefaultValues = true",
            "isReturnDefaultValues = false"
        )
        project.executor().expectFailure().run(":kmpFirstLib:testAndroidTestOnJvm")
    }
}
