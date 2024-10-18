/*
 * Copyright (C) 2016 The Android Open Source Project
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
@file:Suppress("UNCHECKED_CAST")

package com.android.build.gradle.integration.common.fixture

import com.android.SdkConstants
import com.android.build.gradle.integration.common.utils.JacocoAgent
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.Option
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.StringOption
import com.android.prefs.AndroidLocation.ANDROID_PREFS_ROOT
import com.android.testutils.TestUtils
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.base.Strings
import com.google.common.base.Throwables
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.google.common.io.ByteStreams
import com.google.common.util.concurrent.SettableFuture
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.ConfigurableLauncher
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import java.io.File
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Consumer
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.streams.asSequence

/**
 * Common flags shared by [ModelBuilderV2] and [GradleTaskExecutor].
 *
 * @param T The concrete implementing class.
 */
abstract class BaseGradleExecutor<T : BaseGradleExecutor<T>> internal constructor(
    @JvmField val gradleTestInfo: GradleTestInfo,
    @JvmField val projectConnection: ProjectConnection,
    @JvmField val lastBuildResultConsumer: Consumer<GradleBuildResult>,
    gradleOptions: GradleOptions,
): GradleOptionBuilder<T> {

    /** Location of the Android Preferences folder (normally in ~/.android)  */
    lateinit var preferencesRootDir: File

    protected val optionPropertyNames: Set<String>
        get() = options.options.asSequence().map { it.propertyName }.toSet()

    private val customArguments: MutableList<String> = ArrayList()
    private val options: ProjectOptionsBuilder = ProjectOptionsBuilder()
    private var loggingLevel: LoggingLevel = LoggingLevel.INFO
    private var offline: Boolean = true
    private var localPrefsRoot: Boolean = false
    private var perTestPrefsRoot: Boolean = false
    private var failOnWarning: Boolean = true
    private var crashOnOutOfMemory: Boolean = false
    private val gradleOptionBuilderDelegate = GradleOptionBuilderDelegate(gradleOptions)


    init {
        gradleTestInfo.profileDirectory?.let {
            with(StringOption.PROFILE_OUTPUT_DIR, it.toString())
        }
    }

    fun with(option: BooleanOption, value: Boolean): T {
        options.booleans[option] = value
        return this as T
    }

    fun with(option: OptionalBooleanOption, value: Boolean): T {
        options.optionalBooleans[option] = value
        return this as T
    }

    fun with(option: IntegerOption, value: Int): T {
        options.integers[option] = value
        return this as T
    }

    fun with(option: StringOption, value: String): T {
        options.strings[option] = value
        return this as T
    }

    fun suppressOptionWarning(option: Option<*>): T {
        options.suppressWarnings.add(option)
        return this as T
    }

    @Deprecated("")
    fun withProperty(propertyName: String, value: String): T {
        withArgument("-P$propertyName=$value")
        return this as T
    }

    /** Add additional build arguments.  */
    fun withArguments(arguments: List<String>): T {
        for (argument: String in arguments) {
            withArgument(argument)
        }
        return this as T
    }

    /** Add a build argument.  */
    fun withArgument(argument: String): T {
        require(
            !(argument.startsWith("-Pandroid")
                    && !argument.contains("testInstrumentationRunnerArguments"))
        ) { "Use with(Option, Value) instead." }
        customArguments.add(argument)
        return this as T
    }

    fun withEnableInfoLogging(enableInfoLogging: Boolean): T {
        return withLoggingLevel(if (enableInfoLogging) LoggingLevel.INFO else LoggingLevel.LIFECYCLE)
    }

    fun withLoggingLevel(loggingLevel: LoggingLevel): T {
        this.loggingLevel = loggingLevel
        return this as T
    }

    /** Sets to run Gradle with the normal preference root (~/.android)  */
    fun withLocalPrefsRoot(): T {
        localPrefsRoot = true
        return this as T
    }

    /**
     * Sets whether to run Gradle with a per-test preference root. (The preference root outside of
     * test is normally ~/.android.)
     *   - If set to false, the folder is located in the build output, common to all tests.
     *   - If set to true, the test will use its own isolated folder.
     */
    fun withPerTestPrefsRoot(perTestPrefsRoot: Boolean): T {
        this.perTestPrefsRoot = perTestPrefsRoot
        return this as T
    }

    fun withoutOfflineFlag(): T {
        this.offline = false
        return this as T
    }

    fun withSdkAutoDownload(): T {
        return with(BooleanOption.ENABLE_SDK_DOWNLOAD, true)
    }

    fun withFailOnWarning(failOnWarning: Boolean): T {
        this.failOnWarning = failOnWarning
        return this as T
    }

    /** Forces JVM exit in the event of an OutOfMemoryError, without collecting a heap dump.  */
    fun crashOnOutOfMemory(): T {
        this.crashOnOutOfMemory = true
        return this as T
    }

    /**
     * API option 1
     * This allows writing
     * ```
     * executor().configureOptions {
     *    withHeap("12G").withMetaspace("2G")
     * }.run("...")
     */
    fun configureOptions(action: GradleOptionBuilder<*>.() -> Unit): T {
        action(gradleOptionBuilderDelegate)
        return this as T
    }

    /**
     * API option 2
     * This allows writing
     * ```
     * executor().withHeap("12G").withMetaspace("2G").run("...")
     *
     * but has significantly more code duplication in the fixtures.
     */
    override fun withHeap(heapSize: String?): T {
        gradleOptionBuilderDelegate.withHeap(heapSize)
        return this as T
    }

    // API option 2
    override fun withMetaspace(metaspaceSize: String?): T {
        gradleOptionBuilderDelegate.withMetaspace(metaspaceSize)
        return this as T
    }

    // API option 2
    override fun withConfigurationCaching(configurationCaching: ConfigurationCaching): T {
        gradleOptionBuilderDelegate.withConfigurationCaching(configurationCaching)
        return this as T
    }

    protected fun getArguments(): List<String> {
        val arguments: MutableList<String> = ArrayList()
        arguments.addAll(customArguments)
        arguments.addAll(options.arguments)

        if (loggingLevel.argument != null) {
            arguments.add(loggingLevel.argument!!)
        }

        arguments.add("-Dfile.encoding=" + Charset.defaultCharset().displayName())
        arguments.add("-Dsun.jnu.encoding=" + System.getProperty("sun.jnu.encoding"))

        if (offline) {
            arguments.add("--offline")
        }
        if (failOnWarning) {
            arguments.add("--warning-mode=fail")
        }

        when (gradleOptionBuilderDelegate.asGradleOptions.configurationCaching) {
            ConfigurationCaching.ON -> {
                arguments.add("--configuration-cache")
                arguments.add("--configuration-cache-problems=fail")
            }

            ConfigurationCaching.PROJECT_ISOLATION -> {
                arguments.add("--configuration-cache")
                arguments.add("-Dorg.gradle.unsafe.isolated-projects=true")
                arguments.add("--configuration-cache-problems=fail")
            }

            ConfigurationCaching.PROJECT_ISOLATION_WARN -> {
                arguments.add("--configuration-cache")
                arguments.add("-Dorg.gradle.unsafe.isolated-projects=true")
                arguments.add("--configuration-cache-problems=warn")
            }

            ConfigurationCaching.OFF -> arguments.add("--no-configuration-cache")
        }

        if (!localPrefsRoot) {
            val preferencesRootDir = if (perTestPrefsRoot) {
                File(gradleTestInfo.location.projectDir.parentFile, "android_prefs_root")
            } else {
                File(gradleTestInfo.location.testLocation.buildDir, "android_prefs_root")
            }

            FileUtils.mkdirs(preferencesRootDir)

            this.preferencesRootDir = preferencesRootDir

            arguments.add("-D${ANDROID_PREFS_ROOT}=${preferencesRootDir.absolutePath}")
        }

        return arguments
    }

    /*
     * A good-enough heuristic to check if the Kotlin plugin is applied.
     * This is needed because of b/169842093.
     */
    private fun ifAppliesKotlinPlugin(testProject: GradleTestProject): Boolean {
        val rootProject: GradleTestProject = testProject.rootProject

        for (buildFile: File in FileUtils.find(rootProject.projectDir, Pattern.compile("build\\.gradle"))) {
            if (buildFile.readLines(Charsets.UTF_8).stream()
                    .anyMatch { s: String ->
                        s.contains("apply plugin: 'kotlin'")
                                || s.contains("apply plugin: 'kotlin-android'")
                    }
            ) {
                return true
            }
        }

        return false
    }

    protected fun setJvmArguments(launcher: LongRunningOperation) {
        val jvmArguments: MutableList<String> = ArrayList(
            gradleOptionBuilderDelegate.asGradleOptions.memoryRequirement.asJvmArgs
        )

        if (crashOnOutOfMemory) {
            jvmArguments.add("-XX:+CrashOnOutOfMemoryError")
        } else {
            jvmArguments.add("-XX:+HeapDumpOnOutOfMemoryError")
            jvmArguments.add("-XX:HeapDumpPath=" + jvmLogDir.resolve("heapdump.hprof"))
        }

        val debugIntegrationTest: String? = System.getenv("DEBUG_INNER_TEST")
        if (!Strings.isNullOrEmpty(debugIntegrationTest)) {
            val serverArg: String = if (debugIntegrationTest.equals("socket-listen", ignoreCase = true)) "n" else "y"
            jvmArguments.add("-agentlib:jdwp=transport=dt_socket,server=$serverArg,suspend=y,address=5006")
        }

        if (JacocoAgent.isJacocoEnabled()) {
            jvmArguments.add(JacocoAgent.getJvmArg(gradleTestInfo.location.testLocation.buildDir))
        }

        jvmArguments.add("-XX:ErrorFile=$jvmErrorLog")
        if (CAPTURE_JVM_LOGS) {
            jvmArguments.add("-XX:+UnlockDiagnosticVMOptions")
            jvmArguments.add("-XX:+LogVMOutput")
            jvmArguments.add("-XX:LogFile=" + jvmLogDir.resolve("java_log.log").toString())
        }

        launcher.setJvmArguments(*Iterables.toArray(jvmArguments, String::class.java))
    }

    private fun printJvmLogs() {
        val files: List<Path> = jvmLogDir.toFile().walk().filter { it.isFile }.map { it.toPath() }.toList()
        if (files.isEmpty()) {
            return
        }

        val projectDirectory: Path = gradleTestInfo.location.projectDir.toPath()
        val outputs: Path = if (TestUtils.runningFromBazel()) {
            // Put in test undeclared output directory.
            TestUtils.getTestOutputDir()
                .resolve(projectDirectory.parent.parent.fileName)
                .resolve(projectDirectory.parent.fileName)
                .resolve(projectDirectory.fileName)
        } else {
            projectDirectory.resolve("jvm_logs_outputs")
        }
        Files.createDirectories(outputs)

        System.err.println("----------- JVM Log start -----------")
        System.err.println("----- JVM log files being put in $outputs ----")
        for (path: Path in files) {
            System.err.print("---- Copying Log file: ")
            System.err.println(path.fileName)
            Files.move(path, outputs.resolve(path.fileName))
        }
        System.err.println("------------ JVM Log end ------------")
    }

    protected fun maybePrintJvmLogs(failure: GradleConnectionException) {
        val stacktrace: String = Throwables.getStackTraceAsString(failure)
        if (stacktrace.contains("org.gradle.launcher.daemon.client.DaemonDisappearedException")
            || stacktrace.contains("java.lang.OutOfMemoryError")
        ) {
            printJvmLogs()
        }
    }

    protected class CollectingProgressListener : ProgressListener {
        private val events: ConcurrentLinkedQueue<ProgressEvent> = ConcurrentLinkedQueue()

        override fun statusChanged(progressEvent: ProgressEvent) {
            events.add(progressEvent)
        }

        fun getEvents(): List<ProgressEvent> {
            return ImmutableList.copyOf(events)
        }
    }

    fun interface RunAction<LauncherT, ResultT> {
        fun run(launcher: LauncherT, resultHandler: ResultHandler<ResultT>)
    }

    enum class ConfigurationCaching {
        ON,
        PROJECT_ISOLATION_WARN,
        PROJECT_ISOLATION,

        /**
         * Disables configuration cache (i.e., pass `--no-configuration-cache` to the build's
         * arguments).
         *
         * Note: Using this option is not recommended. Only use it if absolutely required.
         */
        @Deprecated("")
        OFF
    }

    companion object {

        // An internal timeout for executing Gradle. This aims to be less than the overall test timeout
        // to give more instructive error messages
        private val TIMEOUT_SECONDS: Long

        private val jvmLogDir: Path = Files.createTempDirectory("GRADLE_JVM_LOGS")

        private val jvmErrorLog: Path = jvmLogDir.resolve("java_error.log")

        private val VERBOSE: Boolean = !Strings.isNullOrEmpty(System.getenv()["CUSTOM_TEST_VERBOSE"])

        const val CAPTURE_JVM_LOGS: Boolean = false

        init {
            val timeoutOverride: String? = System.getenv("TEST_TIMEOUT")
            TIMEOUT_SECONDS = if (timeoutOverride != null) {
                // Allow for longer build times within a test, while still trying to avoid having the
                // overal test timeout be hit. If TEST_TIMEOUT is set, potentially increase the timeout
                // to 1 minute less than the overall test timeout, if that's more than the default 10
                // minute timeout.
                max(600.0, (timeoutOverride.toInt() - 60).toDouble()).toLong()
            } else {
                600
            }
        }

        @JvmStatic
        protected fun setStandardOut(launcher: LongRunningOperation, stdout: OutputStream) {
            if (VERBOSE) {
                launcher.setStandardOutput(TeeOutputStream(stdout, System.out))
            } else {
                launcher.setStandardOutput(stdout)
            }
        }

        @JvmStatic
        protected fun setStandardError(launcher: LongRunningOperation, stderr: OutputStream) {
            if (VERBOSE) {
                launcher.setStandardError(TeeOutputStream(stderr, System.err))
            } else {
                launcher.setStandardError(stderr)
            }
        }

        @JvmStatic
        protected fun <LauncherT : ConfigurableLauncher<LauncherT>, ResultT> runBuild(
            launcher: LauncherT, runAction: RunAction<LauncherT, ResultT>
        ): ResultT {
            val cancellationTokenSource: CancellationTokenSource =
                GradleConnector.newCancellationTokenSource()
            launcher.withCancellationToken(cancellationTokenSource.token())
            val future: SettableFuture<ResultT> = SettableFuture.create()
            runAction.run(
                launcher,
                object : ResultHandler<ResultT> {
                    override fun onComplete(result: ResultT) {
                        future.set(result)
                    }

                    override fun onFailure(e: GradleConnectionException) {
                        future.setException(e)
                    }
                })
            try {
                return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: ExecutionException) {
                throw (e.cause as GradleConnectionException?)!!
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RuntimeException(e)
            } catch (e: TimeoutException) {
                try {
                    printThreadDumps()
                } catch (t: Throwable) {
                    e.addSuppressed(t)
                }
                cancellationTokenSource.cancel()
                // TODO(b/78568459) Gather more debugging info from Gradle daemon.
                throw RuntimeException(e)
            }
        }

        private fun printThreadDumps() {
            if (SdkConstants.currentPlatform() != SdkConstants.PLATFORM_LINUX
                && SdkConstants.currentPlatform() != SdkConstants.PLATFORM_DARWIN
            ) {
                // handle only Linux&Darwin for now
                return
            }
            val javaHome: String = System.getProperty("java.home")
            val processes: String = runProcess("$javaHome/bin/jps")

            val lines: Array<String> =
                processes.split(System.lineSeparator().toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (line: String in lines) {
                val pid: String = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                val threadDump: String = runProcess("$javaHome/bin/jstack", "-l", pid)

                println("Fetching thread dump for: $line")
                println("Thread dump is:")
                println(threadDump)
            }
        }

        private fun runProcess(vararg commands: String): String {
            val processBuilder: ProcessBuilder = ProcessBuilder().command(*commands)
            val process: Process = processBuilder.start()
            process.waitFor(5, TimeUnit.SECONDS)

            val bytes: ByteArray = ByteStreams.toByteArray(process.inputStream)
            return String(bytes, Charsets.UTF_8)
        }
    }
}
