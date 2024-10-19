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

package com.android.build.gradle.internal.cxx.model

import com.android.SdkConstants.NDK_DEFAULT_VERSION
import com.android.build.api.dsl.Prefab
import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.features.NativeBuildCreationConfig
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.core.MergedNdkConfig
import com.android.build.gradle.internal.cxx.configure.CMakeVersion
import com.android.build.gradle.internal.cxx.configure.CmakeLocator
import com.android.build.gradle.internal.cxx.configure.NativeLocationsBuildService
import com.android.build.gradle.internal.cxx.configure.NinjaLocator
import com.android.build.gradle.internal.cxx.configure.defaultCmakeVersion
import com.android.build.gradle.internal.cxx.gradle.generator.tryCreateConfigurationParameters
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.AbiSplitOptions
import com.android.build.gradle.internal.dsl.CmakeOptions
import com.android.build.gradle.internal.dsl.ExternalNativeBuild
import com.android.build.gradle.internal.dsl.ExternalNativeBuildOptions
import com.android.build.gradle.internal.dsl.NdkBuildOptions
import com.android.build.gradle.internal.dsl.Splits
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeProjectLayout
import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.build.gradle.internal.ndk.NdkInstallStatus
import com.android.build.gradle.internal.ndk.NdkPlatform
import com.android.build.gradle.internal.ndk.NdkR25Info
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.IssueReporter
import com.android.prefs.AndroidLocationsProvider
import com.android.repository.Revision
import com.android.utils.FileUtils.join
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileContents
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale

/**
 * Set up up a mock for constructing [CxxModuleModel]. It takes a lot of plumbing so this can
 * be reused between tests that need this.
 */
open class BasicModuleModelMock {
    private val abisJson = """
    {
     "armeabi-v7a": {
        "bitness": 32,
        "default": true,
        "deprecated": false,
        "proc": "armv7-a",
        "arch": "arm",
        "triple": "arm-linux-androideabi",
        "llvm_triple": "armv7-none-linux-androideabi"
      },
      "arm64-v8a": {
        "bitness": 64,
        "default": true,
        "deprecated": false,
        "proc": "aarch64",
        "arch": "arm64",
        "triple": "aarch64-linux-android",
        "llvm_triple": "aarch64-none-linux-android"
      },
      "riscv64": {
        "bitness": 64,
        "default": true,
        "deprecated": false,
        "proc": "riscv64",
        "arch": "riscv64",
        "triple": "riscv64-linux-android",
        "llvm_triple": "riscv64-none-linux-android"
      },
      "x86": {
        "bitness": 32,
        "default": true,
        "deprecated": false,
        "proc": "i686",
        "arch": "x86",
        "triple": "i686-linux-android",
        "llvm_triple": "i686-none-linux-android"
      },
      "x86_64": {
        "bitness": 64,
        "default": true,
        "deprecated": false,
        "proc": "x86_64",
        "arch": "x86_64",
        "triple": "x86_64-linux-android",
        "llvm_triple": "x86_64-none-linux-android"
      }
    }
    """.trimIndent()

    private val platformsJson = """
    {
      "min": 16,
      "max": 29,
      "aliases": {
        "20": 19,
        "25": 24,
        "J": 16,
        "J-MR1": 17,
        "J-MR2": 18,
        "K": 19,
        "L": 21,
        "L-MR1": 22,
        "M": 23,
        "N": 24,
        "N-MR1": 24,
        "O": 26,
        "O-MR1": 27,
        "P": 28,
        "Q": 29
      }
    }
    """.trimIndent()
    val tempFolder = createTempDir()
    val home = join(tempFolder, "home")
    val projects = join(tempFolder, "projects")
    val throwUnmocked = RuntimeExceptionAnswer()
    private val globalConfig: GlobalTaskCreationConfig = mock(defaultAnswer = throwUnmocked)
    val projectInfo: ProjectInfo = mock(defaultAnswer = throwUnmocked)

    val variantExperimentalPropertiesMapProperty: MapProperty<*, *> = mock(defaultAnswer = throwUnmocked)

    val variantImpl: VariantImpl<*> = mock(defaultAnswer = throwUnmocked)

    val taskCreationServices: TaskCreationServices = mock(defaultAnswer = throwUnmocked)

    val issueReporter: IssueReporter = mock()

    val externalNativeBuild: ExternalNativeBuild = mock(defaultAnswer = throwUnmocked)
    val cmake: CmakeOptions = mock(defaultAnswer = throwUnmocked)
    val ndkBuild: NdkBuildOptions = mock(defaultAnswer = throwUnmocked)
    val ndkInstallStatus = NdkInstallStatus.Valid(
        mock<NdkPlatform>(defaultAnswer = throwUnmocked))
    val coreExternalNativeBuildOptions = mock<ExternalNativeBuildOptions>(defaultAnswer = throwUnmocked)
    val variantExternalNativeBuild = mock<com.android.build.api.variant.ExternalNativeBuild>()

    val mergedNdkConfig = mock<MergedNdkConfig>(defaultAnswer = throwUnmocked)

    val androidLocationProvider = mock<AndroidLocationsProvider>()

    val nativeLocationsBuildService = mock<NativeLocationsBuildService>(defaultAnswer = throwUnmocked)

    val sdkComponents = mock<SdkComponentsBuildService>(defaultAnswer = throwUnmocked)

    val versionExecutor : (File) -> String = { exe ->
        val processBuilder = ProcessBuilder(exe.absolutePath, "--version")
        processBuilder.redirectErrorStream()
        val process = processBuilder.start()
        var bufferedReader: BufferedReader? = null
        var inputStreamReader: InputStreamReader? = null
        try {
            inputStreamReader = InputStreamReader(process.inputStream)
            try {
                bufferedReader = BufferedReader(inputStreamReader)
                bufferedReader.readLine()
            } finally {
                bufferedReader?.close()
            }
        } finally {
            inputStreamReader?.close()
        }
    }
    val projectOptions = mock<ProjectOptions>(defaultAnswer = throwUnmocked)
    private val project = mock<Project>(defaultAnswer = throwUnmocked)

    val allPlatformsProjectRootDir = join(projects, "MyProject")
    val projectRootDir = join(allPlatformsProjectRootDir,  "Source", "Android")
    val sdkDir = join(home, "Library", "Android", "sdk")
    val cmakeDir = join(sdkDir, "cmake", CMakeVersion.DEFAULT.version, "bin")
    val ndkHandler = mock<SdkComponentsBuildService.VersionedNdkHandler>(defaultAnswer = throwUnmocked)

    val minSdkVersion = AndroidVersionImpl(19)
    val cmakeFinder = mock<CmakeLocator>(defaultAnswer = throwUnmocked)
    val ninjaFinder = mock<NinjaLocator>(defaultAnswer = throwUnmocked)

    val buildFeatures = mock<BuildFeatureValues>(defaultAnswer = throwUnmocked)

    val gradle = mock<Gradle>()

    val providers = FakeProviderFactory(FakeProviderFactory.factory, emptyMap())

    val layout = FakeProjectLayout()

    lateinit var fileContents : FileContents

    val configurationParameters by lazy {
        tryCreateConfigurationParameters(
            projectOptions,
            variantImpl,
        )!!
    }

    private val variantExperimentalProperties : MutableMap<String, Any> = mutableMapOf()

    fun mockModule(appName : String) : File {
        val appFolder = join(projectRootDir, appName)

        val appFolderDirectory = mock<Directory>(defaultAnswer = throwUnmocked)
        doReturn(appFolder).whenever(appFolderDirectory).asFile

        val buildDir = File(appFolder, "build")
        val buildDirProperty = mock<DirectoryProperty>(defaultAnswer = throwUnmocked)
        val buildDirProvider = mock<Provider<File>>(defaultAnswer = throwUnmocked)
        doReturn(buildDirProperty).whenever(projectInfo).buildDirectory
        doReturn(buildDirProvider).whenever(buildDirProperty).asFile
        doReturn(buildDir).whenever(buildDirProvider).get()


        val intermediates = File(buildDir, "intermediates")
        val intermediatesDir = mock<Directory>(defaultAnswer = throwUnmocked)
        val intermediatesProvider = mock<Provider<Directory>>(defaultAnswer = throwUnmocked)
        doReturn(intermediatesProvider).whenever(projectInfo).intermediatesDirectory
        doReturn(intermediatesDir).whenever(intermediatesProvider).get()
        doReturn(intermediates).whenever(intermediatesDir).asFile

        val abiSplitOptions = mock<AbiSplitOptions>(defaultAnswer = throwUnmocked)
        val splits = mock<Splits>(defaultAnswer = throwUnmocked)

        val prefabArtifactCollection = mock<ArtifactCollection>(defaultAnswer = throwUnmocked)
        val prefabFileCollection = mock<FileCollection>(defaultAnswer = throwUnmocked)


        doReturn(join(buildDir, "build.gradle")).whenever(projectInfo).buildFile
        doReturn(projectRootDir).whenever(projectInfo).rootDir
        doReturn(appName).whenever(projectInfo).path

        doReturn(appFolderDirectory).whenever(projectInfo).projectDirectory

        doReturn(externalNativeBuild).whenever(globalConfig).externalNativeBuild
        doReturn("12.3.4").whenever(globalConfig).compileSdkHashString
        doReturn("29.3.4").whenever(globalConfig).ndkVersion
        doReturn("/path/to/nowhere").whenever(globalConfig).ndkPath

        doReturn(splits).whenever(globalConfig).splits

        doReturn(globalConfig).whenever(this.variantImpl).global
        doReturn(variantExperimentalPropertiesMapProperty).whenever(this.variantImpl).experimentalProperties
        doReturn(variantExperimentalProperties).whenever(this.variantExperimentalPropertiesMapProperty).get()
        doReturn(taskCreationServices).whenever(this.variantImpl).services
        doReturn(issueReporter).whenever(this.taskCreationServices).issueReporter
        doReturn(projectInfo).whenever(this.taskCreationServices).projectInfo

        val variantDependencies = mock<VariantDependencies>()
        doReturn(prefabArtifactCollection).whenever(variantDependencies).getArtifactCollection(
            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
            AndroidArtifacts.ArtifactScope.ALL,
            AndroidArtifacts.ArtifactType.PREFAB_PACKAGE
        )
        doReturn(variantDependencies).whenever(this.variantImpl).variantDependencies
        doReturn(minSdkVersion).whenever(this.variantImpl).minSdkVersion
        doReturn(minSdkVersion).whenever(this.variantImpl).minSdk
        doReturn(prefabFileCollection).whenever(prefabArtifactCollection).artifactFiles
        doReturn(emptyList<File>().iterator()).whenever(prefabFileCollection).iterator()

        doReturn(variantExternalNativeBuild).whenever(this.variantImpl).externalNativeBuild

        val nativeBuildCreationConfig = mock<NativeBuildCreationConfig>()

        doReturn(mergedNdkConfig).whenever(nativeBuildCreationConfig).ndkConfig
        doReturn(variantExperimentalProperties).whenever(nativeBuildCreationConfig).externalNativeExperimentalProperties
        doReturn(nativeBuildCreationConfig).whenever(this.variantImpl).nativeBuildCreationConfig
        doReturn(abiSplitOptions).whenever(splits).abi
        doReturn(setOf<String>()).whenever(splits).abiFilters
        doReturn(false).whenever(abiSplitOptions).isUniversalApk
        doReturn(":$appName").whenever(project).path

        return appFolder
    }

    init {
        val ndkFolder = join(sdkDir, "ndk", NDK_DEFAULT_VERSION)
        val meta = join(ndkFolder, "meta")
        meta.mkdirs()
        cmakeDir.mkdirs()
        File(meta, "platforms.json").writeText(platformsJson)
        File(meta, "abis.json").writeText(abisJson)
        val osName = System.getProperty("os.name").lowercase(Locale.ENGLISH)
        val osType = when {
            osName.contains("windows") -> "windows"
            osName.contains("mac") -> "darwin"
            else -> "linux"
        }
        val ndkPrebuilts = join(ndkFolder, "prebuilt")
        val ndkPrebuiltsHostRoot = join(ndkPrebuilts, "$osType-x86_64")
        ndkPrebuiltsHostRoot.mkdirs()
        val stls = listOf(
            "arm-linux-androideabi/libc++_shared.so",
            "aarch64-linux-android/libc++_shared.so",
            "i686-linux-android/libc++_shared.so",
            "x86_64-linux-android/libc++_shared.so"
        )
        val ndkStlRoot = join(ndkFolder, "toolchains/llvm/prebuilt/$osType-x86_64/sysroot/usr/lib")
        stls
            .map { join(ndkStlRoot, it) }
            .onEach { it.parentFile.mkdirs() }
            .onEach { it.writeText("fake STL generated by BasicModuleModelMock") }

        doReturn(cmake).whenever(externalNativeBuild).cmake
        doReturn(ndkBuild).whenever(externalNativeBuild).ndkBuild
        doReturn(null).whenever(cmake).path
        doReturn(null).whenever(ndkBuild).path
        doReturn(null).whenever(cmake).buildStagingDirectory
        doReturn(null).whenever(ndkBuild).buildStagingDirectory
        doReturn(mock<SetProperty<*>>()).whenever(variantExternalNativeBuild).abiFilters
        doReturn(mock<ListProperty<*>>()).whenever(variantExternalNativeBuild).arguments
        doReturn(mock<ListProperty<*>>()).whenever(variantExternalNativeBuild).cFlags
        doReturn(mock<ListProperty<*>>()).whenever(variantExternalNativeBuild).cppFlags
        doReturn(mock<SetProperty<*>>()).whenever(variantExternalNativeBuild).targets
        doReturn(setOf<String>()).whenever(mergedNdkConfig).abiFilters
        doReturn("debug").whenever(variantImpl).name
        doReturn(buildFeatures).whenever(variantImpl).buildFeatures

        projectRootDir.mkdirs()
        sdkDir.mkdirs()

        doReturn(projectOptions).whenever(taskCreationServices).projectOptions

        doReturn(FakeGradleProvider(FakeGradleDirectory(sdkDir))).whenever(sdkComponents).sdkDirectoryProvider
        doReturn(null).whenever(sdkComponents).ndkSymlinkDirFromProperties
        doReturn(null).whenever(sdkComponents).cmakeDirFromProperties
        doReturn("").whenever(projectOptions)
            .get(StringOption.NDK_SUPPRESS_MIN_SDK_VERSION_ERROR)
        doReturn(false).whenever(projectOptions)
            .get(BooleanOption.ENABLE_PROFILE_JSON)
        doReturn(BooleanOption.ENABLE_CMAKE_BUILD_COHABITATION.defaultValue).whenever(projectOptions)
            .get(BooleanOption.ENABLE_CMAKE_BUILD_COHABITATION)
        doReturn(true)
            .whenever(projectOptions).get(BooleanOption.BUILD_ONLY_TARGET_ABI)
        doReturn(false).whenever(buildFeatures).prefab
        doReturn(true)
            .whenever(projectOptions).get(BooleanOption.ENABLE_SIDE_BY_SIDE_CMAKE)
        doReturn(null)
            .whenever(projectOptions).get(StringOption.IDE_BUILD_TARGET_ABI)
        doReturn("verbose")
            .whenever(projectOptions).get(StringOption.NATIVE_BUILD_OUTPUT_LEVEL)

        doReturn(defaultCmakeVersion.toString()).whenever(cmake).version
        doReturn(listOf(Abi.X86.tag, Abi.X86_64.tag, Abi.ARMEABI_V7A.tag, Abi.ARM64_V8A.tag)).whenever(ndkInstallStatus.getOrThrow()).supportedAbis
        doReturn(listOf(Abi.X86.tag)).whenever(ndkInstallStatus.getOrThrow()).defaultAbis

        doReturn(ndkHandler).whenever(sdkComponents).versionedNdkHandler(
            any(), any()
        )
        doReturn(ndkHandler).whenever(globalConfig).versionedNdkHandler
        doReturn(ndkInstallStatus).whenever(ndkHandler).ndkPlatform
        doReturn(ndkInstallStatus).whenever(ndkHandler).getNdkPlatform(true)
        doReturn(true).whenever(variantImpl).debuggable

        val ndkInfo = NdkR25Info(ndkFolder)
        doReturn(ndkInfo).whenever(ndkInstallStatus.getOrThrow()).ndkInfo
        doReturn(ndkFolder).whenever(ndkInstallStatus.getOrThrow()).ndkDirectory
        doReturn(Revision.parseRevision(NDK_DEFAULT_VERSION)).whenever(ndkInstallStatus.getOrThrow()).revision
        doReturn(cmakeDir.parentFile).whenever(cmakeFinder)
            .findCmakePath(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())

        doReturn(cmakeDir.parentFile.resolve("ninja.exe")).whenever(ninjaFinder)
            .findNinjaPath(anyOrNull(), anyOrNull())


        doReturn(null).whenever(gradle).parent

        doReturn(setOf< Prefab>()).whenever(globalConfig).prefabOrEmpty
        doReturn(cmakeDir.resolve("cmake")).whenever(nativeLocationsBuildService).locateCMake(anyOrNull(), anyOrNull())
        doReturn(cmakeDir.resolve("ninja")).whenever(nativeLocationsBuildService).locateNinja(anyOrNull())

        mockModule("app1")
    }

    class RuntimeExceptionAnswer : Answer<Any> {
        override fun answer(invocation: InvocationOnMock): Any {
            throw RuntimeException(invocation.method.toGenericString() + " is not stubbed")
        }
    }
}
