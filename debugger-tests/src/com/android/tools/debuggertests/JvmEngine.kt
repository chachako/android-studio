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

import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ProcessBuilder.Redirect.INHERIT
import java.lang.ProcessBuilder.Redirect.PIPE
import java.nio.file.Paths
import kotlin.io.path.pathString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val JDWP_OPTIONS = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y"
private val JAVA = if (isWindows()) "java.exe" else "java"

/**
 * An [Engine] that launches and connects to a program using a socket.
 *
 * Starts a java process and connects to it using the socket JDWP transport.
 */
internal class JvmEngine(private val mainClass: String) : Engine() {

  private lateinit var process: Process
  private val scope = CoroutineScope(SupervisorJob())

  override suspend fun createDebugger(): Debugger {
    val classpath = Resources.getTestClassesJarPath()
    process =
      ProcessBuilder(getJavaExe(), JDWP_OPTIONS, "-classpath", classpath, mainClass)
        .redirectOutput(PIPE)
        .redirectError(INHERIT)
        .start()

    val portDeferred = CompletableDeferred<Int>()
    scope.launch {
      launch {
        BufferedReader(InputStreamReader(process.inputStream)).use {
          while (currentCoroutineContext().isActive) {
            val line = withContext(IO) { it.readLine() } ?: break
            println(line)
            if (line.startsWith("Listening")) {
              portDeferred.complete(line.substringAfterLast(": ").toInt())
              break
            }
          }
          while (currentCoroutineContext().isActive) {
            val line = withContext(IO) { it.readLine() } ?: break
            println(line)
          }
        }
      }
    }
    val port = portDeferred.await()
    println("Attaching to localhost:$port")
    return Debugger.attachToProcess("localhost", port)
  }

  override fun close() {
    if (this::process.isInitialized && process.isAlive) {
      process.destroyForcibly()
    }
    scope.cancel()
  }
}

private fun isWindows() = System.getProperty("os.name").lowercase().startsWith("windows")

private fun getJavaExe() = Paths.get(System.getProperty("java.home"), "bin", JAVA).pathString
