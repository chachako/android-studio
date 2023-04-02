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

import ai.grazie.utils.dropPostfix
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.pathString

private const val TEST_DIR = "tests"

private const val RESOURCE_ROOT = "tools/base/debugger-tests/resources/res"

/** Utilities for fetching resources */
object Resources {

  /** Find all tested classes. */
  fun findTestClasses(): List<String> {
    val url = javaClass.classLoader.getResource(TEST_DIR) ?: throw FileNotFoundException(TEST_DIR)
    val classes =
      when (url.protocol) {
        "jar" -> findClassesFromJar()
        "file" -> findClassesFromDir()
        else -> throw IllegalStateException("Unknown protocol: '${url.protocol}'")
      }
    return classes
      .filter { it.endsWith(".class") }
      .map { it.replace('/', '.').dropPostfix(".class") }
  }

  /** Read expected result from golden file */
  fun readGolden(test: String): String {
    val filename = getGoldenFilename(test)
    val stream =
      javaClass.classLoader.getResourceAsStream(filename) ?: throw FileNotFoundException(filename)
    return InputStreamReader(stream).use { it.readText() }
  }

  /** Write expected result to golden file */
  fun writeGolden(test: String, actual: String) {
    val path = Paths.get(getRepoDir(), RESOURCE_ROOT, getGoldenFilename(test))
    OutputStreamWriter(path.outputStream()).use { it.write(actual) }
  }

  private fun findClassesFromJar(): List<String> {
    val url = javaClass.classLoader.getResource(TEST_DIR) ?: throw FileNotFoundException(TEST_DIR)
    val connection =
      url.openConnection() as? JarURLConnection ?: throw IllegalStateException("Jar URL expected")
    return connection.jarFile.use { jarFile ->
      jarFile
        .entries()
        .asSequence()
        .filter { it.name.startsWith(TEST_DIR) }
        .map { it.name }
        .toList()
    }
  }

  private fun findClassesFromDir(): List<String> {
    val url = javaClass.classLoader.getResource(TEST_DIR) ?: throw FileNotFoundException(TEST_DIR)
    val prefix = url.file.dropPostfix("/").length - TEST_DIR.length
    return Files.walk(Paths.get(url.toURI())).map { it.pathString.drop(prefix) }.toList()
  }
}

private fun getRepoDir(): String {
  val pwd = System.getenv("BUILD_WORKSPACE_DIRECTORY") ?: System.getenv("PWD")
  var path = Paths.get(pwd)
  while (path.name != "studio-main") {
    path = path.parent
  }
  return path.pathString
}

private fun getGoldenFilename(test: String) = "golden/${test.replace('.', '/')}.txt"
