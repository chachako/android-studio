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

import java.io.Closeable
import java.lang.ProcessBuilder.Redirect.INHERIT
import java.lang.ProcessBuilder.Redirect.PIPE
import kotlin.io.path.pathString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ADB = Resources.ROOT.resolve("prebuilts/studio/sdk/linux/platform-tools/adb").pathString
private const val DEVICE_DEX_PATH = "/data/local/tmp/test-classes-dex.jar"
private const val JDWP_PORT = 54321
private const val PUSH_COMMAND = "%s push %s %s"
private const val LAUNCH_COMMAND =
  "%s shell dalvikvm" +
    " -classpath %s" +
    " -XjdwpProvider:adbconnection" +
    " -XjdwpOptions:server=y" +
    " MainKt -android %s"
private const val PORT_FORWARD_COMMAND = "%s forward tcp:%d jdwp:%d"

/**
 * An [Engine] runs on an Android device.
 *
 * This engine deploys a DEX jar file to a device, executes it and attaches a debugger.
 *
 * For now, the engine assumes a single Android device is connected.
 *
 * TODO: Add support for specifying a serial number
 */
internal class AndroidEngine(serialNumber: String? = null) : Engine("dex") {

  private val adb = if (serialNumber == null) ADB else "$ADB -s $serialNumber"

  init {
    pushDexFileToDevice()
  }

  /**
   * Starts a debugger:
   * 1. Launch process using `dalvikvm`. The process will wait for a `<newline>` before starting the
   *    test.
   * 2. Monitor the output from `dalvik` and extract the PID of the launched process
   * 3. Using the pid, set up port forwarding. TODO: Find a way to find a safe unused port
   * 4. Attach a debugger
   * 5. Suspend the process because `dalvik` doesn't support `suspend`
   * 6. Send a `<newline>` to the process. It is still suspended.
   * 7. The debugger is now ready. Note that unlike other engines, we do not expect a `VMStartEvent`
   *    here so do not call [Debugger.waitForStart]
   */
  override suspend fun startDebugger(mainClass: String): Debugger {
    val scope = CoroutineScope(SupervisorJob())

    // Step 1
    val process =
      ProcessBuilder(LAUNCH_COMMAND.format(adb, DEVICE_DEX_PATH, mainClass).split(' '))
        .redirectOutput(PIPE)
        .redirectError(INHERIT)
        .redirectInput(PIPE)
        .start()

    // Step 2
    val pid = waitForPid(process, scope)

    // Step 3
    setupPortForwarding(pid)

    // Step 4
    println("Attaching to process $pid via adb")
    val debugger = DebuggerImpl.attachToProcess("localhost", JDWP_PORT)

    // Step 5
    debugger.suspend()

    // Step 6
    process.outputStream.writer().use { it.write("\n") }

    // Step 7
    return DebuggerWithResources(
      debugger,
      Closeable { scope.cancel() },
      Closeable { if (process.isAlive) process.destroyForcibly() },
    )
  }

  private suspend fun waitForPid(process: Process, scope: CoroutineScope): Int {
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

  private fun pushDexFileToDevice() {
    val dexPath = Resources.TEST_CLASSES_DEX
    println("Pushing dex file to device")
    val rc = Runtime.getRuntime().exec(PUSH_COMMAND.format(adb, dexPath, DEVICE_DEX_PATH)).waitFor()
    if (rc != 0) {
      throw IllegalStateException("Failed to push dex file to device")
    }
  }

  private fun setupPortForwarding(pid: Int) {
    println("Setting up port forwarding")
    val rc = Runtime.getRuntime().exec(PORT_FORWARD_COMMAND.format(adb, JDWP_PORT, pid)).waitFor()
    if (rc != 0) {
      throw IllegalStateException("Failed to set up port forwarding")
    }
  }
}
