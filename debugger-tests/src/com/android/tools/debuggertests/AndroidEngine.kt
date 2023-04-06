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

import java.lang.ProcessBuilder.Redirect.INHERIT
import java.lang.ProcessBuilder.Redirect.PIPE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEVICE_DEX_PATH = "/data/local/tmp/test-classes-dex.jar"
private const val JDWP_PORT = 54321
private const val PUSH_COMMAND = "adb push %s %s"
private const val LAUNCH_COMMAND =
  "adb shell dalvikvm" +
    " -classpath %s" +
    " -XjdwpProvider:adbconnection" +
    " -XjdwpOptions:server=y" +
    " MainKt -dalvik %s"
private const val PORT_FORWARD_COMMAND = "adb forward tcp:%d jdwp:%d"

/**
 * An [Engine] runs on an Android device.
 *
 * This engine deploys a DEX jar file to a device, executes it and attaches a debugger.
 *
 * For now, the engine assumes a single Android device is connected.
 *
 * TODO: Add support for specifying a serial number
 */
internal class AndroidEngine(private val mainClass: String) : Engine() {

  private lateinit var process: Process
  private val scope = CoroutineScope(SupervisorJob())

  /**
   * Starts a debugger:
   * 1. Push the DEX JAR file to the device TODO: Find a way to do this once for all tests
   * 2. Launch process using `dalvikvm`. The process will wait for a `<newline>` before starting the
   *    test.
   * 3. Monitor the output from `dalvik` and extract the PID of the launched process
   * 4. Using the pid, set up port forwarding. TODO: Find a way to find a safe unused port
   * 5. Attach a debugger
   * 6. Suspend the process because `dalvik` doesn't support `suspend`
   * 7. Send a `<newline>` to the process. It is still suspended.
   * 8. The debugger is now ready. Note that unlike other engines, we do not expect a `VMStartEvent`
   *    here so do not call [Debugger.waitForStart]
   */
  override suspend fun startDebugger(): Debugger {
    // Step 1
    pushDexFileToDevice()

    // Step 2
    process =
      ProcessBuilder(LAUNCH_COMMAND.format(DEVICE_DEX_PATH, mainClass).split(' '))
        .redirectOutput(PIPE)
        .redirectError(INHERIT)
        .redirectInput(PIPE)
        .start()

    // Step 3
    val pid = waitForPid(process)

    // Step 4
    setupPortForwarding(pid)

    // Step 5
    println("Attaching to process $pid via adb")
    val debugger = Debugger.attachToProcess("localhost", JDWP_PORT)

    // Step 6
    debugger.suspend()

    // Step 7
    process.outputStream.writer().use { it.write("\n") }

    // Step 8
    return debugger
  }

  private suspend fun waitForPid(process: Process): Int {
    val pidDeferred = CompletableDeferred<Int>()
    scope.launch {
      process.inputStream.bufferedReader().use {
        while (currentCoroutineContext().isActive) {
          val line = withContext(Dispatchers.IO) { it.readLine() } ?: break
          println(line)
          if (line.contains("adb forward tcp")) {
            pidDeferred.complete(line.substringAfterLast(":").toInt())
          }
        }
        while (currentCoroutineContext().isActive) {
          val line = withContext(Dispatchers.IO) { it.readLine() } ?: break
          println(line)
        }
      }
    }
    return pidDeferred.await()
  }

  override fun close() {
    if (this::process.isInitialized && process.isAlive) {
      process.destroyForcibly()
    }
    scope.cancel()
  }
}

private fun pushDexFileToDevice() {
  val dexPath = Resources.getTestClassesDexPath()
  println("Pushing dex file to device")
  Runtime.getRuntime().exec(PUSH_COMMAND.format(dexPath, DEVICE_DEX_PATH)).waitFor()
}

private fun setupPortForwarding(pid: Int) {
  println("Setting up port forwarding")
  Runtime.getRuntime().exec(PORT_FORWARD_COMMAND.format(JDWP_PORT, pid)).waitFor()
}
