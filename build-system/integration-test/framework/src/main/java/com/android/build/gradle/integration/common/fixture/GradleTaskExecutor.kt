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
package com.android.build.gradle.integration.common.fixture

import com.android.testutils.TestUtils
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.events.OperationType
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.function.Consumer

/** A Gradle tooling api build builder. */
class GradleTaskExecutor(
    gradleTestInfo: GradleTestInfo,
    gradleOptions: GradleOptions,
    projectConnection: ProjectConnection,
    lastBuildResultConsumer: Consumer<GradleBuildResult>
) :
    BaseGradleExecutor<GradleTaskExecutor>(
        gradleTestInfo,
        projectConnection,
        lastBuildResultConsumer,
        gradleOptions
    ) {

    private var isExpectingFailure = false
    private var env: Map<String, String>? = null

    /**
     * Assert that the task called fails.
     *
     *  The resulting exception is stored in the [GradleBuildResult].
     */
    fun expectFailure(): GradleTaskExecutor {
        isExpectingFailure = true
        return this
    }

    fun withEnvironmentVariables(env: Map<String, String>): GradleTaskExecutor {
        val myEnv = if (this.env == null) {
            // If specifying some env vars, make sure to copy the existing one first.
            HashMap(System.getenv())
        } else {
            HashMap(this.env)
        }
        myEnv.putAll(env)

        this.env = ImmutableMap.copyOf(myEnv)
        return this
    }

    /** Execute the specified tasks  */
    fun run(vararg tasks: String): GradleBuildResult {
        return run(tasks.toList())
    }

    fun run(tasksList: List<String>): GradleBuildResult {
        TestUtils.waitForFileSystemTick()

        val args: MutableList<String> = ArrayList()
        args.addAll(getArguments())

        if (!isExpectingFailure) {
            args.add("--stacktrace")
        }

        val testOutputDir = TestUtils.getTestOutputDir().toFile()
        val tmpStdOut = File.createTempFile("stdout", "log", testOutputDir)
        val tmpStdErr = File.createTempFile("stderr", "log", testOutputDir)

        val launcher =
            projectConnection.newBuild().forTasks(
                *Iterables.toArray(
                    tasksList,
                    String::class.java
                )
            )

        setJvmArguments(launcher)

        val progressListener = CollectingProgressListener()

        launcher.addProgressListener(progressListener, OperationType.TASK)

        launcher.withArguments(*Iterables.toArray(args, String::class.java))

        launcher.setEnvironmentVariables(env)

        var failure: GradleConnectionException? = null
        try {
            BufferedOutputStream(FileOutputStream(tmpStdOut)).use { stdout ->
                BufferedOutputStream(FileOutputStream(tmpStdErr)).use { stderr ->
                    val message =
                        ("""[GradleTestProject ${gradleTestInfo.location.projectDir}] Executing tasks:
gradle ${Joiner.on(' ').join(args)} ${Joiner.on(' ').join(tasksList)}

""")
                    stdout.write(message.toByteArray())

                    setStandardOut(launcher, stdout)
                    setStandardError(launcher, stderr)
                    runBuild<BuildLauncher, Any?>(
                        launcher
                    ) { obj: BuildLauncher, resultHandler: ResultHandler<Any?> ->
                        obj.run(
                            resultHandler
                        )
                    }
                }
            }
        } catch (e: GradleConnectionException) {
            failure = e
        }

        val result =
            GradleBuildResult(tmpStdOut, tmpStdErr, progressListener.getEvents(), failure)
        lastBuildResultConsumer.accept(result)

        if (isExpectingFailure && failure == null) {
            throw AssertionError("Expecting build to fail")
        } else if (!isExpectingFailure && failure != null) {
            maybePrintJvmLogs(failure)
            throw failure
        }
        return result
    }
}
