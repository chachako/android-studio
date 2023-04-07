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
import java.io.Closeable
import java.io.InputStreamReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * An [Engine] that launches and connects to a program using a socket.
 *
 * Starts a debuggable process and connects to it using the socket JDWP transport.
 */
internal abstract class AttachingEngine(vmName: String) : Engine(vmName) {

  /**
   * Starts a debugger:
   * 1. Launch process using `java`.
   * 2. Monitor the output from `java` and extract the debugger port
   * 3. Attach a debugger and wait for vm to start
   */
  override suspend fun startDebugger(mainClass: String): Debugger {
    val scope = CoroutineScope(SupervisorJob())

    // Step 1
    val process =
      ProcessBuilder(buildCommandLine(mainClass))
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    // Step 2
    val port = waitForPortNumber(scope, process)

    // Step 3
    println("Attaching to localhost:$port")
    return DebuggerWithResources(
      DebuggerImpl.attachToProcess("localhost", port).waitForStart(),
      Closeable { process.destroyForcibly() },
      Closeable { scope.cancel() },
    )
  }

  protected abstract fun buildCommandLine(mainClass: String): List<String>

  private suspend fun waitForPortNumber(scope: CoroutineScope, process: Process): Int {
    val portDeferred = CompletableDeferred<Int>()
    scope.launch {
      launch {
        BufferedReader(InputStreamReader(process.inputStream)).use {
          while (currentCoroutineContext().isActive) {
            val line = withContext(Dispatchers.IO) { it.readLine() } ?: break
            println(line)
            if (line.startsWith("Listening")) {
              portDeferred.complete(line.substringAfterLast(": ").toInt())
              break
            }
          }
          while (currentCoroutineContext().isActive) {
            val line = withContext(Dispatchers.IO) { it.readLine() } ?: break
            println(line)
          }
        }
      }
    }
    return portDeferred.await()
  }
}
