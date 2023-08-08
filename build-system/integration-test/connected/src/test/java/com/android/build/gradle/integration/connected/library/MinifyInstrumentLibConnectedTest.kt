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

package com.android.build.gradle.integration.connected.library

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.connected.utils.getEmulator
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class MinifyInstrumentLibConnectedTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestProject("libMinifyLibDep")
            .create()

    @Test
    fun testNonMinify() {
        project.executor().run("lib:connectedAndroidTest")
    }

    @Test
    fun testMinify() {
        // enable minify for android test in lib which is the benchmark lib project
        val libProject = project.getSubproject("lib")
        TestFileUtils.appendToFile(
            libProject.buildFile,
            """
                android.buildTypes.debug.androidTest.enableMinification = true
            """.trimIndent()
        )
        TestFileUtils.addMethod(
            libProject.projectDir.resolve(
                "src/androidTest/java/com/android/tests/basic/StringGetterTest.java"),
            """
                @Test
                public void testFunctionFromDepLibrary() {
                    // Use function from dependency lib2
                    String one = com.android.tests.basic.StringProvider.getString(1);
                    org.junit.Assert.assertEquals(one, "1");
                }
            """.trimIndent()
        )
        val testProguardFileName = "testProguardRules.pro"
        val testProguardFile = libProject.projectDir.resolve(testProguardFileName)
        TestFileUtils.appendToFile(
            testProguardFile,
            """
                -keep class android.support.test.runner.AndroidJUnitRunner {*;}
                -keep class android.support.test.runner.lifecycle.Stage {*;}
                -keep class android.support.test.runner.AndroidJUnit4 {*;}
                -keep class com.android.tests.basic.StringGetterTest {
                    public void testFunctionFromDepLibrary();
                    <init>(...);
                 }
                 # to keep the @RunWith
                -keepattributes *Annotation*
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
            libProject.buildFile,
            "android.buildTypes.debug.testProguardFiles('$testProguardFileName')"
        )
        project.executor().run("lib:connectedAndroidTest")
    }

    companion object {
        @JvmField
        @ClassRule
        val emulator: ExternalResource = getEmulator()
    }
}
