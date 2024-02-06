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

package com.android.tools.render.compose

import com.android.testutils.TestUtils
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString

/**
 * This is the rule to conveniently set up an android gradle project to be able to run gradle
 * tasks in that project. There is a number of assumption about the project:
 *
 * * Repositories are located only in settings.gradle.kts, google() is present there.
 * * gradle/wrapper/gradle-wrapper.properties has distributionUrl that will be replaced with
 *   [relativeGradleDistributionPath] distribution version.
 * * local.properties is either absent or has no useful settings, it will be replaced with the one
 *   containing sdk path.
 *
 * All the necessary bazel dependencies should be added manually, e.g.
 * * For repositories, include all maven artifacts in the "@maven//:..." format
 * * For distribution, include something like "//tools/base/build-system:gradle-distrib-8.2"
 * * For sdk, include corresponding sdk e.g. "//prebuilts/studio/sdk:platforms/android-34",
 *   "//prebuilts/studio/sdk:build-tools/34.0.0".
 */
class GradleProjectRule(
    private val tmpFolder: TemporaryFolder,
    private val relativeAndroidGradleProjectPath: String,
    private val relativeGradleDistributionPath: String,
) : TestRule {
    private lateinit var projectFolderPath: Path
    private lateinit var gradleUserHomePath: Path

    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                init()
                base?.evaluate()
            }
        }
    }

    /**
     * Executes gradle task against the android gradle project.
     * @param gradleParams gradle task path e.g. :app:assembleDebug
     */
    fun executeGradleTask(vararg gradleParams: String): String {
        // We apply in-process strategy so that we don't search for kotlin compiler daemon that we
        // can't find
        // We apply --offline so that all the app dependencies are fetched from the local maven
        // repository
        val command = listOf("./gradlew", *gradleParams, "-Pkotlin.compiler.execution.strategy=in-process", "--offline")
        val procBuilder = ProcessBuilder(command)
            .directory(projectFolderPath.toFile())
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
        // We have to specify JAVA_HOME for gradlew to run properly
        procBuilder.environment()["JAVA_HOME"] = System.getProperty("java.home")
        // Need to specify custom gradle cache otherwise, it will fail trying to get a lock on the
        // parent folder of ~/.gradle
        procBuilder.environment()["GRADLE_USER_HOME"] = gradleUserHomePath.absolutePathString()
        val proc = procBuilder.start()
        proc.waitFor(5, TimeUnit.MINUTES)
        val error = proc.errorStream.bufferedReader().readText()
        if (error.isNotEmpty()) {
            val commandStr = command.joinToString(" ")
            throw AssertionError("Error while executing gradle command \"$commandStr\":\n$error")
        }
        return proc.inputStream.bufferedReader().readText()
    }

    /** Path to the root folder of the gradle project. */
    val projectRoot: Path
        get() = projectFolderPath

    private fun init() {
        val originalProjectPath = TestUtils.resolveWorkspacePath(relativeAndroidGradleProjectPath)
        val projectFolder = tmpFolder.newFolder("android-project")
        originalProjectPath.toFile().copyRecursively(projectFolder)
        projectFolderPath = projectFolder.toPath()
        // We are going to invoke ./gradlew therefore we need to make it executable after copying
        projectFolderPath.resolve("gradlew").toFile().setExecutable(true)

        injectGradleDistribution()
        injectRepositories()
        injectSdk()

        gradleUserHomePath = tmpFolder.newFolder("gradle-cache").toPath()
    }

    private fun injectGradleDistribution() {
        val gradleDistrib = TestUtils.resolveWorkspacePath(relativeGradleDistributionPath)
        val wrapper = projectFolderPath.resolve("gradle/wrapper/gradle-wrapper.properties")
        var content = Files.readString(wrapper)
        val replacedDistributionUrl =
            gradleDistrib.toUri().toString().replace("file:", "file\\:")
        content = content.replace(
            "distributionUrl=.*".toRegex(),
            "distributionUrl=$replacedDistributionUrl"
        )
        Files.writeString(wrapper, content)
    }

    private fun injectRepositories() {
        val gradleSettings = projectFolderPath.resolve("settings.gradle.kts")
        val rootPath = TestUtils.getWorkspaceRoot()
        val content = Files.readString(gradleSettings).replace(
            "google\\(\\)".toRegex(),
            "maven(url = \"${TestUtils.getLocalMavenRepoFile("")}\")"
        )
        Files.writeString(gradleSettings, content)
    }

    private fun injectSdk() {
        val localProperties = projectFolderPath.resolve("local.properties")
        Files.writeString(localProperties, "sdk.dir=${TestUtils.getSdk()}")
    }
}
