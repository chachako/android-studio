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
package com.android.tools.debuggertests

import com.intellij.util.io.exists
import com.intellij.util.io.isFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString
import kotlin.io.path.reader
import kotlin.io.path.writer
import kotlin.streams.asSequence

/** Utilities for fetching resources */
internal object Resources {

  // Root of running process. IntelliJ runs from `tools/adt/idea` to we remove that
  val ROOT = Paths.get(System.getProperty("user.dir").removeSuffix("tools/adt/idea"))

  // Root location for build artifacts. When running from IntelliJ, we use `bazel-bin`.
  // Required build artifacts should be added to the `idea-deps` file group.
  val BUILD_ROOT = run {
    val bazelBin = ROOT.resolve("bazel-bin")
    when {
      bazelBin.exists() -> bazelBin
      else -> ROOT
    }
  }

  // Root location of source files.
  // When running with Bazel, these need to be added as `data`
  // When running from IntelliJ just use the `ROOT` dir
  private val MODULE_SRC = ROOT.resolve("tools/base/debugger-tests")

  // Module build artifacts location
  private val MODULE_BUILD = BUILD_ROOT.resolve("tools/base/debugger-tests")

  // Test classes location
  private val TESTS = MODULE_SRC.resolve("resources/src/tests")

  // Readable golden classes location. Can be used by local and remote (presubmit) executions
  private val GOLDEN = MODULE_SRC.resolve("resources/res/golden")

  // Writeable golden classes location. Can only be used by local execution.
  private val WORKSPACE_GOLDEN =
    Paths.get(
      System.getenv("BUILD_WORKSPACE_DIRECTORY")
        ?: System.getProperty("user.dir").removeSuffix("tools/adt/idea"),
      "tools/base/debugger-tests",
      "resources/res/golden"
    )

  // Test classes jar file
  val TEST_CLASSES_JAR = MODULE_BUILD.resolve("test-classes-binary_deploy.jar").pathString

  // Test classes dex file
  val TEST_CLASSES_DEX = MODULE_BUILD.resolve("test-classes-dex.jar").pathString

  /** Find all tested classes. */
  fun findTestClasses(): List<String> {
    return Files.walk(TESTS).asSequence().filter(Path::isTestFile).map(Path::toClassName).toList()
  }

  /** Read expected result from golden file */
  fun readGolden(test: String, dir: String): String {
    val path = GOLDEN.resolve("$dir/${getGoldenFileName(test)}")
    return path.reader().use { it.readText() }
  }

  /** Write expected result to golden file */
  fun writeGolden(test: String, actual: String, dir: String) {
    val path = WORKSPACE_GOLDEN.resolve("$dir/${getGoldenFileName(test)}")
    path.parent.toFile().mkdirs()
    path.writer().use { it.write(actual) }
  }
}

private fun getGoldenFileName(test: String) = "${test.replace(".", File.separator)}.txt"

private fun Path.toClassName() =
  pathString
    .substringAfterLast("src${File.separator}")
    .replace(File.separator, ".")
    .removeSuffix(".kt")

private fun Path.isTestFile(): Boolean {
  if (!isFile()) {
    return false
  }
  return reader().readLines().find { it.contains("fun start() {") } != null
}
