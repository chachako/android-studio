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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.NDK_WITH_RISCV_ABI
import com.android.testutils.AssumeUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * ModuleToModuleDepsTest for riscv64 abi using NDK r27 dev build that runs only on Linux.
 *
 * TODO: Remove these tests when we upgrade the default NDK to r27 and modify ModuleToModuleDepsTest to run with riscv64.
 */
@RunWith(Parameterized::class)
class RiscvModuleToModuleDepsTest(
    appBuildSystem: BuildSystemConfig,
    libBuildSystem: BuildSystemConfig,
    appUsesPrefabTag: String,
    libUsesPrefabPublishTag: String,
    libExtension: String,
    appStlTag: String,
    libStlTag: String,
    outputStructureType: OutputStructureType,
    headerType: HeaderType
): AbstractModuleToModuleDepsTest(appBuildSystem, libBuildSystem, appUsesPrefabTag,
    libUsesPrefabPublishTag, libExtension, appStlTag, libStlTag, outputStructureType, headerType) {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(multiModule)
            .setSideBySideNdkVersion(NDK_WITH_RISCV_ABI)
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
            NDK_WITH_RISCV_ABI,
            appAbiFilters = """
            ndk {
                abiFilters "riscv64"
            }
            """.trimIndent(),
            libAbiFilters = """
            ndk {
                abiFilters "x86", "arm64-v8a", "armeabi-v7a", "riscv64", "x86_64"
            }
            """.trimIndent()
        )
    }


    @Test
    fun `app configure`() {
        AssumeUtil.assumeIsLinux()
        testAppConfigure("riscv64")
    }

    @Test
    fun `app build`() {
        AssumeUtil.assumeIsLinux()
        testAppBuild("riscv64")
    }

    override fun getTestProject(): GradleTestProject = project
}
