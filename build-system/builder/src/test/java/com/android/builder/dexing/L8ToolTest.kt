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

package com.android.builder.dexing

import com.android.testutils.TestUtils
import com.android.testutils.truth.DexSubject
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.r8.OutputMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Sanity test to make sure we can invoke L8 successfully
 */
class L8ToolTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun testDexGeneration() {
        val output = tmp.newFolder().toPath()
        runL8(
            desugarJar,
            output,
            desugarConfig,
            bootClasspath,
            20,
            KeepRulesConfig(emptyList(), emptyList()),
            true,
            OutputMode.DexIndexed
        )
        assertThat(getDexFileCount(output)).isEqualTo(1)
        assertThat(output.resolve("classes1000.dex")).exists()
    }

    @Test
    fun testShrinking() {
        val output = tmp.newFolder().resolve("out")
        val input = tmp.newFolder().toPath()

        val keepRulesFile1 = input.toFile().resolve("keep_rules").also { file ->
            file.bufferedWriter().use {
                it.write("-keep class j$.util.stream.Stream {*;}")
            }
        }
        val keepRulesFile2 = input.toFile().resolve("dir/keep_rules").also { file ->
            file.parentFile.mkdirs()
            file.bufferedWriter().use {
                it.write("-keep class j$.util.Optional {*;}")
            }
        }
        runL8(
            desugarJar,
            output.toPath(),
            desugarConfig,
            bootClasspath,
            20,
            KeepRulesConfig(
                listOf(keepRulesFile1.toPath(), keepRulesFile2.toPath()),
                emptyList()
            ),
            true,
            OutputMode.DexIndexed
        )
        val dexFile = output.resolve("classes1000.dex")
        DexSubject.assertThatDex(dexFile).containsClass("Lj$/util/stream/Stream;")
        DexSubject.assertThatDex(dexFile).containsClass("Lj$/util/Optional;")
        // check unused API classes are removed from the from desugar lib dex.
        DexSubject.assertThatDex(dexFile).doesNotContainClasses("Lj$/time/LocalTime;")

    }

    private fun getDexFileCount(dir: Path): Long =
        Files.list(dir).filter { it.toString().endsWith(".dex") }.count()

    companion object {
        val bootClasspath = listOf(
            TestUtils.resolvePlatformPath("android.jar", TestUtils.TestType.AGP)
        )
        val desugarJar = listOf(TestUtils.getDesugarLibJar())
        val desugarConfig = TestUtils.getDesugarLibConfigContent()
    }
}
