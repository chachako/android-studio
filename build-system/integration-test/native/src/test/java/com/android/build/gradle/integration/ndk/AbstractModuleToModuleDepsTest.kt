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

package com.android.build.gradle.integration.ndk

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.model.cartesianOf
import com.android.build.gradle.integration.common.fixture.model.enableCxxStructuredLogging
import com.android.build.gradle.integration.common.fixture.model.minimizeUsingTupleCoverage
import com.android.build.gradle.integration.common.fixture.model.recoverExistingCxxAbiModels
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.CMakeVersion
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.json.readJsonFile
import com.android.build.gradle.internal.cxx.model.name
import com.android.build.gradle.internal.cxx.prefab.ModuleMetadataV1
import com.google.common.truth.Truth
import org.junit.Assume
import java.io.File

abstract class AbstractModuleToModuleDepsTest(
    private val appBuildSystem: BuildSystemConfig,
    private val libBuildSystem: BuildSystemConfig,
    appUsesPrefabTag: String,
    libUsesPrefabPublishTag: String,
    private val libExtension: String,
    appStlTag: String,
    libStlTag: String,
    private val outputStructureType: OutputStructureType,
    private val headerType: HeaderType
) {
    private val appUsesPrefab = appUsesPrefabTag == ""
    private val libUsesPrefabPublish = libUsesPrefabPublishTag == ""
    private val appStl = appStlTag.substringAfter(":")
    private val libStl = libStlTag.substringAfter(":")
    private val effectiveAppStl = effectiveStl(appStl, appBuildSystem)
    protected val effectiveLibStl = effectiveStl(libStl, libBuildSystem)
    private val config = "$appBuildSystem:$appStl $libBuildSystem:$libStl:$libExtension"

    abstract fun getTestProject() : GradleTestProject

    sealed class BuildSystemConfig {
        abstract val build : String
        data class CMake(val version : String) : BuildSystemConfig() {
            override val build = "CMake"
            override fun toString() = "cmake$version"
        }

        object NdkBuild : BuildSystemConfig() {
            override val build = "NdkBuild"
            override fun toString() = "ndk-build"
        }
    }
    sealed class OutputStructureType {
        object Normal : OutputStructureType() {
            override fun toString() = ""
        }
        object OutOfTreeBuild : OutputStructureType() {
            override fun toString() = " [out-of-tree]"
        }
    }
    sealed class HeaderType(
        val createHeaderDir : Boolean,
        val createHeaderFile : Boolean
    ) {
        object Normal : HeaderType(true, true) {
            override fun toString() = ""
        }
        object DirectoryButNoFile : HeaderType(true, false) {
            override fun toString() = " [header-dir-only]"
        }
        object None : HeaderType(false, false) {
            override fun toString() = " [no-header]"
        }
    }
    companion object {
        fun data() : Array<Array<Any?>> {
            val tests = cartesianOf(
                arrayOf<BuildSystemConfig>(BuildSystemConfig.NdkBuild) + CMakeVersion.FOR_TESTING.map {
                    BuildSystemConfig.CMake(
                        it.version
                    )
                }.toTypedArray(),
                arrayOf<BuildSystemConfig>(BuildSystemConfig.NdkBuild) + CMakeVersion.FOR_TESTING.map {
                    BuildSystemConfig.CMake(
                        it.version
                    )
                }.toTypedArray(),
                arrayOf("", ":no-prefab"),
                arrayOf("", ":no-prefab-publish"),
                arrayOf(".a", ".so"),
                arrayOf("", ":c++_static", ":c++_shared"),
                arrayOf("", ":c++_static", ":c++_shared"),
                arrayOf(OutputStructureType.Normal, OutputStructureType.OutOfTreeBuild),
                arrayOf(HeaderType.Normal, HeaderType.DirectoryButNoFile, HeaderType.None)
            )
                .minimizeUsingTupleCoverage(4)

            // Because Windows runs much slower, limit the tests that run on Windows to a (stable) sample
            // Because of the way minimizeUsingTupleCoverage works, the tests are sorted in descending
            // order of coverage power (the earlier ones tend to cover more "tuples") the first N
            // tests still have significant coverage.
            val result = if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) tests.take(30).toTypedArray() else tests
            println("Test configuration count: ${result.size}")
            return result
        }
    }

    private val app = MinimalSubProject.app(projectPath="app(1)")
    private val lib = MinimalSubProject.lib(projectPath="lib(1)")
    protected val multiModule = MultiModuleTestProject.builder()
        .subproject(":app", app)
        .subproject(":lib", lib)
        .dependency(app, lib)
        .build()

    private fun effectiveStl(stl: String, buildSystem: BuildSystemConfig): String {
        if (stl != "") {
            return stl
        }

        return when (buildSystem) {
            BuildSystemConfig.NdkBuild -> ""
            else -> "c++_static"
        }
    }

    private fun getModuleOutputRoot(
        project: GradleTestProject,
        outputStructureType: OutputStructureType,
        moduleName:String): File = if (outputStructureType == OutputStructureType.Normal) {
        project.getSubproject(moduleName).buildDir.parentFile
    } else {
        // The output folder for lib is at $PROJECT_ROOT/out/lib
        project.getSubproject("app").buildDir.parentFile.parentFile.resolve("out/$moduleName")
    }

    protected val appOutputRoot : File
        get() = getModuleOutputRoot(
            getTestProject(),
            outputStructureType,
            "app"
        )

    protected val libOutputRoot : File
        get() = getModuleOutputRoot(
            getTestProject(),
            outputStructureType,
            "lib"
        )

    /**
     * Returns true if the project has configured prefab correctly.
     *
     * At the time of writing, the following errors will be emitted when building the app if prefab
     * is not configured correctly, either because the app was not configured to consume prefab
     * packages or because the library was not configured to produce them.
     *
     * ndk-build:
     * Are you sure your NDK_MODULE_PATH variable is properly defined
     *
     * CMake:
     * Add the installation prefix of "lib" to CMAKE_PREFIX_PATH
     */
    protected val prefabConfiguredCorrectly = appUsesPrefab && libUsesPrefabPublish

    /**
     * These configurations produce
     *      [CXX1211] Library is a shared library with a statically linked STL and cannot be used
     *      with any library using the STL
     */
    protected fun expectErrorCXX1211() =
        libExtension == ".so" && effectiveLibStl == "c++_static" && effectiveAppStl.isNotEmpty()

    /**
     * These configurations produce
     *      [CXX1212] User is using a static STL but library requires a shared STL
     */
    protected fun expectErrorCXX1212() =
        libExtension == ".so" && effectiveAppStl == "c++_static" && effectiveLibStl == "c++_shared"

    private fun expectSingleStlViolationError() : Boolean {
        if (expectErrorCXX1211()) return true
        if (expectErrorCXX1212()) return true
        return false
    }

    /**
     * Returns true if this configuration is expected to fail.
     */
    protected fun expectGradleConfigureError() : Boolean {
        if (expectSingleStlViolationError()) return true
        if (!prefabConfiguredCorrectly) return true

        // ndk-build can't consume configuration-only packages until a future NDK
        // update and corresponding prefab update.
        if (appBuildSystem == BuildSystemConfig.NdkBuild) {
            // Error when this test was written:
            //   Android.mk:foo: LOCAL_SRC_FILES points to a missing file
            return true
        }

        return false
    }

    fun setupProject(ndkVersion: String, appAbiFilters: String, libAbiFilters: String) {
        val staticOrShared = if (libExtension == ".a") "STATIC" else "SHARED"
        val appStanza = when(appBuildSystem) {
            is BuildSystemConfig.CMake -> """
                android.externalNativeBuild.cmake.path="CMakeLists.txt"
                android.externalNativeBuild.cmake.version="${appBuildSystem.version}"
                """.trimIndent()
            BuildSystemConfig.NdkBuild -> """
                android.externalNativeBuild.ndkBuild.path="Android.mk"
                """.trimIndent()
            else -> error("$appBuildSystem")
        }
        val appStlStanza = when {
            appStl == "" -> ""
            appBuildSystem is BuildSystemConfig.CMake -> """
                android.defaultConfig.externalNativeBuild.cmake.arguments.add("-DANDROID_STL=$appStl")
                """.trimIndent()
            appBuildSystem is BuildSystemConfig.NdkBuild -> """
                android.defaultConfig.externalNativeBuild.ndkBuild.arguments.add("APP_STL=$appStl")
                """.trimIndent()
            else -> error(appStl)
        }
        val testRoot = getTestProject().getSubproject(":app")
            .buildFile
            .parentFile
            .parentFile
            .absolutePath.replace("\\", "/")
        val appStructureStanza = if (outputStructureType == OutputStructureType.Normal) "" else when(appBuildSystem) {
            is BuildSystemConfig.CMake -> """
                android.externalNativeBuild.cmake.buildStagingDirectory="$testRoot/out/${'$'}{project.path.replace(":", "/")}/nativeConfigure"
                project.buildDir = new File("$testRoot/out/${'$'}{project.path.replace(":", "/")}/build")
                """.trimIndent()
            BuildSystemConfig.NdkBuild -> """
                android.externalNativeBuild.cmake.buildStagingDirectory="$testRoot/out/${'$'}{project.path.replace(":", "/")}/nativeConfigure"
                project.buildDir = new File("$testRoot/out/${'$'}{project.path.replace(":", "/")}/build")
                """.trimIndent()
            else -> error("$appBuildSystem")
        }
        val libStructureStanza = if (outputStructureType== OutputStructureType.Normal) "" else when(libBuildSystem) {
            is BuildSystemConfig.CMake -> """
                android.externalNativeBuild.cmake.buildStagingDirectory="$testRoot/out/${'$'}{project.path.replace(":", "/")}/nativeConfigure"
                project.buildDir = new File("$testRoot/out/${'$'}{project.path.replace(":", "/")}/build")
                """.trimIndent()
            BuildSystemConfig.NdkBuild -> """
                android.externalNativeBuild.cmake.buildStagingDirectory="$testRoot/out/${'$'}{project.path.replace(":", "/")}/nativeConfigure"
                project.buildDir = new File("$testRoot/out/${'$'}{project.path.replace(":", "/")}/build")
                """.trimIndent()
            else -> error("$appBuildSystem")
        }
        val libExportLibrariesStanza =
            "android.externalNativeBuild.experimentalProperties[\"prefab.foo.exportLibraries\"] = [\"-llog\"]"

        getTestProject().getSubproject(":app").buildFile.appendText(
            """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                ndkVersion "$ndkVersion"
                ndkPath "${getTestProject().ndkPath}"
                defaultConfig {
                    minSdk ${GradleTestProject.DEFAULT_MIN_SDK_VERSION}
                    $appAbiFilters
                }

                buildFeatures {
                    prefab $appUsesPrefab
                }
            }

            dependencies {
                implementation project(":lib")
            }

            $appStanza
            $appStlStanza
            $appStructureStanza

            """.trimIndent())

        val libStanza = when(libBuildSystem) {
            is BuildSystemConfig.CMake -> """
                android.externalNativeBuild.cmake.path="CMakeLists.txt"
                android.externalNativeBuild.cmake.version="${libBuildSystem.version}"
                """.trimIndent()
            BuildSystemConfig.NdkBuild -> """
                android.externalNativeBuild.ndkBuild.path="Android.mk"
                """.trimIndent()
            else -> error("$appBuildSystem")
        }
        val libStlStanza = when {
            libStl == "" -> ""
            libBuildSystem is BuildSystemConfig.CMake -> """
                android.defaultConfig.externalNativeBuild.cmake.arguments.add("-DANDROID_STL=$libStl")
                """.trimIndent()
            libBuildSystem == BuildSystemConfig.NdkBuild -> """
                android.defaultConfig.externalNativeBuild.ndkBuild.arguments.add("APP_STL=$libStl")
                """.trimIndent()
            else -> error(libStl)
        }
        val headerStanza = if (headerType.createHeaderDir) "headers \"src/main/cpp/include\"\n" else ""

        getTestProject().getSubproject(":lib").buildFile.appendText(
            """
            android {
                ndkVersion "$ndkVersion"
                ndkPath "${getTestProject().ndkPath}"

                defaultConfig {
                    minSdk ${GradleTestProject.DEFAULT_MIN_SDK_VERSION}
                    $libAbiFilters
                }
                buildFeatures {
                    prefabPublishing $libUsesPrefabPublish
                }
                prefab {
                    foo {
                        $headerStanza
                        libraryName "libfoo"
                    }
                }
            }
            $libStanza
            $libStlStanza
            $libStructureStanza
            $libExportLibrariesStanza
            """)
        val header =
            getTestProject().getSubproject(":lib").buildFile.resolveSibling("src/main/cpp/include/foo.h")
        if (headerType.createHeaderDir) header.parentFile.mkdirs()
        if (headerType.createHeaderFile) header.writeText("int foo();")

        val libSource = getTestProject().getSubproject(":lib").buildFile.resolveSibling("src/main/cpp/foo.cpp")
        libSource.parentFile.mkdirs()
        libSource.writeText("int foo() { return 5; }")

        when(libBuildSystem) {
            is BuildSystemConfig.CMake -> {
                val libCMakeLists =
                    getTestProject().getSubproject(":lib").buildFile.resolveSibling("CMakeLists.txt")
                libCMakeLists.writeText(
                    """
                    cmake_minimum_required(VERSION 3.4.1)
                    project(ProjectName)
                    file(GLOB_RECURSE SRC src/*.c src/*.cpp src/*.cc src/*.cxx src/*.c++ src/*.C)
                    message("${'$'}{SRC}")
                    set(CMAKE_VERBOSE_MAKEFILE ON)
                    add_library(foo $staticOrShared ${'$'}{SRC})
                    """.trimIndent()
                )
            }
            is BuildSystemConfig.NdkBuild -> {
                val libAndroidMk =
                    getTestProject().getSubproject(":lib").buildFile.resolveSibling("Android.mk")
                libAndroidMk.writeText("""
                    LOCAL_PATH := $(call my-dir)

                    include $(CLEAR_VARS)
                    LOCAL_MODULE := foo
                    LOCAL_MODULE_FILENAME := libfoo
                    LOCAL_SRC_FILES := src/main/cpp/foo.cpp
                    LOCAL_C_INCLUDES := src/main/cpp/include
                    LOCAL_EXPORT_C_INCLUDES := src/main/cpp/include
                    include $(BUILD_${staticOrShared}_LIBRARY)
                    """.trimIndent())
            }
        }

        when(appBuildSystem) {
            is BuildSystemConfig.CMake -> {
                val appCMakeLists = getTestProject().getSubproject(":app").buildFile.resolveSibling("CMakeLists.txt")
                appCMakeLists.writeText(
                    """
                    cmake_minimum_required(VERSION 3.4.1)
                    project(ProjectName)
                    file(GLOB_RECURSE SRC src/*.c src/*.cpp src/*.cc src/*.cxx src/*.c++ src/*.C)
                    message("${'$'}{SRC}")
                    set(CMAKE_VERBOSE_MAKEFILE ON)
                    add_library(hello-jni SHARED ${'$'}{SRC})
                    find_package(lib REQUIRED CONFIG)
                    target_link_libraries(hello-jni log lib::foo)
                    """.trimIndent())
            }
            is BuildSystemConfig.NdkBuild -> {
                val appAndroidMk =
                    getTestProject().getSubproject(":app").buildFile.resolveSibling("Android.mk")
                appAndroidMk.writeText("""
                    LOCAL_PATH := $(call my-dir)
                    $(call import-module,prefab/lib)

                    include $(CLEAR_VARS)
                    LOCAL_MODULE := hello-jni
                    LOCAL_MODULE_FILENAME := libhello-jni
                    LOCAL_SRC_FILES := src/main/cpp/call_foo.cpp
                    LOCAL_C_INCLUDES := src/main/cpp/include
                    LOCAL_SHARED_LIBRARIES := libfoo
                    include $(BUILD_SHARED_LIBRARY)
                    """.trimIndent())
            }
        }


        val appSource = getTestProject().getSubproject(":app").buildFile.resolveSibling("src/main/cpp/call_foo.cpp")
        appSource.parentFile.mkdirs()
        if (headerType.createHeaderFile) {
            appSource.writeText(
                """
                #include <foo.h>
                int callFoo() { return foo(); }
                """.trimIndent())
        } else {
            appSource.writeText(
                """
                int foo();
                int callFoo() { return foo(); }
                """.trimIndent())
        }
        enableCxxStructuredLogging(getTestProject())
    }
    protected fun testAppConfigure(abi: String) {
        println(config) // Print identifier for this configuration
        val executor = getTestProject().executor()

        // Expect failure for cases when configuration failure is expected.
        if (expectGradleConfigureError()) {
            executor.expectFailure()
        }

        executor.run(":app:configure${appBuildSystem.build}Debug[$abi]")

        // Check for expected Gradle error message (if any)
        if (expectGradleConfigureError()) {
            return
        }

        // Check that the output is known but does not yet exist on disk.
        val libAbi = recoverExistingCxxAbiModels(libOutputRoot).single { it.name == Abi.X86.tag }
        val libConfig = AndroidBuildGradleJsons.getNativeBuildMiniConfig(libAbi, null)

        val libOutput = libConfig.libraries.values.single().output!!
        Truth.assertThat(libOutput.toString().endsWith(libExtension))
            .named("$libOutput")
            .isTrue()
        Truth.assertThat(libOutput.isFile)
            .named("$libOutput")
            .isFalse()

        // Check that export libraries information winds up in the final prefab structure
        var sawAtleastOneModule = false
        appOutputRoot.walkTopDown().forEach { file ->
            if (file.name == "module.json") {
                val module = readJsonFile<ModuleMetadataV1>(file)
                Truth.assertThat(module.exportLibraries).containsExactly("-llog")
                sawAtleastOneModule = true
            }
        }
        Truth.assertThat(sawAtleastOneModule)
            .named("Expected at least one Prefab module")
            .isTrue()
    }

    protected fun testAppBuild(abi: String) {
        println(config) // Print identifier for this configuration

        // There is no point in testing build in the cases where configuration is expected to fail.
        // Those cases will be covered by other tests
        Assume.assumeFalse(expectGradleConfigureError())

        val executor = getTestProject().executor()

        executor.run(":app:build${appBuildSystem.build}Debug[$abi]")
    }
}
