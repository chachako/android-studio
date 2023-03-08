/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.profgen.cli

import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import kotlin.io.path.createTempFile
import kotlinx.cli.ExperimentalCli
import org.junit.Test

@ExperimentalCli
class ExpandWildcardsCommandTest {

    @Test
    fun test() {
        val command = ExpandWildcardsCommand()
        val profile = createTempFile(suffix=".txt").toFile()
        profile.writeText("L*;")
        val output = createTempFile(suffix=".txt").toFile()
        command.parse(
                arrayOf(
                        "--profile", profile.toString(),
                        "--output", output.toString(),
                        TestUtils.resolveWorkspacePath(ClassFilePath).toString(),
                        TestUtils.resolveWorkspacePath(JarArchivePath).toString()))
        command.execute()
        assertThat(output.readText()).isEqualTo(
            """
                LHello;
                LWorld;
            """.trimIndent().plus('\n')
        )
    }

    companion object {
        private const val ClassFilePath = "tools/base/profgen/profgen-cli/testData/Hello.class"
        private const val JarArchivePath = "tools/base/profgen/profgen-cli/testData/world.jar"
    }
}
