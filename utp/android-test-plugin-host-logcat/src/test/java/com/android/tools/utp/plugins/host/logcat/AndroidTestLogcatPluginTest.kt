/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.utp.plugins.host.logcat

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.tools.utp.plugins.host.logcat.proto.AndroidTestLogcatConfigProto.AndroidTestLogcatConfig
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.testing.platform.api.config.ConfigBase
import com.google.testing.platform.api.config.Environment
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.api.config.environment
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.device.CommandHandle
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.event.Events
import com.google.testing.platform.api.plugin.sendIssue
import com.google.testing.platform.api.plugin.sendTestResultUpdate
import com.google.testing.platform.proto.api.core.IssueProto
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.controller.ext.deviceShell
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.quality.Strictness
import java.util.logging.Logger

/**
 * Unit tests for [AndroidTestLogcatPlugin]
 */
@RunWith(JUnit4::class)
class AndroidTestLogcatPluginTest {
    @get:Rule val mockitoJUnitRule = MockitoJUnit.rule().strictness(Strictness.LENIENT)
    @get:Rule var tempFolder: TemporaryFolder = TemporaryFolder()

    @Mock private lateinit var mockCommandHandle: CommandHandle
    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockEvents: Events
    @Mock (extraInterfaces = [ConfigBase::class])
    private lateinit var mockConfig: ProtoConfig
    @Mock private lateinit var mockDeviceController: DeviceController
    @Mock private lateinit var mockLogger: Logger

    private lateinit var androidTestLogcatPlugin: AndroidTestLogcatPlugin
    private lateinit var emptyTestResult: TestResult
    private lateinit var passedTestSuiteResult: TestSuiteResult
    private lateinit var environment: Environment

    private val testDeviceTime = "01-01 00:00:00"
    private val logcatOptions = listOf("shell", "logcat", "-v", "threadtime", "-b", "main", "-b", "crash")
    private val logcatOutputText = """
        04-28 23:18:49.444  1887  1988 I TestRunner: started: (.)
        04-28 23:18:50.444  1887  1988 I ExampleTestApp: test logcat output
        04-28 23:18:51.444  1887  1988 I TestRunner: finished: (.)
    """.trimIndent()
    private val testPackageName = "com.example.myapplication"
    private val crashLogcatOutputText = """
        10-27 15:30:28.456 22746 22746 E AndroidRuntime: Process: ${testPackageName}, PID: 22746
        10-27 15:30:28.456 22746 22746 E AndroidRuntime: 	at dalvik.system.BaseDexClassLoader
        10-27 15:30:28.456 22746 22746 E AndroidRuntime: 	at java.lang.ClassLoader.loadClass
        10-27 15:30:28.456 22746 22746 E AndroidRuntime: 	at java.lang.ClassLoader.loadClass
        10-27 15:30:28.456 22746 22746 E AndroidRuntime: 	at android.app.ActivityThread
        10-27 15:30:28.456 22746 22746 E AndroidRuntime: 	... 10 more
        10-27 15:30:28.457 22746 22746 I Process : Sending signal. PID: 22746 SIG: 9
    """.trimIndent()
    private val testCrashIndicator = "E AndroidRuntime: "

    @Before
    fun setUp() {
        environment = Environment(tempFolder.root.path, "", "", "", "", null)
        emptyTestResult = TestResult.newBuilder().build()
        passedTestSuiteResult = TestSuiteResult.newBuilder().apply {
            testStatus = TestStatusProto.TestStatus.PASSED
        }.build()
        androidTestLogcatPlugin = AndroidTestLogcatPlugin(mockLogger)

        `when`(mockContext[eq(Context.CONFIG_KEY)]).thenReturn(mockConfig)
        `when`(mockContext[eq(Context.EVENTS_KEY)]).thenReturn(mockEvents)
        `when`(mockConfig.environment).thenReturn(environment)
        `when`(mockConfig.configProto).thenReturn(Any.pack(
                AndroidTestLogcatConfig.newBuilder().apply {
                    targetTestProcessName = this@AndroidTestLogcatPluginTest.testPackageName
                }.build()
        ))
        `when`(mockDeviceController.deviceShell(listOf("date", "+%m-%d\\ %H:%M:%S")))
                .thenReturn(CommandResult(0, listOf(testDeviceTime)))
        `when`(mockDeviceController.executeAsync(
                eq(listOf(
                        "shell", "logcat",
                        "-v", "threadtime",
                        "-b", "main",
                        "-b", "crash",
                        "-T", "\'$testDeviceTime.000\'")),
                any())).then  {
            val outputTextProcessor: (String) -> Unit = it.getArgument(1)
            logcatOutputText.lines().forEach(outputTextProcessor)
            mockCommandHandle
        }
    }

    @Test
    fun beforeAll_startsLogcatStreamWithExpectedLogcatOptions() {
        androidTestLogcatPlugin.configure(mockContext)
        androidTestLogcatPlugin.beforeAll(mockDeviceController)

        val expectedLogcatOptions = mutableListOf<String>()
        expectedLogcatOptions.addAll(logcatOptions)
        expectedLogcatOptions.addAll(listOf("-T", "\'$testDeviceTime.000\'"))

        verify(mockDeviceController).executeAsync(eq(expectedLogcatOptions), any())
    }

    @Test
    fun afterEach_addsLogcatArtifacts() {
        val testResult = androidTestLogcatPlugin.run {
            configure(mockContext)
            beforeAll(mockDeviceController)
            beforeEach(emptyTestResult.testCase, mockDeviceController)
            afterEachWithReturn(emptyTestResult, mockDeviceController)
        }

        assertThat(testResult.outputArtifactList).isNotEmpty()
        testResult.outputArtifactList.forEach {
            assertThat(it.label.namespace).isEqualTo("android")
            assertThat(it.label.label).isEqualTo("logcat")
            assertThat(it.sourcePath.path).endsWith("logcat-.-.txt")
        }
        verify(mockEvents).sendTestResultUpdate(testResult)
    }

    @Test
    fun afterAll_stopsLogcatStream() {
        androidTestLogcatPlugin.configure(mockContext)
        androidTestLogcatPlugin.beforeAll(mockDeviceController)
        androidTestLogcatPlugin.afterEach(emptyTestResult, mockDeviceController)
        androidTestLogcatPlugin.afterAll(passedTestSuiteResult, mockDeviceController)

        verify(mockCommandHandle).stop()
    }

    @Test
    fun canRun_isTrue() {
        assertThat(androidTestLogcatPlugin.canRun()).isTrue()
    }

    @Test
    fun doNotDisplayWarningIfAfterAllIsCalledWithoutBeforeAll() {
        androidTestLogcatPlugin.configure(mockContext)
        // afterAll() may be invoked without beforeAll() when there is a runtime error
        // in other UTP plugins.
        androidTestLogcatPlugin.afterAll(passedTestSuiteResult, mockDeviceController)

        verifyNoInteractions(mockLogger)
    }

    @Test
    fun afterAll_catchesCrashLogcat() {
        `when`(mockDeviceController.executeAsync(
                eq(listOf(
                        "shell", "logcat",
                        "-v", "threadtime",
                        "-b", "main",
                        "-b", "crash",
                        "-T", "\'$testDeviceTime.000\'")),
                any())).then  {
            val outputTextProcessor: (String) -> Unit = it.getArgument(1)
            crashLogcatOutputText.lines().forEach(outputTextProcessor)
            mockCommandHandle
        }
        val crashedTestSuiteResult = TestSuiteResult.newBuilder().apply {
            testStatus = TestStatusProto.TestStatus.FAILED
        }.build()

        androidTestLogcatPlugin.configure(mockContext)
        androidTestLogcatPlugin.beforeAll(mockDeviceController)
        val finalTestSuiteResult =  androidTestLogcatPlugin.afterAllWithReturn(
            crashedTestSuiteResult, mockDeviceController)

        verify(mockCommandHandle).stop()
        val lastIssue = finalTestSuiteResult.issueList.last()
        assertThat(lastIssue.severity).isEqualTo(IssueProto.Issue.Severity.SEVERE)
        assertThat(lastIssue.message).contains("Logcat of last crash:")
        assertThat(lastIssue.message).contains(
                "... 10 more")
        verify(mockEvents).sendIssue(lastIssue)
    }
}
