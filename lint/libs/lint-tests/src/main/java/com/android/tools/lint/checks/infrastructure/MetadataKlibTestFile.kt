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
package com.android.tools.lint.checks.infrastructure

import com.android.tools.lint.checks.infrastructure.TestFiles.computeCheckSum
import com.android.tools.lint.checks.infrastructure.TestFiles.toBase64gzipKotlin
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.metadata.K2MetadataCompiler
import org.junit.Assert

class MetadataKlibTestFile(
  to: String,
  val files: Array<out TestFile>,
  val checksum: Long?,
  val encoding: String?,
) : TestFile() {

  init {
    to(to)
  }

  override fun createFile(targetDir: File): File {
    if (encoding == null) {
      compile(targetDir)
    }
    return readEncoding(targetDir, encoding!!)
  }

  // NB: to run this, you need to pass kotlin-compiler.jar from Kotlin repo/dist as classpath
  private fun compile(targetDir: File) {
    targetRootFolder = targetDir.path

    val tmpSrc = createTempDirectory()
    val tmpOut = createTempDirectory()
    val args =
      buildList<String> {
        files.map { add(it.createFile(tmpSrc).path) }
        add("-d")
        add(tmpOut.path)
        // This flag may simplify the output, but seems marginal and/or unnecessary.
        // add("-Xmetadata-klib=true")
      }

    val outStream = ByteArrayOutputStream()
    val compilerClass = K2MetadataCompiler::class.java
    val compiler = compilerClass.newInstance()
    val execMethod =
      compilerClass.getMethod("exec", PrintStream::class.java, Array<String>::class.java)
    val invocationResult =
      execMethod.invoke(compiler, PrintStream(outStream), args.toTypedArray()) as Enum<*>
    Assert.assertEquals(String(outStream.toByteArray()), ExitCode.OK.name, invocationResult.name)

    tmpSrc.deleteRecursively()
    val (checksum, encodings) = describeTestFiles(targetDir, tmpOut)
    tmpOut.deleteRecursively()
    Assert.fail(
      "Update the test source declaration for $targetRelativePath with this encoding:" +
        "\n\n\"\"\"\n$encodings\"\"\"" +
        "\n\nAlso the checksum is 0x" +
        checksum.toString(16)
    )
  }

  private fun describeTestFiles(targetDir: File, klibFolder: File): Pair<Int, String> {
    val files = mutableSetOf<File>()
    collectFiles(files, klibFolder)
    val klib = File.createTempFile(targetPath, "klib", targetDir)
    klib.deleteOnExit()
    createZipFile(files, klib)
    val bytes = klib.readBytes()
    val checksum = computeCheckSum(targetPath, listOf(bytes))
    return checksum to
      toBase64gzipKotlin(bytes, indent = 4, indentStart = true, includeQuotes = false)
  }

  private fun collectFiles(into: MutableSet<File>, root: File) {
    if (!root.exists()) {
      return
    }
    if (root.isDirectory) {
      val files = root.listFiles()
      for (file in files) {
        collectFiles(into, file)
      }
    } else {
      into.add(root)
    }
  }

  private fun createZipFile(files: Collection<File>, outputZipFile: File): File {
    ZipOutputStream(FileOutputStream(outputZipFile)).use { zipOut ->
      files.forEach { file ->
        FileInputStream(file).use { fis ->
          val zipEntry = ZipEntry(file.name)
          zipOut.putNextEntry(zipEntry)
          fis.copyTo(zipOut)
          zipOut.closeEntry()
        }
      }
    }
    return outputZipFile
  }

  private fun readEncoding(targetDir: File, encoding: String): File {
    val encoded = encoding.trimIndent()
    val producer = TestFiles.getByteProducerForBase64gzip(encoded)
    val binaryFile = BinaryTestFile(targetPath, producer)
    val klib = binaryFile.createFile(targetDir)
    if (checksum != null) {
      val actualBytes = binaryFile.binaryContents
      val actualChecksum = computeCheckSum(targetPath, listOf(actualBytes))
      if (checksum.toInt() != actualChecksum) {
        Assert.fail(
          "The checksum does not match for $targetRelativePath;\n" +
            "expected " +
            "0x${Integer.toHexString(checksum.toInt())} but was " +
            "0x${Integer.toHexString(actualChecksum)}.\n" +
            "Has the source file been changed without updating the binaries?\n" +
            "Don't just update the checksum -- delete the binary file arguments and " +
            "re-run the test first!"
        )
      }
    }
    return klib
  }
}
