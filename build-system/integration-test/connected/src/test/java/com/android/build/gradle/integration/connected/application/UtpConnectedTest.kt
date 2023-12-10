/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.build.gradle.integration.utp.UtpTestBase
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import java.io.IOException
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import com.android.tools.perflogger.Benchmark;
/**
 * Connected tests using UTP test executor.
 */
class UtpConnectedTest : UtpTestBase() {
    private val connectedAndroidTestWithUtpBenchmark: Benchmark = Benchmark.Builder("connectedAndroidTestWithUtp").setProject("Android Studio Gradle").build()

    companion object {
        @ClassRule
        @JvmField
        val EMULATOR = getEmulator()

        private const val TEST_RESULT_XML = "build/outputs/androidTest-results/connected/debug/TEST-emulator-5554 - 13-_"
        private const val LOGCAT = "build/outputs/androidTest-results/connected/debug/emulator-5554 - 13/logcat-com.example.android.kotlin.ExampleInstrumentedTest-useAppContext.txt"
        private const val TEST_REPORT = "build/reports/androidTests/connected/debug/com.example.android.kotlin.html"
        private const val TEST_RESULT_PB = "build/outputs/androidTest-results/connected/debug/emulator-5554 - 13/test-result.pb"
        private const val AGGREGATED_TEST_RESULT_PB = "build/outputs/androidTest-results/connected/debug/test-result.pb"
        private const val TEST_COV_XML = "build/reports/coverage/androidTest/debug/connected/report.xml"
        private const val ENABLE_UTP_TEST_REPORT_PROPERTY = "com.android.tools.utp.GradleAndroidProjectResolverExtension.enable"
        private const val TEST_ADDITIONAL_OUTPUT = "build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/emulator-5554 - 13"
    }

    @Before
    @Throws(IOException::class)
    fun setUp() {
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll")
    }

    override fun selectModule(moduleName: String) {
        testTaskName = ":${moduleName}:connectedAndroidTest"
        testResultXmlPath = "${moduleName}/$TEST_RESULT_XML${moduleName}-.xml"
        testReportPath = "${moduleName}/$TEST_REPORT"
        testResultPbPath = "${moduleName}/$TEST_RESULT_PB"
        aggTestResultPbPath = "${moduleName}/$AGGREGATED_TEST_RESULT_PB"
        testCoverageXmlPath = "${moduleName}/$TEST_COV_XML"
        testLogcatPath = "${moduleName}/$LOGCAT"
        testAdditionalOutputPath = "${moduleName}/${TEST_ADDITIONAL_OUTPUT}"
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithUtpTestResultListener() {
        val benchmark: Benchmark = Benchmark.Builder("connectedAndroidTestWithUtpTestResultListener").setProject("Android Studio Gradle").build()
        val startTime: Long = java.lang.System.currentTimeMillis()
        selectModule("app")
        val initScriptPath = TestUtils.resolveWorkspacePath(
                "tools/adt/idea/utp/addGradleAndroidTestListener.gradle")

        var testExecutionStartTime: Long = java.lang.System.currentTimeMillis()
        val result = project.executor()
                .withArgument("--init-script")
                .withArgument(initScriptPath.toString())
                .withArgument("-P${ENABLE_UTP_TEST_REPORT_PROPERTY}=true")
                .run(testTaskName)
        var testExecutionTime = java.lang.System.currentTimeMillis() - testExecutionStartTime
        connectedAndroidTestWithUtpBenchmark.log("connectedAndroidTestWithUtpTestResultListenerExecution_time", testExecutionTime)

        result.stdout.use {
            assertThat(it).contains("<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")
            assertThat(it).contains("</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")
        }
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()

        // Run the task again after clean. This time the task configuration is
        // restored from the configuration cache. We expect no crashes.
        project.executor().run("clean")

        assertThat(project.file(testReportPath)).doesNotExist()
        assertThat(project.file(testResultPbPath)).doesNotExist()

        testExecutionStartTime = java.lang.System.currentTimeMillis()
        val resultWithConfigCache = project.executor()
                .withArgument("--init-script")
                .withArgument(initScriptPath.toString())
                .withArgument("-P${ENABLE_UTP_TEST_REPORT_PROPERTY}=true")
                .run(testTaskName)
        testExecutionTime = java.lang.System.currentTimeMillis() - testExecutionStartTime
        connectedAndroidTestWithUtpBenchmark.log("connectedAndroidTestWithUtpTestResultListenerWithConfigCacheExecution_time", testExecutionTime)

        resultWithConfigCache.stdout.use {
            assertThat(it).contains("<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")
            assertThat(it).contains("</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")
        }
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        val timeTaken = java.lang.System.currentTimeMillis() - startTime
        benchmark.log("connectedAndroidTestWithUtpTestResultListener_time", timeTaken)
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithUtpTestResultListenerAndTestReportingDisabled() {
        val benchmark: Benchmark = Benchmark.Builder("connectedAndroidTestWithUtpTestResultListenerAndTestReportingDisabled").setProject("Android Studio Gradle").build()
        val startTime: Long = java.lang.System.currentTimeMillis()
        selectModule("app")
        val initScriptPath = TestUtils.resolveWorkspacePath(
                "tools/adt/idea/utp/addGradleAndroidTestListener.gradle")

        val testExecutionStartTime: Long = java.lang.System.currentTimeMillis()
        val result = project.executor()
                .withArgument("--init-script")
                .withArgument(initScriptPath.toString())
                .run(testTaskName)
        val testExecutionTime = java.lang.System.currentTimeMillis() - testExecutionStartTime
        connectedAndroidTestWithUtpBenchmark.log("connectedAndroidTestWithUtpTestResultListenerAndTestReportingDisabledExecution_time", testExecutionTime)

        result.stdout.use {
            assertThat(it).doesNotContain("<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")
            assertThat(it).doesNotContain("</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")
        }
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        val timeTaken = java.lang.System.currentTimeMillis() - startTime
        benchmark.log("connectedAndroidTestWithUtpTestResultListenerAndTestReportingDisabled_time", timeTaken)
    }
}
