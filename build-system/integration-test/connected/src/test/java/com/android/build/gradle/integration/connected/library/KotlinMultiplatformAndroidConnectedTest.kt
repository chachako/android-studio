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

import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class KotlinMultiplatformAndroidConnectedTest {

    companion object {
        @JvmField
        @ClassRule
        val emulator = getEmulator()
    }

    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
               kotlin.androidLibrary.compilations.withType(
                 com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnDeviceCompilation::class.java
               ) {
                  enableCoverage = true
               }
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin.sourceSets.getByName("androidInstrumentedTest").dependencies {
                    implementation("androidx.core:core-ktx:1.1.0")
                    implementation("androidx.test.espresso:espresso-core:3.2.0")
                }
            """.trimIndent()
        )

        // fail fast if no response
        project.addAdbTimeout()
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.executor().withFailOnWarning(false).run("androidUninstallAll")
    }

    @Test
    fun connectedKmpLibraryTests() {
        // TODO (b/293964676): remove withFailOnWarning(false) once KMP bug is fixed
        project.executor()
            .withFailOnWarning(false)
            .run(":kmpFirstLib:androidConnectedCheck")

        val testResultFolder = FileUtils.join(
            project.getSubproject("kmpFirstLib").buildDir,
            "reports", "androidTests", "connected", "androidMain"
        )

        Truth.assertThat(testResultFolder.exists()).isTrue()

        Truth.assertThat(testResultFolder.listFiles()!!.map { it.name }).containsAtLeast(
            "com.example.kmpfirstlib.test.html",
            "com.example.kmpfirstlib.test.KmpAndroidFirstLibActivityTest.html",
        )

        val coveragePackageFolder = FileUtils.join(
            project.getSubproject("kmpFirstLib").buildDir,
            "reports", "coverage", "androidTest", "main", "connected", "com.example.kmpfirstlib"
        )

        Truth.assertThat(coveragePackageFolder.exists()).isTrue()

        Truth.assertThat(coveragePackageFolder.listFiles()!!.map { it.name }).containsExactly(
            "index.html",
            "index.source.html",

            "KmpCommonFirstLibClass.html",
            "KmpCommonFirstLibClass.kt.html",

            "KmpAndroidActivity.html",
            "KmpAndroidActivity.kt.html",

            "KmpAndroidFirstLibClass.html",
            "KmpAndroidFirstLibClass.kt.html",

            "KmpAndroidFirstLibJavaClass.html",
            "KmpAndroidFirstLibJavaClass.java.html",
        )

        val packageCoverageReport = FileUtils.join(
            coveragePackageFolder,
            "index.html"
        )

        val generatedCoverageReportHTML = packageCoverageReport.readLines().joinToString("\n")

        val totalCoverageMetricsContents = Regex("<tfoot>(.*?)</tfoot>")
            .find(generatedCoverageReportHTML)
        val totalCoverageInfo = Regex("<td class=\"ctr2\">(.*?)</td>")
            .find(totalCoverageMetricsContents?.groups?.first()!!.value)

        val packageCoveragePercentage = totalCoverageInfo!!.groups[1]!!.value

        Truth.assertThat(packageCoveragePercentage.trimEnd('%').toInt() > 0).isTrue()
    }
}
