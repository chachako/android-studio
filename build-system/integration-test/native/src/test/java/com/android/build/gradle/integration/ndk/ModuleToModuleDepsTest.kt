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

package com.android.build.gradle.integration.ndk

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
import com.android.build.gradle.integration.common.fixture.model.deleteExistingStructuredLogs
import com.android.build.gradle.integration.common.fixture.model.readStructuredLogs
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.build.gradle.internal.cxx.configure.decodeConfigureInvalidationState
import com.android.build.gradle.internal.cxx.configure.shouldConfigure
import com.android.build.gradle.internal.cxx.logging.LoggingMessage
import com.android.build.gradle.internal.cxx.logging.decodeLoggingMessage
import com.android.build.gradle.internal.cxx.logging.text
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * CMake lib<-app project where lib is published as Prefab
 */
@RunWith(Parameterized::class)
class ModuleToModuleDepsTest(
    private val appBuildSystem: BuildSystemConfig,
    private val libBuildSystem: BuildSystemConfig,
    appUsesPrefabTag: String,
    libUsesPrefabPublishTag: String,
    private val libExtension: String,
    appStlTag: String,
    libStlTag: String,
    outputStructureType: OutputStructureType,
    headerType: HeaderType
) : AbstractModuleToModuleDepsTest(appBuildSystem, libBuildSystem, appUsesPrefabTag,
    libUsesPrefabPublishTag, libExtension, appStlTag, libStlTag, outputStructureType, headerType) {
    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(multiModule)
            .setSideBySideNdkVersion(GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
            .create()

    companion object {

        @Parameterized.Parameters(
            name = "app.so={0}{2}{5} lib{4}={1}{3}{6}{7}{8}"
        )
        @JvmStatic
        fun data(): Array<Array<Any?>> = AbstractModuleToModuleDepsTest.data()
    }

    @Before
    fun setUp() {
        setupProject(
            DEFAULT_NDK_SIDE_BY_SIDE_VERSION,
            """
            ndk {
                abiFilters "arm64-v8a"
            }
            """.trimIndent(),
            "")
    }


    /**
     * Returns true if this configuration is expected to fail at build time.
     */
    private fun expectGradleBuildError() : Boolean {
        // If ndk-build produces no library then it will eventually become a build error
        return expectNdkBuildProducesNoLibrary()
    }

    // https://developer.android.com/ndk/guides/cpp-support#one_stl_per_app
    private fun expectSingleStlViolationError() : Boolean {
        if (expectErrorCXX1211()) return true
        if (expectErrorCXX1212()) return true
        return false
    }

    /**
     * When ndk-build is configure to produce .a but has shared STL it will silently produce
     * no .a file.
     */
    private fun expectNdkBuildProducesNoLibrary() =
        libBuildSystem == BuildSystemConfig.NdkBuild && effectiveLibStl == "c++_shared" && libExtension == ".a"

    @Test
    fun `app configure`() {
        testAppConfigure("arm64-v8a")
    }

    @Test
    fun `app second configure should be nop`() {
        Assume.assumeFalse(expectGradleConfigureError())
        val executor = getTestProject().executor()
        executor.run(":app:configure${appBuildSystem.build}Debug[arm64-v8a]")
        deleteExistingStructuredLogs(getTestProject())
        executor.run(":app:configure${appBuildSystem.build}Debug[arm64-v8a]")
        val secondConfigure = getTestProject().readStructuredLogs(::decodeConfigureInvalidationState)
            .filter { it.inputFilesList.any { it.contains("prefab_publication.json" ) } }
        assertThat(secondConfigure).hasSize(1)
        assertThat(secondConfigure[0].shouldConfigure).isFalse()
    }

    @Test
    fun `app build`() {
        testAppBuild("arm64-v8a")
    }

    // Calculating task graph as configuration cache cannot be reused because the file system entry
    // 'out/lib/build/intermediates/prefab_package_header_only/prefab_publication.json' has been
    // created.
    @Test
    fun `check configuration caching`() {
        Assume.assumeFalse(expectGradleConfigureError())
        Assume.assumeFalse(expectGradleBuildError())
        getTestProject().execute("assembleRelease")
        getTestProject().execute("assembleRelease")
        getTestProject().buildResult.assertConfigurationCacheHit()
    }

    @Test
    fun `check single STL violation CXX1211`() {
        Assume.assumeTrue(prefabConfiguredCorrectly)
        Assume.assumeTrue(expectErrorCXX1211()) // Only run the CXX1211 cases
        val executor = getTestProject().executor()
        executor.expectFailure()
        executor.run(":app:configure${appBuildSystem.build}Debug[arm64-v8a]")
        val errors = getTestProject().readStructuredLogs(::decodeLoggingMessage)
            .filter { it.level == LoggingMessage.LoggingLevel.ERROR}
        val error = errors.map { it.diagnosticCode }.single()
        assertThat(error)
            .named(errors.map { it.text() }.single())
            .isEqualTo(1211)
    }

    @Test
    fun `check single STL violation CXX1212`() {
        Assume.assumeTrue(prefabConfiguredCorrectly)
        Assume.assumeTrue(expectErrorCXX1212()) // Only run the CXX1212 cases
        val executor = getTestProject().executor()
        executor.expectFailure()
        executor.run(":app:configure${appBuildSystem.build}Debug[arm64-v8a]")
        val errors = getTestProject().readStructuredLogs(::decodeLoggingMessage)
            .filter { it.level == LoggingMessage.LoggingLevel.ERROR }
        val error = errors.map { it.diagnosticCode }.single()
        assertThat(error)
            .named(errors.map { it.text() }.single())
            .isEqualTo(1212)
    }

    @Test
    fun `test sync`() {
        Assume.assumeFalse(expectGradleConfigureError())
        // Simulate an IDE sync
        getTestProject().modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING) // CMake cannot detect compiler attributes
            .fetchNativeModules(ModelBuilderV2.NativeModuleParams())
    }

    @Test
    fun `changing a cpp file causes a rebuild`() {
        Assume.assumeFalse(expectGradleConfigureError())
        Assume.assumeFalse(expectGradleBuildError())

        val executor = getTestProject().executor()

        val buildTask = ":app:build${appBuildSystem.build}Debug[arm64-v8a]"

        executor.run(buildTask)
        executor.run(buildTask)
        assertThat(getTestProject().buildResult.getTask(":lib:prefabDebugPackage")).wasUpToDate()

        val cppSrc = getTestProject().getSubproject(":lib").buildFile.resolveSibling("src/main/cpp/foo.cpp")
        cppSrc.writeText("int foo() { return 6; }")
        executor.run(buildTask)
/* b/259542368
        assertThat(project.buildResult.getTask(":lib:prefabDebugPackage")).didWork()
b/259542368 */
    }

    override fun getTestProject(): GradleTestProject = project
}
