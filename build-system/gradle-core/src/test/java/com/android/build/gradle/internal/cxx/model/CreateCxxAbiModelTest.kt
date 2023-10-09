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

import com.android.build.gradle.internal.cxx.RandomInstanceGenerator
import com.android.build.gradle.internal.cxx.logging.PassThroughRecordingLoggingEnvironment
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CreateCxxAbiModelTest {
    // Check some specific issues I had to debug
    @Test
    fun `repeated cmake bug`() {
        BasicCmakeMock().let {
            val module = createCxxModuleModel(
                it.sdkComponents,
                it.configurationParameters,
            )
            val variant = createCxxVariantModel(
                it.configurationParameters,
                module)
            val abi = createCxxAbiModel(
                it.sdkComponents,
                it.configurationParameters,
                variant, "x86")
            assertThat(abi.cxxBuildFolder.path
                    .replace("\\", "/"))
                .endsWith(".cxx/cmake/debug/x86")
        }
    }

    @Test
    fun `unknown ABI name`() {
        BasicCmakeMock().let {
            val module = createCxxModuleModel(
                it.sdkComponents,
                it.configurationParameters,
            )
            val variant = createCxxVariantModel(
                it.configurationParameters,
                module)

            PassThroughRecordingLoggingEnvironment().use { logger ->
                try {
                    createCxxAbiModel(
                        it.sdkComponents,
                        it.configurationParameters,
                        variant, "unknown-abi-name"
                    )
                } catch (e: Exception) {
                    assertThat(logger.errors[0])
                        .isEqualTo("[CXX1201] ABI unknown-abi-name was not recognized. " +
                                "Valid ABIs are: arm64-v8a, armeabi-v7a, riscv64, x86, x86_64.")
                    return
                }
                error("Expected exception from createCxxAbiModel")
            }
        }
    }

    @Test
    fun `round trip random instance`() {
        RandomInstanceGenerator()
            .synthetics(CxxAbiModel::class.java)
            .forEach { abi ->
                val abiString = abi.toJsonString()
                val recoveredAbi = createCxxAbiModelFromJson(abiString)
                val recoveredAbiString = recoveredAbi.toJsonString()
                assertThat(abiString).isEqualTo(recoveredAbiString)
            }
    }
}
