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

package com.android.build.gradle.integration

import java.io.File
import org.junit.Test
import kotlin.test.fail
import com.android.testutils.TestUtils
import java.nio.file.Path

class BazelTargetTest {

    /**
     * Regression test for b/171017713.
     *
     * This test prevents someone adding a new test file without also adding a corresponding bazel
     * target.
     */
    @Test
    fun testBazelTargetsMatchTestSourceFiles() {

        val workspaceRoot: Path = TestUtils.getWorkspaceRoot()
        val sourceDir: File =
            workspaceRoot.resolve("tools/base/build-system/integration-test/native/src/test")
                .toFile()
        val bazelFile: File =
            workspaceRoot.resolve("tools/base/build-system/integration-test/native/BUILD.bazel")
                .toFile()
        val ignoredBazelTargets: List<String> = listOf("all_test_files", "prebuilts")

        val testFileNames =
            sourceDir.walk()
                .filter { it.extension == "kt" || it.extension == "java" }
                .map { it.nameWithoutExtension }
                .toList()

        val bazelTargets =
            bazelFile.readLines()
                .filter { it.contains("name = \"") }
                .map { it.split("\"")[1] }
                .filterNot { ignoredBazelTargets.contains(it) }

        val missingTargets = testFileNames.filterNot { bazelTargets.contains(it) }

        if (missingTargets.isNotEmpty()) {
            fail("Missing expected Bazel targets: ${missingTargets.joinToString(", ")}")
        }

        val missingTestFiles = bazelTargets.filterNot { testFileNames.contains(it) }

        if (missingTestFiles.isNotEmpty()) {
            fail("Missing expected test files: ${missingTestFiles.joinToString(", ")}")
        }
    }
}
