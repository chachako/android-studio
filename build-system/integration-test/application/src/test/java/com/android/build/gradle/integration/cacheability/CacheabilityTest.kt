/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.cacheability

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FROM_CACHE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.utils.CacheabilityTestHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Verifies tasks' states in a cached clean build (i.e., whether they should be cacheable or not
 * cacheable).
 */
class CacheabilityTest {

    /**
     * The expected states of tasks when running a second build with the Gradle build cache
     * enabled from an identical project at a different location.
     */
    private val expectedTaskStates: Map<ExecutionState, Set<String>> = mapOf(
        // Sort alphabetically so it's easier to search
        FROM_CACHE to setOf(
            ":app:bundle${DEBUG_RELEASE}Resources",
            ":app:compile${DEBUG_RELEASE}JavaWithJavac",
            ":app:compile${DEBUG_RELEASE}UnitTestJavaWithJavac",
            ":app:compileReleaseArtProfile",
            ":app:compress${DEBUG_RELEASE}Assets",
            ":app:desugar${DEBUG_RELEASE}FileDependencies",
            ":app:dexBuilder${DEBUG_RELEASE}",
            ":app:extractDeepLinks${DEBUG_RELEASE}",
            ":app:generate${DEBUG_RELEASE}Resources",
            ":app:generate${DEBUG_RELEASE}ResValues",
            ":app:jacocoDebug",
            ":app:javaPreCompile${DEBUG_RELEASE}",
            ":app:javaPreCompile${DEBUG_RELEASE}UnitTest",
            ":app:lintVitalAnalyzeRelease",
            ":app:merge${DEBUG_RELEASE}Assets",
            ":app:merge${DEBUG_RELEASE}JniLibFolders",
            ":app:merge${DEBUG_RELEASE}Resources",
            ":app:merge${DEBUG_RELEASE}Shaders",
            ":app:mergeDex${DEBUG_RELEASE}",
            ":app:mergeExtDex${DEBUG_RELEASE}",
            ":app:optimizeReleaseResources",
            ":app:package${DEBUG_RELEASE}Resources",
            ":app:parse${DEBUG_RELEASE}IntegrityConfig",
            ":app:parse${DEBUG_RELEASE}LocalResources",
            ":app:process${DEBUG_RELEASE}MainManifest",
            ":app:process${DEBUG_RELEASE}Manifest",
            ":app:process${DEBUG_RELEASE}ManifestForPackage",
            ":app:process${DEBUG_RELEASE}Resources",
            ":app:test${DEBUG_RELEASE}UnitTest",
        ),
        DID_WORK to setOf(
            ":app:build${DEBUG_RELEASE}PreBundle",
            ":app:bundle${DEBUG_RELEASE}ClassesToCompileJar",
            ":app:bundle${DEBUG_RELEASE}ClassesToRuntimeJar",
            ":app:check${DEBUG_RELEASE}AarMetadata",
            ":app:check${DEBUG_RELEASE}DuplicateClasses",
            ":app:collectReleaseDependencies",
            ":app:configureReleaseDependencies",
            ":app:create${DEBUG_RELEASE}ApkListingFileRedirect",
            ":app:create${DEBUG_RELEASE}CompatibleScreenManifests",
            ":app:extractProguardFiles",
            ":app:extractReleaseVersionControlInfo",
            ":app:generateDebugJacocoPropertiesFile",
            ":app:generate${DEBUG_RELEASE}UnitTestConfig",
            ":app:generateReleaseLintVitalReportModel",
            ":app:lintVitalRelease",
            ":app:lintVitalReportRelease",
            ":app:map${DEBUG_RELEASE}SourceSetPaths",
            ":app:merge${DEBUG_RELEASE}JavaResource",
            ":app:mergeReleaseArtProfile",
            ":app:processApplicationManifest${DEBUG_RELEASE}ForBundle",
            ":app:package${DEBUG_RELEASE}",
            ":app:package${DEBUG_RELEASE}Bundle",
            ":app:package${DEBUG_RELEASE}UnitTestForUnitTest",
            ":app:process${DEBUG_RELEASE}JavaRes",
            ":app:sdkReleaseDependencyData",
            ":app:validateSigningDebug",
            ":app:write${DEBUG_RELEASE}AppMetadata",
            ":app:write${DEBUG_RELEASE}SigningConfigVersions",
        ),
        UP_TO_DATE to setOf(
            ":app:clean",
            ":app:generate${DEBUG_RELEASE}Assets",
            ":app:preBuild",
            ":app:pre${DEBUG_RELEASE}Build",
            ":app:pre${DEBUG_RELEASE}UnitTestBuild",
        ),
        SKIPPED to setOf(
            ":app:assemble${DEBUG_RELEASE}",
            ":app:compile${DEBUG_RELEASE}Shaders",
            ":app:extractReleaseNativeSymbolTables",
            ":app:merge${DEBUG_RELEASE}NativeDebugMetadata",
            ":app:merge${DEBUG_RELEASE}NativeLibs",
            ":app:process${DEBUG_RELEASE}UnitTestJavaRes",
            ":app:processReleaseJavaRes",
            ":app:strip${DEBUG_RELEASE}DebugSymbols",
        )
    ).fillVariantNames()

    @get:Rule
    val buildCacheDir = TemporaryFolder()

    @get:Rule
    val projectCopy1 = setUpTestProject("projectCopy1")

    @get:Rule
    val projectCopy2 = setUpTestProject("projectCopy2")

    private fun setUpTestProject(projectName: String): GradleTestProject {
        return with(EmptyActivityProjectBuilder()) {
            this.projectName = projectName
            this.withUnitTest = true
            build()
        }
    }

    @Before
    fun setUp() {
        for (project in listOf(projectCopy1, projectCopy2)) {
            // Set up the project such that we can test more tasks
            TestFileUtils.appendToFile(
                project.getSubproject("app").buildFile,
                """
                android {
                    defaultConfig { versionCode = 1 }
                    testOptions { unitTests { includeAndroidResources = true } }
                    buildTypes { debug { testCoverageEnabled = true } }
                }
                """.trimMargin()
            )
        }
    }

    @Test
    fun `check task states`() {
        CacheabilityTestHelper(projectCopy1, projectCopy2, buildCacheDir.root)
            .runTasks(
                "clean", ":app:assembleDebug", ":app:testDebugUnitTest", ":app:packageDebugBundle",
                ":app:assembleRelease", ":app:testReleaseUnitTest", ":app:packageReleaseBundle",
            )
            .assertTaskStatesByGroups(expectedTaskStates, exhaustive = true)
    }

    private fun Map<ExecutionState, Set<String>>.fillVariantNames(): Map<ExecutionState, Set<String>> {
        return mapValues { (_, taskNames) ->
            taskNames.flatMapTo(mutableSetOf()) { taskName ->
                when {
                    taskName.contains(DEBUG_RELEASE) -> setOf(
                        taskName.substringBefore(DEBUG_RELEASE) + "Debug" + taskName.substringAfter(DEBUG_RELEASE),
                        taskName.substringBefore(DEBUG_RELEASE) + "Release" + taskName.substringAfter(DEBUG_RELEASE)
                    )
                    else -> setOf(taskName)
                }
            }
        }
    }
}

private const val DEBUG_RELEASE = "{DEBUG_RELEASE}"
