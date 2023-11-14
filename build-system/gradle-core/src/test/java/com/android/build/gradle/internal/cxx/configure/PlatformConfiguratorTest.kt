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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.codeText
import com.android.build.gradle.internal.cxx.logging.PassThroughRecordingLoggingEnvironment
import com.android.sdklib.AndroidVersion
import com.android.utils.cxx.CxxDiagnosticCode.ABI_IS_INVALID
import com.android.utils.cxx.CxxDiagnosticCode.NDK_DOES_NOT_SUPPORT_API_LEVEL
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.StringReader

class PlatformConfiguratorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val defaultApiLevelFromDsl = AndroidVersion.DEFAULT.apiLevel
    private val expectedNdkR17MetaPlatforms = "{\n" +
            "  \"min\": 16,\n" +
            "  \"max\": 28,\n" +
            "  \"aliases\": {\n" +
            "    \"20\": 19,\n" +
            "    \"25\": 24,\n" +
            "    \"J\": 16,\n" +
            "    \"J-MR1\": 17,\n" +
            "    \"J-MR2\": 18,\n" +
            "    \"K\": 19,\n" +
            "    \"L\": 21,\n" +
            "    \"L-MR1\": 22,\n" +
            "    \"M\": 23,\n" +
            "    \"N\": 24,\n" +
            "    \"N-MR1\": 24,\n" +
            "    \"O\": 26,\n" +
            "    \"O-MR1\": 27,\n" +
            "    \"P\": 28\n" +
            "  }\n" +
            "}"
    private val expectedNdkR26MetaPlatforms = """
        {
          "min": 21,
          "max": 34,
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
            "Q": 29,
            "R": 30,
            "S": 31,
            "Sv2": 32,
            "Tiramisu": 33,
            "UpsideDownCake": 34
          }
        }
    """.trimIndent()
    val ABIS_FROM_META = parseAbiJson(StringReader("""
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
              },
              "mips": {
                "bitness": 32,
                "default": false,
                "deprecated": true,
                "proc": "mips",
                "arch": "mips",
                "triple": "mipsel-linux-android",
                "llvm_triple": "mipsel-linux-android"
              },
              "armeabi": {
                "bitness": 32,
                "default": false,
                "deprecated": true,
                "proc": "armeabi",
                "arch": "armeabi",
                "triple": "arm-linux-androideabi",
                "llvm_triple": "arm-linux-androideabi"
              }
            }
        """.trimIndent()), "unknown")
    private val logger = PassThroughRecordingLoggingEnvironment()
    private lateinit var ndk17: File
    private lateinit var ndk26: File

    @Before
    fun before() {
        ndk17 = temporaryFolder.newFolder("17").absoluteFile
        ndk26 = temporaryFolder.newFolder("26").absoluteFile
    }

    @After
    fun after() {
        logger.close()
    }

    private fun expectedNdkR17MetaPlatforms() : NdkMetaPlatforms {
        return NdkMetaPlatforms.fromReader(StringReader(expectedNdkR17MetaPlatforms))
    }

    private fun expectedNdkR26MetaPlatforms() : NdkMetaPlatforms {
        return NdkMetaPlatforms.fromReader(StringReader(expectedNdkR26MetaPlatforms))
    }

    private fun platformConfiguratorNdk16() : PlatformConfigurator {
        val root = temporaryFolder.newFolder("16").absoluteFile
        root.deleteRecursively()
        File(root, "platforms/android-14/arch-x86").mkdirs()
        File(root, "platforms/android-15/arch-x86").mkdirs()
        File(root, "platforms/android-16/arch-x86").mkdirs()
        File(root, "platforms/android-18/arch-x86").mkdirs()
        File(root, "platforms/android-19/arch-x86").mkdirs()
        File(root, "platforms/android-21/arch-x86").mkdirs()
        File(root, "platforms/android-22/arch-x86").mkdirs()
        File(root, "platforms/android-23/arch-x86").mkdirs()
        File(root, "platforms/android-24/arch-x86").mkdirs()
        File(root, "platforms/android-26/arch-x86").mkdirs()
        File(root, "platforms/android-27/arch-x86").mkdirs()
        return PlatformConfigurator(root)
    }

    private fun platformConfiguratorNdk17() : PlatformConfigurator {
        val root = ndk17
        root.deleteRecursively()
        File(root, "platforms/android-14/arch-x86").mkdirs()
        File(root, "platforms/android-15/arch-x86").mkdirs()
        File(root, "platforms/android-16/arch-x86").mkdirs()
        File(root, "platforms/android-18/arch-x86").mkdirs()
        File(root, "platforms/android-19/arch-x86").mkdirs()
        File(root, "platforms/android-21/arch-x86").mkdirs()
        File(root, "platforms/android-22/arch-x86").mkdirs()
        File(root, "platforms/android-23/arch-x86").mkdirs()
        File(root, "platforms/android-24/arch-x86").mkdirs()
        File(root, "platforms/android-26/arch-x86").mkdirs()
        File(root, "platforms/android-27/arch-x86").mkdirs()
        File(root, "platforms/android-28/arch-x86").mkdirs()
        return PlatformConfigurator(root)
    }

    private fun platformConfiguratorNdk26() : PlatformConfigurator {
        val root = ndk26
        root.deleteRecursively()
        return PlatformConfigurator(root)
    }

    private fun platformConfiguratorNdkInvalid() : PlatformConfigurator {
        val root = File("./invalid").absoluteFile
        root.deleteRecursively()
        return PlatformConfigurator(root)
    }

    private fun platformConfiguratorNdk17ButHasWeirdAndroidFolder() : PlatformConfigurator {
        val root = temporaryFolder.newFolder("17-weird").absoluteFile
        root.deleteRecursively()
        File(root, "platforms/android-14/arch-x86").mkdirs()
        File(root, "platforms/android-15/arch-x86").mkdirs()
        File(root, "platforms/android-16/arch-x86").mkdirs()
        File(root, "platforms/android-18/arch-x86").mkdirs()
        File(root, "platforms/android-19/arch-x86").mkdirs()
        File(root, "platforms/android-21/arch-x86").mkdirs()
        File(root, "platforms/android-22/arch-x86").mkdirs()
        File(root, "platforms/android-23/arch-x86").mkdirs()
        File(root, "platforms/android-24/arch-x86").mkdirs()
        File(root, "platforms/android-26/arch-x86").mkdirs()
        File(root, "platforms/android-27/arch-x86").mkdirs()
        File(root, "platforms/android-28/arch-x86").mkdirs()
        File(root, "platforms/android-bob/arch-x86").mkdirs()
        return PlatformConfigurator(root)
    }

    private fun platformConfiguratorMissingSomePlatforms() : PlatformConfigurator {
        val root = temporaryFolder.newFolder("17-incomplete").absoluteFile
        root.deleteRecursively()
        File(root, "platforms/android-19/arch-x86").mkdirs()
        File(root, "platforms/android-21/arch-x86").mkdirs()
        File(root, "platforms/android-24/arch-x86").mkdirs()
        return PlatformConfigurator(root)
    }

    private fun findSuitablePlatformVersion(
        platformConfigurator: PlatformConfigurator,
        abiName: String,
        minSdkVersion: Int?,
        codeName: String?,
        ndkMetaPlatforms: NdkMetaPlatforms? = null,
        vararg ignoreMinSdkVersion: Int = intArrayOf()) : Int {
        val androidVersion = if (minSdkVersion == null && codeName == null) {
            null
        } else {
            AndroidVersion(minSdkVersion ?: 0, codeName)
        }
        return platformConfigurator.findSuitablePlatformVersionLogged(
                abiName,
                ABIS_FROM_META,
                androidVersion,
                ndkMetaPlatforms,
                ignoreMinSdkVersion.toList())
    }

    @Test
    fun testPlatformJustFoundNdk16() {
        val configurator = platformConfiguratorNdk16()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            27,
            null)
        assertThat(platform).isEqualTo(27)
        assertThat(logger.messageCount).isEqualTo(0)
    }

    @Test
    fun testNdkPlatformFallbackNdk16() {
        val configurator = platformConfiguratorNdk16()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            28,
            null)
        assertThat(platform).isEqualTo(27)
        assertThat(logger.warnings).containsExactly("C/C++: Platform version " +
                "28 is beyond 27, the maximum API level supported by this NDK. Using 27 instead.")
    }

    @Test
    fun testPlatformTooLowClimbToMinimumNdk16() {
        val configurator = platformConfiguratorNdk16()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            13,
            null)
        assertThat(platform).isEqualTo(14)
        assertThat(logger.messageCount).isEqualTo(1)
        assertThat(logger.warnings.single()).isEqualTo(
            "C/C++: Platform version 13 is unsupported by this NDK, using 14 instead. Please change minSdk to at least 14 to avoid this warning.")
    }

    @Test
    fun testPlatformJustFoundNdk17() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            28,
            null)
        assertThat(platform).isEqualTo(28)
        assertThat(logger.messageCount).isEqualTo(0)
    }

    @Test
    fun testNdkPlatformFallbackNdk17() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            29,
            null)
        assertThat(platform).isEqualTo(28)
        assertThat(logger.warnings).containsExactly("C/C++: Platform version 29 " +
                "is beyond 28, the maximum API level supported by this NDK. Using 28 instead.")
    }

    @Test
    fun testPlatformTooLowClimbToMinimumNdk17() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            13,
            null)
        assertThat(platform).isEqualTo(14)
        assertThat(logger.messageCount).isEqualTo(1)
        assertThat(logger.warnings.single()).isEqualTo(
            "C/C++: Platform version 13 is unsupported by this NDK, using 14 instead. Please change minSdk to at least 14 to avoid this warning.")
    }

    @Test
    fun testPlatformFoundByCodeNdk17() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "P")
        assertThat(platform).isEqualTo(28)
        assertThat(logger.infos).containsExactly(
            "C/C++: Version minSdkVersion='P' is mapped to '28'.")
    }

    @Test
    fun testAliasInMinSdkVersionPositionNdk17() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            20,
            null)
        assertThat(logger.infos).containsExactly("C/C++: Version minSdkVersion='20' " +
                "is mapped to '19'.")
        assertThat(platform).isEqualTo(19)
    }

    @Test
    fun testPlatformUnknownMrCodeNdk17() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "O-MR2" // <- doesn't exist
           )
        assertThat(platform).isEqualTo(28)
        assertThat(logger.errors).containsExactly(
            "${NDK_DOES_NOT_SUPPORT_API_LEVEL.codeText} API codeName 'O-MR2' is not supported by NDK '$ndk17'."
        )
    }

    @Test
    fun testNoVersionSpecifiedNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            null,
            expectedNdkR17MetaPlatforms())
        assertThat(platform).isEqualTo(16)
        assertThat(logger.infos).containsExactly("C/C++: Neither codeName nor " +
                "minSdkVersion specified. Using minimum platform version for 'x86'.")
    }

    @Test
    fun testPlatformJustFoundNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            28,
            null,
            expectedNdkR17MetaPlatforms())
        assertThat(platform).isEqualTo(28)
        assertThat(logger.messageCount).isEqualTo(0)
    }

    @Test
    fun testNdkPlatformFallbackNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            29,
            null,
            expectedNdkR17MetaPlatforms())
        assertThat(platform).isEqualTo(28)
        assertThat(logger.warnings).containsExactly("C/C++: Platform version 29 " +
                "is beyond 28, the maximum API level supported by this NDK. Using 28 instead.")
    }

    @Test
    fun testPlatformTooLowClimbToMinimumNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            abiName = "x86",
            minSdkVersion = 13,
            codeName = null,
            expectedNdkR17MetaPlatforms())
        assertThat(platform).isEqualTo(16)
        assertThat(logger.messageCount).isEqualTo(1)
        assertThat(logger.warnings.single()).isEqualTo(
            "C/C++: Platform version 13 is unsupported by this NDK, using 16 instead. Please change minSdk to at least 16 to avoid this warning.")
    }

    @Test
    fun `Bug 310718265 platform too low becomes error after min SDK version 19`() {
        val configurator = platformConfiguratorNdk26()
        val platform = findSuitablePlatformVersion(
            configurator,
            abiName = "x86",
            minSdkVersion = 19,
            codeName = null,
            expectedNdkR26MetaPlatforms())
        assertThat(platform).isEqualTo(19)
        assertThat(logger.messageCount).isEqualTo(1)
        assertThat(logger.errors.single()).isEqualTo(
            "[CXX1110] Platform version 19 is unsupported by this NDK. Please change minSdk to at least 21 to avoid undefined behavior. " +
                    "To suppress this error, add android.ndk.suppressMinSdkVersionError=19 to the project's gradle.properties.")
    }

    @Test
    fun `Bug 310718265 platform too low error ignored if user passes flag`() {
        val configurator = platformConfiguratorNdk26()
        val platform = findSuitablePlatformVersion(
            configurator,
            abiName = "x86",
            minSdkVersion = 19,
            codeName = null,
            expectedNdkR26MetaPlatforms(),
            19)
        assertThat(platform).isEqualTo(19)
        assertThat(logger.messageCount).isEqualTo(0)
    }

    @Test
    fun `Bug 310718265 platform too low error ignored if user passed wrong flag`() {
        val configurator = platformConfiguratorNdk26()
        val platform = findSuitablePlatformVersion(
            configurator,
            abiName = "x86",
            minSdkVersion = 19,
            codeName = null,
            expectedNdkR26MetaPlatforms(),
            20)
        assertThat(platform).isEqualTo(19)
        assertThat(logger.messageCount).isEqualTo(1)
        assertThat(logger.errors.single()).isEqualTo(
            "[CXX1110] Platform version 19 is unsupported by this NDK. Please change minSdk to at least 21 to avoid undefined behavior. " +
                    "To suppress this error, add android.ndk.suppressMinSdkVersionError=19 to the project's gradle.properties.")
    }

    @Test
    fun testPlatformFoundByCodeNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "P",
            expectedNdkR17MetaPlatforms())
        assertThat(platform).isEqualTo(28)
        assertThat(logger.infos).containsExactly(
            "C/C++: Version minSdkVersion='P' is mapped to '28'.")
    }

    @Test
    fun testAliasInMinSdkVersionPositionNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            20,
            null,
            expectedNdkR17MetaPlatforms())
        assertThat(logger.infos).containsExactly("C/C++: Version minSdkVersion='20' " +
                "is mapped to '19'.")
        assertThat(platform).isEqualTo(19)
    }

    @Test
    fun testPlatformMrCodeNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "O-MR1",
            expectedNdkR17MetaPlatforms())
        assertThat(platform).isEqualTo(27)
        assertThat(logger.infos).containsExactly(
            "C/C++: Version minSdkVersion='O-MR1' is mapped to '27'.")
    }

    @Test
    fun testPlatformUnknownMrCodeNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "O-MR2", // <- doesn't exist
            expectedNdkR17MetaPlatforms())
        assertThat(platform).isEqualTo(28)
        assertThat(logger.errors).containsExactly(
            "${NDK_DOES_NOT_SUPPORT_API_LEVEL.codeText} API codeName 'O-MR2' is not supported by NDK '$ndk17'.")
    }

    @Test
    fun testWeirdABI() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "bob",
            13,
            null)
        assertThat(platform).isEqualTo(AndroidVersion.MIN_RECOMMENDED_API)
        assertThat(logger.errors).containsExactly("${ABI_IS_INVALID.codeText} Specified abi='bob' " +
                "is not recognized.")
    }

    @Test
    fun testBothMinSdkAndCodeNameAgree() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            28,
            "P")
        assertThat(platform).isEqualTo(28)
        assertThat(logger.infos).containsExactly(
            "C/C++: Version minSdkVersion='P' is mapped to '28'.")
        assertThat(logger.warnings).containsExactly(
            "C/C++: Both codeName and minSdkVersion specified. They agree but only " +
                    "one should be specified.")
    }

    @Test
    fun testBothMinSdkAndCodeNameDisagree() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            27,
            "P")
        assertThat(logger.infos).containsExactly(
            "C/C++: Version minSdkVersion='P' is mapped to '28'.",
            "C/C++: Disagreement between codeName='P' and minSdkVersion='27'. Probably a preview " +
                    "release. Using 28 to match code name.")
        assertThat(platform).isEqualTo(28)
    }

    @Test
    fun testMissingNDKFolder() {
        val configurator = platformConfiguratorNdkInvalid()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            27,
            "P")
        assertThat(platform).isEqualTo(AndroidVersion.MIN_RECOMMENDED_API)
        val message = logger.warnings.first()
        assertThat(message).contains("does not contain 'platforms'.")
    }

    @Test
    fun testPlatformConfiguratorNdk17ButHasWeirdAndroidFolder() {
        val configurator = platformConfiguratorNdk17ButHasWeirdAndroidFolder()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            20,
            null)
        assertThat(platform).isEqualTo(19)
        assertThat(logger.infos).containsExactly("C/C++: Version minSdkVersion='20' " +
                "is mapped to '19'.")
    }

    @Test
    fun testVeryOldCodenameGetsPromoted() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "J")
        assertThat(platform).isEqualTo(16)
        assertThat(logger.infos).containsExactly("C/C++: Version " +
                "minSdkVersion='J' is mapped to '16'.")
    }

    @Test
    fun testNotYetKnownCodenameFallsBackToMaximumForNdk() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "Z")
        assertThat(platform).isEqualTo(28)
        assertThat(logger.errors).containsExactly(
            "${NDK_DOES_NOT_SUPPORT_API_LEVEL.codeText} API codeName 'Z' is not supported by NDK '$ndk17'.")
    }

    @Test
    fun testEmptyVersionInfoInBuildGradle() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            null,
            null)
        assertThat(platform).isEqualTo(22)
        assertThat(logger.messageCount).isEqualTo(0)
    }

    @Test
    fun testAgainstIncompletePlatformsFolder() {
        val configurator = platformConfiguratorMissingSomePlatforms()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            null,
            null)
        assertThat(platform).isEqualTo(19)
        assertThat(logger.warnings).containsExactly("C/C++: Expected platform " +
                "folder platforms/android-22, using platform API 19 instead.")
    }
}
