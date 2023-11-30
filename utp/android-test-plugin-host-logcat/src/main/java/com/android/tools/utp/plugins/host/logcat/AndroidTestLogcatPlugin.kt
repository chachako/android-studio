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

import com.android.tools.utp.plugins.common.HostPluginAdapter
import com.android.tools.utp.plugins.host.logcat.proto.AndroidTestLogcatConfigProto.AndroidTestLogcatConfig
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.api.config.environment
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.context.events
import com.google.testing.platform.api.device.CommandHandle
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.plugin.sendIssue
import com.google.testing.platform.api.plugin.sendTestResultUpdate
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.proto.api.core.IssueProto
import com.google.testing.platform.proto.api.core.TestCaseProto.TestCase
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.controller.ext.deviceShell
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

/**
 * This plugin updates [TestSuiteResult] proto with logcat artifacts
 */
class AndroidTestLogcatPlugin(
    private val logger: Logger = getLogger(),
    private val logcatTimeoutSeconds: Long = LOGCAT_TIMEOUT_SECONDS,
        ) : HostPluginAdapter() {

    // Empty companion object is needed to call getLogger() method
    // from the constructor's default parameter. If you remove it,
    // the Kotlin compiler fails with an error.
    companion object {
        private const val TEST_CRASH_INDICATOR = "E AndroidRuntime: "
        private const val LOGCAT_TIMEOUT_SECONDS = 10L
    }

    private lateinit var outputDir: String

    private val logcatTextProcessFinished: CountDownLatch = CountDownLatch(1)
    private val processedLogcatNum: AtomicInteger = AtomicInteger(0)
    private val allTestFinished: AtomicBoolean = AtomicBoolean(false)
    private val expectedTestCaseNum: AtomicInteger = AtomicInteger(0)

    private val currentLogcatLock: Any = Object()
    private var currentLogcatFile: File? = null
    private var currentLogcatWriter: BufferedWriter? = null

    private lateinit var logcatCommandHandle: CommandHandle
    private lateinit var crashLogcatFile: File
    private lateinit var crashLogcatWriter: BufferedWriter
    private lateinit var targetTestProcessName: String
    private lateinit var crashLogcatStartMatcher: Regex
    private lateinit var testPid: String
    private lateinit var context: Context

    private val crashLogFinished = CountDownLatch(1)
    // Heuristic value for reading 5 lines of logcat message before deciding if there's a crash
    private val logcatCounter = CountDownLatch(5)
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
        this.context = context
    }

    override fun beforeAll(deviceController: DeviceController) {
        logger.info("Start logcat streaming.")
        logcatCommandHandle = startLogcatAsync(deviceController)
    }

    override fun beforeEach(
            testCase: TestCase?,
            deviceController: DeviceController
    ) {}

    override fun afterEachWithReturn(
        testResult: TestResult,
        deviceController: DeviceController,
        cancelled: Boolean
    ): TestResult {
        expectedTestCaseNum.incrementAndGet()
        val testCase = testResult.testCase
        val packageName = testCase.testPackage
        val className = testCase.testClass
        val methodName = testCase.testMethod
        return testResult.toBuilder().apply {
            addOutputArtifactBuilder().apply {
                labelBuilder.label = "logcat"
                labelBuilder.namespace = "android"
                sourcePathBuilder.path = generateLogcatFileName("$packageName.$className", methodName)
            }
        }.build().also { context.events.sendTestResultUpdate(it) }
    }

    override fun afterAllWithReturn(
        testSuiteResult: TestSuiteResult,
        deviceController: DeviceController,
        cancelled: Boolean
    ): TestSuiteResult {
        // If we expect more logcat text to process, we wait until timeout.
        if (expectedTestCaseNum.get() > processedLogcatNum.get()) {
            allTestFinished.set(true)
            if(!logcatTextProcessFinished.await(logcatTimeoutSeconds, TimeUnit.SECONDS)) {
                logger.warning(
                        "Failed to retrieve logcat for some test cases. " +
                        "We retrieved logcat for ${processedLogcatNum.get()} test cases " +
                        "out of ${expectedTestCaseNum.get()} tests.")
            }
        }

        // CountDownLatch await with timeout means that the latch will release after timeout.
        // It continues immediately if thread is interrupted or latch reaches 0 before timeout
        // We wait here in case there's a delay in reading logcat message
        logcatCounter.await(logcatTimeoutSeconds, TimeUnit.SECONDS)

        var updatedTestSuiteResult = testSuiteResult
        if(crashHappened.get()) {
            crashLogFinished.await(logcatTimeoutSeconds, TimeUnit.SECONDS)
            val issue = IssueProto.Issue.newBuilder().apply {
                severity = IssueProto.Issue.Severity.SEVERE
                message = "Logcat of last crash: \n" +
                        crashLogcatFile.bufferedReader().use { it.readText() }
            }.build()
            updatedTestSuiteResult = testSuiteResult.toBuilder().apply { addIssue(issue) }.build()
            context.events.sendIssue(issue)
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
            if (crashHappened.get() && line.matches(crashLogcatProgressMatcher)) {
                crashLogcatWriter.write(line.split(TEST_CRASH_INDICATOR).last())
                crashLogcatWriter.newLine()
                crashLogcatWriter.flush()
            }

            // Use regular expression to find end of the logcat for the crash
            // Should be similar to this line:
            // 10-27 15:26:52.864 22058 22058 I Process : Sending signal. PID: 22058 SIG: 9
            if (crashHappened.get() && line.matches(crashLogcatFinishMatcher)) {
                crashLogFinished.countDown()
            }

            if (line.contains("TestRunner: started: ")) {
                testRunInProgress = true
                parseLine(line)
            }
            if (testRunInProgress) {
                synchronized(currentLogcatLock) {
                    currentLogcatWriter?.write(line)
                    currentLogcatWriter?.newLine()
                    currentLogcatWriter?.flush()
                }
            }
            if (line.contains("TestRunner: finished:")) {
                testRunInProgress = false
                closeCurrentLogcatWriter()
            }
        }
    }

    /** Stop logcat stream. */
    private fun stopLogcat() {
        logger.info("Stop logcat streaming.")
        try {
            if (this::logcatCommandHandle.isInitialized) {
                logcatCommandHandle.stop()
                logcatCommandHandle.waitFor() // Wait for the command to exit gracefully.
            }
        } catch (t: Throwable) {
            logger.warning("Stopping logcat failed with the following error: $t")
        } finally {
            closeCurrentLogcatWriter()
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

        synchronized(currentLogcatLock) {
            closeCurrentLogcatWriter()

            val logcatFile = File(tempFileName)
            currentLogcatFile = logcatFile
            currentLogcatWriter = logcatFile.outputStream().bufferedWriter()
        }
    }

    private fun closeCurrentLogcatWriter() {
        synchronized(currentLogcatLock) {
            if (currentLogcatFile != null) {
                currentLogcatWriter?.close()
                currentLogcatWriter = null
                currentLogcatFile = null
                processedLogcatNum.incrementAndGet()
                if (allTestFinished.get() && processedLogcatNum.get() >= expectedTestCaseNum.get()) {
                    logcatTextProcessFinished.countDown()
                }
            }
        }
    }
}
