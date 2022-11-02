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

import com.android.tools.utp.plugins.host.logcat.proto.AndroidTestLogcatConfigProto.AndroidTestLogcatConfig
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.api.config.environment
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.device.CommandHandle
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.plugin.HostPlugin
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.proto.api.core.IssueProto
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestCaseProto.TestCase
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.controller.ext.deviceShell
import java.io.BufferedWriter
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

/**
 * This plugin updates [TestSuiteResult] proto with logcat artifacts
 */
class AndroidTestLogcatPlugin(
    private val logger: Logger = getLogger()) : HostPlugin {

    // Empty companion object is needed to call getLogger() method
    // from the constructor's default parameter. If you remove it,
    // the Kotlin compiler fails with an error.
    companion object {
        private const val TEST_CRASH_INDICATOR = "E AndroidRuntime: "
    }

    private lateinit var outputDir: String
    private lateinit var tempLogcatFile: File
    private lateinit var tempLogcatWriter: BufferedWriter
    private lateinit var logcatCommandHandle: CommandHandle
    private lateinit var crashLogcatFile: File
    private lateinit var crashLogcatWriter: BufferedWriter
    private lateinit var targetTestProcessName: String
    private lateinit var crashLogcatStartMatcher: Regex
    private lateinit var testPid: String

    private val crashLogFinished = CountDownLatch(1)
    // Heuristic value for reading 5 lines of logcat message before deciding if there's a crash
    private val logcatCounter = CountDownLatch(5)
    private var logcatFilePaths: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var logcatOptions: List<String> = mutableListOf()
    private var crashHappened: AtomicBoolean = AtomicBoolean(false)

    private val  crashLogcatFinishMatcher: Regex by lazy {
        Regex(".*I\\sProcess.*Sending\\ssignal.*PID:.*${testPid}.*SIG:\\s9")
    }
    private val crashLogcatProgressMatcher: Regex by lazy {
        Regex(".*${testPid}.*${TEST_CRASH_INDICATOR}.*")
    }

    override fun configure(context: Context) {
        val config = context[Context.CONFIG_KEY] as ProtoConfig
        outputDir = config.environment.outputDirectory
        this.targetTestProcessName = AndroidTestLogcatConfig
                .parseFrom(config.configProto!!.value).targetTestProcessName
        crashLogcatStartMatcher = Regex(
                ".*E\\sAndroidRuntime:\\sProcess:\\s${targetTestProcessName}.*")
    }

    override fun beforeAll(deviceController: DeviceController) {
        logger.fine("Start logcat streaming.")
        logcatCommandHandle = startLogcatAsync(deviceController)
    }

    override fun beforeEach(
            testCase: TestCase?,
            deviceController: DeviceController
    ) {}

    override fun afterEach(
        testResult: TestResult,
        deviceController: DeviceController,
        cancelled: Boolean
    ): TestResult {
        val testCase = testResult.testCase
        val packageName = testCase.testPackage
        val className = testCase.testClass
        val methodName = testCase.testMethod
        val updatedTestResult = testResult.toBuilder().apply {
            synchronized (logcatFilePaths) {
                logcatFilePaths.forEach {
                    if (it == generateLogcatFileName("$packageName.$className", methodName)) {
                        addOutputArtifact(
                                TestArtifactProto.Artifact.newBuilder().apply {
                                    labelBuilder.label = "logcat"
                                    labelBuilder.namespace = "android"
                                    sourcePathBuilder.path = it
                                }.build()
                        )
                    }
                }
            }
        }.build()
        return updatedTestResult
    }

    override fun afterAll(
        testSuiteResult: TestSuiteResult,
        deviceController: DeviceController,
        cancelled: Boolean
    ): TestSuiteResult {
        var updatedTestSuiteResult = testSuiteResult
        // CountDownLatch await with timeout means that the latch will release after timeout.
        // It continues immediately if thread is interrupted or latch reaches 0 before timeout
        // We wait here in case there's a delay in reading logcat message
        logcatCounter.await(2, TimeUnit.SECONDS)
        if(crashHappened.get()) {
            crashLogFinished.await(2, TimeUnit.SECONDS)
            updatedTestSuiteResult = testSuiteResult.toBuilder().apply {
                addIssue(IssueProto.Issue.newBuilder().apply {
                    severity = IssueProto.Issue.Severity.SEVERE
                    message = "Logcat of last crash: \n" +
                            crashLogcatFile.bufferedReader().use { it.readText() }
                }.build())
            }.build()
        }
        stopLogcat()
        return updatedTestSuiteResult
    }

    override fun canRun(): Boolean = true

    /**
     * Generates the logcat file name using the output directory and test class and test method.
     */
    private fun generateLogcatFileName(
            testPackageAndClass: String,
            testMethod: String
    ) = File(outputDir, "logcat-$testPackageAndClass-$testMethod.txt").absolutePath

    /** Gets current date time on device. */
    private fun getDeviceCurrentTime(deviceController: DeviceController): String? {
        val dateCommandResult = deviceController.deviceShell(listOf("date", "+%m-%d\\ %H:%M:%S"))
        if (dateCommandResult.statusCode != 0 || dateCommandResult.output.isEmpty()) {
            logger.warning("Failed to read device time.")
            return null
        }
        return "\'${dateCommandResult.output[0]}.000\'"
    }

    /** Set up logcat command args. */
    private fun setUpLogcatCommandLine(): List<String> {
        val logcatCommand = mutableListOf<String>()
        with(logcatCommand) {
            add("shell")
            add("logcat")
            add("-v")
            add("threadtime")
            add("-b")
            add("main")
            add("-b")
            add("crash")
            addAll(logcatOptions)
        }
        return logcatCommand
    }

    /** Start logcat streaming. */
    private fun startLogcatAsync(controller: DeviceController): CommandHandle {
        val deviceTime = getDeviceCurrentTime(controller)
        if (deviceTime != null) {
            logcatOptions = mutableListOf("-T", deviceTime)
        }
        var testRunInProgress = false
        return controller.executeAsync(setUpLogcatCommandLine()) { line ->
            logcatCounter.countDown()
            // Use regular expression to find start of the logcat for the crash
            // Should be similar to this line:
            // 10-27 15:26:52.863 22058 22058 E AndroidRuntime: Process: com.example.myapplication4, PID: 22058
            if (!crashHappened.get() && line.matches(crashLogcatStartMatcher)) {
                crashLogcatFile = File(generateLogcatFileName(targetTestProcessName, "crash-report"))
                crashLogcatWriter = crashLogcatFile.outputStream().bufferedWriter()
                crashHappened.set(true)
                testPid = line.split(" ").last()
            }
            if (crashHappened.get() && line.matches(crashLogcatProgressMatcher)){
                crashLogcatWriter.write(line.split(TEST_CRASH_INDICATOR).last())
                crashLogcatWriter.newLine()
                crashLogcatWriter.flush()
            }
            // Use regular expression to find end of the logcat for the crash
            // Should be similar to this line:
            // 10-27 15:26:52.864 22058 22058 I Process : Sending signal. PID: 22058 SIG: 9
            if (crashHappened.get() && line.matches(crashLogcatFinishMatcher))
                crashLogFinished.countDown()

            if (line.contains("TestRunner: started: ")) {
                testRunInProgress = true
                parseLine(line)
            }
            if (testRunInProgress) {
                tempLogcatWriter.write(line)
                tempLogcatWriter.newLine()
                tempLogcatWriter.flush()
            }
            if (line.contains("TestRunner: finished:")) {
                testRunInProgress = false
            }
        }
    }

    /** Stop logcat stream. */
    private fun stopLogcat() {
        try {
            if (this::logcatCommandHandle.isInitialized) {
                logcatCommandHandle.stop()
                logcatCommandHandle.waitFor() // Wait for the command to exit gracefully.
            }
        } catch (t: Throwable) {
            logger.warning("Stopping logcat failed with the following error: $t")
        } finally {
            if (this::tempLogcatWriter.isInitialized) {
                tempLogcatWriter.close()
            }
            if (this::crashLogcatWriter.isInitialized) {
                crashLogcatWriter.close()
            }
        }
    }

    /**
     * Parse test package, class, and method info from logcat
     * Assumes that the line is of the form "**TestRunner: started: method(package.class)"
     */
    private fun parseLine(line: String) {
        val testPackageClassAndMethodNames = line.split("TestRunner: started: ")[1].split("(")
        val testMethod = testPackageClassAndMethodNames[0].trim()
        val testPackageAndClass = testPackageClassAndMethodNames[1].removeSuffix(")")
        val tempFileName = generateLogcatFileName(testPackageAndClass, testMethod)
        tempLogcatFile = File(tempFileName)
        logcatFilePaths.add(tempFileName)
        tempLogcatWriter = tempLogcatFile.outputStream().bufferedWriter()
    }
}
