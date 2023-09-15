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

package com.android.build.gradle.integration.common.utils

import java.io.File
import com.android.testutils.TestUtils
import org.junit.Assert.fail
import java.nio.file.Path


/**
 * Checks for a 1-1 mapping between source files and bazel targets
 *
 * @param sourceDirRelativePath the relative path of the source directory from the workspace
 *     root. All .kt and .java files in this directory or any subdirectory will be checked for
 *     a corresponding bazel target.
 * @param bazelFileRelativePath the relative path of the bazel build file from the workspace
 *     root
 * @param ignoredBazelTargets the list of any bazel targets that should be ignored when checking
 *     for a 1-1 mapping between source files and bazel targets.
 */
fun checkBazelTargetsMatchTestSourceFiles(
    sourceDirRelativePath: String,
    bazelFileRelativePath: String,
    ignoredBazelTargets: List<String> = emptyList()
) {

    val workspaceRoot: Path = TestUtils.getWorkspaceRoot()
    val sourceDir: File = workspaceRoot.resolve(sourceDirRelativePath).toFile()
    val bazelFile: File = workspaceRoot.resolve(bazelFileRelativePath).toFile()

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
