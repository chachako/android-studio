/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.configurationcache

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.LoggingLevel
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.regex.Pattern
import kotlin.streams.asSequence

class BasicConfigurationCacheTest {

    private val app = MinimalSubProject.app("com.app")
    private val lib = MinimalSubProject.lib("com.lib")

    @JvmField
    @Rule
    var project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .subproject(":lib", lib)
                .subproject(
                    ":test",
                    MinimalSubProject
                        .test("com.test")
                        .appendToBuild("android.targetProjectPath ':app'")
                )
                .build()
        )
        .create()

    @Before
    fun setUp() {
        project.projectDir.resolve(".gradle/configuration-cache").deleteRecursively()
    }

    @Test
    fun testUpToDate() {
        executor().run("assemble")
        assertThat(project.projectDir.resolve(".gradle/configuration-cache")).isDirectory()
        val result = executor().run("assemble")
        // AndroidLintTextOutputTask always run
        Truth.assertThat(result.didWorkTasks).containsExactly(
            ":app:lintVitalRelease",
            ":app:assembleRelease",
            ":app:assemble")
    }

    @Test
    fun testCleanBuild() {
        executor().run("assemble")
        executor().run("clean")
        executor().run("assemble")
    }

    @Test
    fun testWhenInvokedFromTheIde() {
        executor()
            .with(BooleanOption.IDE_INVOKED_FROM_IDE, true)
            .run("assemble")

        assertThat(project.projectDir.resolve(".gradle/configuration-cache")).isDirectory()
        executor().run("clean")
        executor()
            .with(BooleanOption.IDE_INVOKED_FROM_IDE, true)
            .run("assemble")
    }

    /** Regression test for b/146659187. */
    @Test
    fun testWithJniMerging() {
        project.getSubproject("app").file("src/main/jniLibs/subDir/empty.so").also {
            it.parentFile.mkdirs()
            it.createNewFile()
        }
        executor().run(":app:mergeDebugJniLibFolders")
        executor().run("clean")
        executor().run(":app:mergeDebugJniLibFolders")
    }

    @Test
    fun testAndroidTestBuild() {
        executor().run(":app:assembleDebugAndroidTest")
        executor().run("clean")
        executor().run(":app:assembleDebugAndroidTest")
    }

    /** Regression test for b/300617088. */
    @Test
    fun testStableConfigurationCacheFeatureFlag() {
        project.settingsFile.appendText("\nenableFeaturePreview(\"STABLE_CONFIGURATION_CACHE\")")

        val result = executor().expectFailure().run("assemble")

        val violations = result.stdout.findAll(usesServiceWarningRegex).asSequence()
            .map {
                val buildService = it.group(1)
                val task = it.group(2)
                "$buildService is used by $task"
            }.sorted().toList()
        // TODO(b/300617088): We'll need to fix all the issues in this list
        Truth.assertThat(violations).containsExactlyElementsIn(
            listOf(
                "com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService is used by :app:generateReleaseLintVitalReportModel",
                "com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService is used by :app:lintVitalAnalyzeRelease",
                "com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService is used by :app:generateReleaseLintVitalReportModel",
                "com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService is used by :app:lintVitalAnalyzeRelease",
                "com.android.build.gradle.internal.services.Aapt2DaemonBuildService is used by :app:optimizeReleaseResources",
                "com.android.build.gradle.internal.services.Aapt2DaemonBuildService is used by :app:processDebugResources",
                "com.android.build.gradle.internal.services.Aapt2DaemonBuildService is used by :app:processReleaseResources",
                "com.android.build.gradle.internal.services.Aapt2DaemonBuildService is used by :lib:verifyReleaseResources",
                "com.android.build.gradle.internal.services.Aapt2DaemonBuildService is used by :test:processDebugResources",
            )
        )
    }

    /**
     * Regex to capture warnings about Task.usesService (https://docs.gradle.org/current/userguide/configuration_cache.html#config_cache:stable).
     * Example warning:
     *    > Build service 'com.android.build.gradle.internal.services.SymbolTableBuildService_a3ab9d9e-ea4f-497b-a09a-35aa9e17dcdb'
     *    > is being used by task ':app:processDebugResources' without the corresponding declaration via 'Task#usesService'.
     */
    private val usesServiceWarningRegex = Pattern.compile("Build service '([a-zA-Z0-9.]+)_[a-zA-Z0-9\\-]+' is being used by task '([a-zA-Z0-9:]+)'")

    @Test
    fun testWithProjectIsolation() {
        executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.PROJECT_ISOLATION)
            .withArgument("-Dorg.gradle.unsafe.configuration-cache.max-problems=0")
            .run("assemble")
    }

    private fun executor(): GradleTaskExecutor =
        project.executor()
            .withLoggingLevel(LoggingLevel.LIFECYCLE)
            .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
}
