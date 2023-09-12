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
import com.android.utils.cxx.CxxDiagnosticCode.ABI_IS_UNSUPPORTED
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.StringReader

class AbiConfiguratorTest {
    @Rule
    @JvmField
    val tmpFolder = TemporaryFolder()

    companion object {
        val ALL_ABI = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        val ALL_ABI_COMMA_STRING = ALL_ABI.sorted().joinToString(", ")
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
    }

    private val logger = PassThroughRecordingLoggingEnvironment()

    fun configure(
        ndkHandlerSupportedAbis: Set<String> = ALL_ABI,
        ndkHandlerDefaultAbis: Set<String> = ALL_ABI,
        externalNativeBuildAbiFilters: Set<String> = setOf(),
        ndkConfigAbiFilters: Set<String> = setOf(),
        splitsFilterAbis: Set<String> = ALL_ABI.toSet(),
        ideBuildOnlyTargetAbi: Boolean = false,
        ideBuildTargetAbi: String? = null): AbiConfigurator {
        return AbiConfigurator(
                AbiConfigurationKey(
                    ABIS_FROM_META,
                    ndkHandlerSupportedAbis,
                    ndkHandlerDefaultAbis,
                    externalNativeBuildAbiFilters,
                    ndkConfigAbiFilters,
                    splitsFilterAbis,
                    ideBuildOnlyTargetAbi,
                    ideBuildTargetAbi
                )
            )
    }

    @After
    fun after() {
        logger.close()
    }

    @Test
    fun testBaseline() {
        val configurator = configure()
        assertThat(logger.errors).isEmpty()
        // Should be no messages reported
        assertThat(configurator.validAbis).containsExactlyElementsIn(ALL_ABI)
        assertThat(configurator.allAbis).containsExactlyElementsIn(ALL_ABI)
    }

    @Test
    fun testNon64BitWarning() {
        configure(
            externalNativeBuildAbiFilters = setOf("x86"))
        assertThat(logger.errors).isEmpty()
        assertThat(logger.warnings.first().toString()).isEqualTo(
            "[CXX5202] This app only has 32-bit [x86] native libraries. Beginning August 1, " +
                    "2019 Google Play store requires that all apps that include native " +
                    "libraries must provide 64-bit versions. For more information, " +
                    "visit https://g.co/64-bit-requirement")
    }

    @Test
    fun testNoNon64BitWarningForInjectedAbi() {
        configure(
            externalNativeBuildAbiFilters = setOf("x86", "armeabi-v7a"),
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = "x86")
        assertThat(logger.errors).isEmpty()
        assertThat(logger.warnings).isEmpty()
    }

    @Test
    fun testValidAbiInBuildGradleDsl() {
        val configurator = configure(
            externalNativeBuildAbiFilters = setOf("x86"))
        assertThat(logger.errors).isEmpty()
        // Should be no messages reported
        assertThat(configurator.validAbis).containsExactly("x86")
        assertThat(configurator.allAbis).containsExactly("x86")
    }

    // User typed a wrong ABI into build.gradle:externalNativeBuild.cmake.abiFilters
    @Test
    fun testInvalidAbiInBuildGradleDsl() {
        val configurator = configure(
            externalNativeBuildAbiFilters = setOf("x87"))
        assertThat(logger.errors.first().toString()).isEqualTo(
                "${ABI_IS_UNSUPPORTED.codeText} ABIs [x87] are not supported for platform. Supported ABIs " +
                "are [$ALL_ABI_COMMA_STRING].")
        assertThat(configurator.validAbis).isEmpty()
        assertThat(configurator.allAbis).containsExactlyElementsIn(Sets.newHashSet("x87"))
    }

    @Test
    fun testSplitsEnabled() {
        val configurator = configure(
            splitsFilterAbis = setOf("x86"))
        assertThat(logger.errors).isEmpty()
        // Should be no messages reported
        assertThat(configurator.validAbis).containsExactly("x86")
        assertThat(configurator.allAbis).containsExactly("x86")
    }

    @Test
    fun testSplitsEnabledInvalidAbi() {
        val configurator = configure(
            splitsFilterAbis = setOf("x87"))
        assertThat(logger.errors).containsExactly(
                "${ABI_IS_UNSUPPORTED.codeText} ABIs [x87] are not supported for platform. Supported ABIs are "
                + "[$ALL_ABI_COMMA_STRING].")
        assertThat(configurator.validAbis).isEmpty()
        assertThat(configurator.allAbis).containsExactly("x87")
    }

    @Test
    fun testValidAbiThatIsNotInNdk() {
        val configurator = configure(
            ndkHandlerSupportedAbis = setOf("x86_64"),
            externalNativeBuildAbiFilters = setOf("x86"),
            splitsFilterAbis = setOf())
        assertThat(logger.errors).containsExactly(
                "${ABI_IS_UNSUPPORTED.codeText} ABIs [x86] are not supported for platform. " +
                "Supported ABIs are [x86_64].")
        assertThat(configurator.validAbis).containsExactly("x86")
        assertThat(configurator.allAbis).containsExactly("x86")
    }

    @Test
    fun testExternalNativeBuildAbiFiltersAndNdkAbiFiltersAreTheSame() {
        val configurator = configure(
            externalNativeBuildAbiFilters = setOf("x86"),
            ndkConfigAbiFilters = setOf("x86"))
        assertThat(logger.errors).isEmpty()
        // Should be no messages reported
        assertThat(configurator.validAbis).containsExactly("x86")
        assertThat(configurator.allAbis).containsExactly("x86")
    }

    @Test
    fun testExternalNativeBuildAbiFiltersAndNdkAbiFiltersAreNonIntersecting() {
        val configurator = configure(
            externalNativeBuildAbiFilters = setOf("x86_64"),
            ndkConfigAbiFilters = setOf("x86"))
        assertThat(configurator.validAbis).isEmpty()
        assertThat(configurator.allAbis).isEmpty()
    }

    @Test
    fun testValidInjectedAbi() {
        val configurator = configure(
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = "x86")
        assertThat(logger.errors).isEmpty()
        // Should be no messages reported
        assertThat(configurator.validAbis).containsExactly("x86")
        assertThat(configurator.allAbis).containsExactlyElementsIn(ALL_ABI)
    }

    @Test
    fun testValidBogusAndValidInjectedAbi() {
        val configurator = configure(
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = "bogus,x86")

        assertThat(logger.warnings).containsExactly(
            "[CXX5200] ABIs [bogus,x86] set by 'android.injected.build.abi' gradle flag contained " +
                    "'bogus' which is invalid.")
        assertThat(logger.errors).isEmpty()
        assertThat(configurator.validAbis).containsExactly("x86")
        assertThat(configurator.allAbis).containsExactlyElementsIn(ALL_ABI)
    }

    @Test
    fun testToleratedAndValidInjectedAbi() {
        val configurator = configure(
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = "mips64,x86")

        assertThat(logger.infos).containsExactly(
            "C/C++: ABIs [mips64,x86] set by 'android.injected.build.abi' gradle flag " +
                    "contained 'mips64' which is known but invalid for this NDK.")
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(configurator.validAbis).containsExactly("x86")
        assertThat(configurator.allAbis).containsExactlyElementsIn(ALL_ABI)
    }

    @Test
    fun testToleratedAndBogusInjectedAbi() {
        val configurator = configure(
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = "mips64,bogus,x86")
        assertThat(logger.infos).containsExactly(
            "C/C++: ABIs [mips64,bogus,x86] set by 'android.injected.build.abi' gradle flag " +
                    "contained 'mips64' which is known but invalid for this NDK."
        )
        assertThat(logger.warnings).containsExactly(
            "[CXX5200] ABIs [mips64,bogus,x86] set by 'android.injected.build.abi' gradle flag " +
                    "contained 'bogus' which is invalid."
        )
        assertThat(logger.errors).isEmpty()
        assertThat(configurator.validAbis).containsExactly("x86")
        assertThat(configurator.allAbis).containsExactlyElementsIn(ALL_ABI)
    }

    @Test
    fun testBogusInjectedAbi() {
        val configurator = configure(
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = "bogus")
        assertThat(logger.errors).containsExactly(
                "${ABI_IS_UNSUPPORTED.codeText} ABIs [bogus] set by 'android.injected.build.abi' gradle " +
                "flag is not supported. Supported ABIs are " +
                "[$ALL_ABI_COMMA_STRING].")
        assertThat(configurator.validAbis).containsExactlyElementsIn(ALL_ABI)
        assertThat(configurator.allAbis).containsExactlyElementsIn(ALL_ABI)
    }

    @Test
    fun testValidEmptyInjectedAbi() {
        // Empty list should not error
        val configurator = configure(
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = "")
        assertThat(configurator.validAbis).containsExactlyElementsIn(ALL_ABI)
        assertThat(configurator.allAbis).containsExactlyElementsIn(ALL_ABI)
    }

    @Test
    fun testValidNullInjectedAbi() {
        // Empty list should not error
        val configurator = configure(
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = null)
        assertThat(configurator.validAbis).containsExactlyElementsIn(ALL_ABI)
        assertThat(configurator.allAbis).containsExactlyElementsIn(ALL_ABI)
    }

    @Test
    fun testAbiSplitsLookDefaulted() {
        // Empty list should not error
        val configurator = configure(
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = null)
        assertThat(configurator.validAbis).containsExactlyElementsIn(ALL_ABI)
        assertThat(configurator.allAbis).containsExactlyElementsIn(ALL_ABI)
    }

    @Test
    fun testPeopleCanSpecifyMipsIfTheyReallyWantTo() {
        // Empty list should not error
        val configurator = configure(
            splitsFilterAbis = setOf("mips"),
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = null)
        assertThat(configurator.validAbis).containsExactly("mips")
        assertThat(configurator.allAbis).containsExactly("mips")
    }

    @Test
    fun testMisspelledMips() {
        // Empty list should not error
        val configurator = configure(
            splitsFilterAbis = setOf("misp"),
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = null)
        assertThat(logger.errors).containsExactly(
            "${ABI_IS_UNSUPPORTED.codeText} ABIs [misp] are not supported for platform. Supported ABIs are [arm64-v8a, " +
                    "armeabi-v7a, x86, x86_64].")
        assertThat(logger.warnings).isEmpty()
        assertThat(configurator.validAbis).isEmpty()
        assertThat(configurator.allAbis).containsExactly("misp")
    }

    // Related to: http://b/74173612
    @Test
    fun testIdeSelectedAbiDoesntIntersectWithNdkConfigAbiFilters() {
        val configurator = configure(
            ndkConfigAbiFilters = setOf("arm64-v8a", "x86_64"),
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = "armeabi-v7a,armeabi")
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly(
            "C/C++: ABIs [armeabi-v7a,armeabi] set by 'android.injected.build.abi' gradle flag " +
                    "contained 'armeabi, armeabi-v7a' not targeted by this project.")
        assertThat(configurator.validAbis).containsExactly()
    }

    // Related to: http://b/74173612
    @Test
    fun testIdeSelectedAbiDoesntIntersectWithExternalNativeBuildAbiFilters() {
        val configurator = configure(
            externalNativeBuildAbiFilters = setOf("arm64-v8a", "x86_64"),
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = "armeabi-v7a,armeabi")
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly(
            "C/C++: ABIs [armeabi-v7a,armeabi] set by 'android.injected.build.abi' gradle flag " +
                    "contained 'armeabi, armeabi-v7a' not targeted by this project.")
        assertThat(configurator.validAbis).containsExactly()
    }

    // Related to: http://b/74173612
    // This test contains one ideBuildTargetAbi that is not targeted by this project and one that
    // is targeted by this project. There should be a warning and the ABI targeted by this project
    // should be retained.
    @Test
    fun testIdeSelectedAbiDoesntIntersectWithSplitsFilterAbis() {
        val configurator = configure(
            splitsFilterAbis = setOf("arm64-v8a", "x86_64"),
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = "armeabi-v7a,x86_64")
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly(
            "C/C++: ABIs [armeabi-v7a,x86_64] set by 'android.injected.build.abi' gradle flag " +
                    "contained 'armeabi-v7a' not targeted by this project.")
        assertThat(configurator.validAbis).containsExactly("x86_64")
    }

    @Test
    fun testAllowSpaceInInjectedAbi() {
        val configurator = configure(
            ideBuildOnlyTargetAbi = true,
            ideBuildTargetAbi = " x86, x86_64 ")
        assertThat(logger.errors).isEmpty()
        assertThat(logger.warnings).isEmpty()
        assertThat(configurator.validAbis).containsExactly("x86")
    }
}
