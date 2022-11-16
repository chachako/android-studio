/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.builder.dexing

import com.android.builder.dexing.testdata.ClassWithDesugarLibraryApi
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.tools.r8.OutputMode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.io.path.createDirectory

class TraceReferenceToolTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun testKeepRuleGeneration() {
        // run r8 to generate dex files
        val programJar = tmp.root.toPath().resolve("program.jar")
        TestInputsGenerator.pathWithClasses(
            programJar,
            listOf(ClassWithDesugarLibraryApi::class.java)
        )
        val dexOutput = tmp.root.toPath().resolve("output").also { it.createDirectory() }

        val toolConfig = ToolConfig(
            minSdkVersion = 21,
            isDebuggable = true,
            disableTreeShaking = true,
            disableDesugaring = true,
            disableMinification = true,
            r8OutputType = R8OutputType.DEX
        )
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val fakeOutput = tmp.newFolder().resolve("fake_output.txt").toPath()
        val emptyProguardOutputFiles =
                ProguardOutputFiles(fakeOutput, fakeOutput, fakeOutput, fakeOutput, fakeOutput)
        val proguardConfig =
                ProguardConfig(listOf(), null, listOf(), emptyProguardOutputFiles)
        val androidJar = TestUtils.resolvePlatformPath("android.jar", TestUtils.TestType.AGP)
        val javaRes = tmp.root.toPath().resolve("javaResources")

        runR8(
            inputClasses = listOf(programJar),
            output = dexOutput,
            inputJavaResources = listOf(),
            javaResourcesJar = javaRes,
            libraries = listOf(),
            classpath = listOf(androidJar),
            toolConfig = toolConfig,
            proguardConfig = proguardConfig,
            mainDexListConfig = mainDexConfig,
            messageReceiver = NoOpMessageReceiver(),
            true,
            featureClassJars = listOf(),
            featureJavaResourceJars = listOf(),
            featureDexDir = null,
            featureJavaResourceOutputDir = null
        )

        // run l8 to generate the desugared desugar library
        val desugaredDesugarLib = tmp.root.toPath().resolve("desugaredDesugarLib")

        runL8(
            listOf(TestUtils.getDesugarLibJar()),
            desugaredDesugarLib,
            TestUtils.getDesugarLibConfigContent(),
            listOf(androidJar),
            21,
            KeepRulesConfig(emptyList(), emptyList()),
            true,
            OutputMode.ClassFile
        )

        // run trace reference tool to generate keep rules
        val keepRuleOutput = tmp.root.toPath().resolve("keepRule")
        runTraceReferenceTool(
            listOf(androidJar),
            listOf(desugaredDesugarLib.resolve("desugared-desugar-lib.jar")),
            listOf(dexOutput.resolve("classes.dex")),
            keepRuleOutput
        )
        check(keepRuleOutput.toFile().exists())
    }
}
