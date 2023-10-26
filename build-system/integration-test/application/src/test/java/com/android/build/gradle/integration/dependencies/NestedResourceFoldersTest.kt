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
package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.AssumeUtil
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.util.Scanner

class NestedResourceFoldersTest {
    @get:Rule
    var project = builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    fun simpleNestedDirs() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    sourceSets.main.res.srcDirs = [
                       'src/main/res/layout/people',
                       'src/main/res/layout/parks',
                       'src/main/res/layout',
                       'src/main/res']
                }
                """
        )
        val result = project.executor().run("clean", "mergeDebugResources", "packageDebugResources")

        TruthHelper.assertThat(result.exception).isNull()
        Truth.assertThat(result.stdout.findAll("Nested resources detected.").count()).isEqualTo(1)
        findMultilineText(result.stdout,
        """
        + src/main/res/layout
        -- src/main/res/layout/people
        -- src/main/res/layout/parks

        + src/main/res
        -- src/main/res/layout/people
        -- src/main/res/layout/parks
        -- src/main/res/layout
        """.trimIndent()
        )
    }

    @Test
    fun nestedDirsWithComplexPaths() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    sourceSets.main.res.srcDirs = [
                       'src/main/res/special',
                       'src/main/res/special/one/../two']
                }
                """
        )
        val result = project.executor().run("clean", "mergeDebugResources")
        TruthHelper.assertThat(result.exception).isNull()
        findMultilineText(result.stdout,
            """
        + src/main/res/special
        -- src/main/res/special/two
        """.trimIndent())
    }

    @Test
    fun nestedDirsWithAbsolutePaths() {
        AssumeUtil.assumeNotWindows()

        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    sourceSets.main.res.srcDirs = [
                       '/temp',
                       '/temp/internal']
                }
                """
        )
        val result = project.executor().run("clean", "mergeDebugResources")
        TruthHelper.assertThat(result.exception).isNull()
        findMultilineText(result.stdout,
            """
        + /temp
        -- /temp/internal
        """.trimIndent())
    }

    private fun findMultilineText(s: Scanner, str: String) {
        val buffer = StringBuilder()
        while (s.hasNextLine())
            buffer.append(s.nextLine().replace("\\","/") + "\n")
        TruthHelper.assertThat(buffer.toString()).contains(str)
    }

}
