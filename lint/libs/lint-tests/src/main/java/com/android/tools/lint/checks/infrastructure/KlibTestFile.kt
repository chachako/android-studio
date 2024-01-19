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
package com.android.tools.lint.checks.infrastructure

import com.android.SdkConstants
import com.google.common.hash.Hashing
import java.io.File
import kotlin.reflect.KClass
import org.junit.Assert

class KlibTestFile(
  to: String,
  val encoded: String?,
  val checksum: Int?,
  vararg val files: TestFile,
) : TestFile() {
  private val sourceLanguage: KLibLanguage =
    KLibLanguage.values().find { lang -> files.all { it::class in lang.sourceFileTypes } }
      ?: throw IllegalArgumentException("Mismatched or unsupported source files in klib")

  init {
    to(to)
    if (encoded != null && checksum != null) {
      val computedChecksum = computeChecksum(encoded)
      assert(computedChecksum == checksum) {
        "Expected checksum is ${computedChecksum.toString(16)}, given ${checksum.toString(16)}" +
          "Encoded:\n" +
          encoded
      }
    }
  }

  override fun createFile(targetDir: File): File {
    if (encoded == null) compile(targetDir)
    return LintDetectorTest.base64gzip(targetRelativePath, encoded!!).createFile(targetDir)
  }

  private fun compile(targetDir: File) {
    val tmpDir = createTempDirectory()
    targetRootFolder = targetDir.path

    fun findOnPath(target: String): String? =
      System.getenv("PATH")?.split(File.pathSeparator)?.firstNotNullOfOrNull { binDir ->
        val file = File(binDir + File.separator + target)
        file.path.takeIf {
          file.isFile /* maybe file.canExecute() too but not sure how .bat files behave */
        }
      }

    fun find(tag: String, flag: String): String {
      val isWindows = SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS
      val target =
        System.getenv(flag)
          ?: findOnPath("$tag${if (isWindows) ".bat" else ""}")
          ?: error(
            "Couldn't find $tag to update test file $targetPath with. Point to it with \$$flag"
          )
      if (!File(target).isFile) Assert.fail("$target is not a file")
      if (!File(target).canExecute()) Assert.fail("$target is not executable")
      return target
    }

    fun findNativeCompiler() = find("kotlinc-native", "LINT_TEST_KOTLINC_NATIVE")
    fun findCinterop() = find("cinterop", "LINT_TEST_INTEROP")

    CompiledSourceFile.executeProcess(
      when (sourceLanguage) {
        KLibLanguage.Kotlin ->
          listOf(findNativeCompiler(), "-p", "library", "-o", targetPath) +
            files.map { it.createFile(tmpDir).path }
        KLibLanguage.C ->
          listOf(
            findCinterop() +
              files.filterIsInstance<DefTestFile>().flatMap {
                listOf("-def", it.createFile(tmpDir).path)
              } +
              listOf("-o", targetPath.removeSuffix(".klib"))
          )
      }
    )

    tmpDir.deleteRecursively()
    val target = File(targetPath)
    val checksum = computeChecksum(TestFiles.toBase64gzipString(target.readBytes()))
    Assert.fail(
      "Update the test source declaration for $targetRelativePath with this encoding: " +
        "\n\n${TestFiles.toBase64gzip(target)}" +
        "\n\nAlso the checksum is " +
        checksum.toString(16)
    )
  }

  private enum class KLibLanguage(val sourceFileTypes: Set<KClass<*>>) {
    Kotlin(setOf(KotlinTestFile::class)),
    C(setOf(CTestFile::class, DefTestFile::class))
  }
}

@Suppress("UnstableApiUsage")
private fun computeChecksum(s: String): Int =
  Hashing.sha256().newHasher().run {
    putString(s, Charsets.UTF_8)
    hash().asInt()
  }
