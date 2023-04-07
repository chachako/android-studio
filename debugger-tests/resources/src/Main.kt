import java.util.Scanner
import kotlin.system.exitProcess

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

fun main(args: Array<String>) {
  val testClassName = args.find { !it.startsWith("-") }
  if (testClassName == null) {
    println("Missing test class name argument")
    exitProcess(1)
  }

  if (args[0] == "-android") {
    println(
      """
        Waiting for debugger. run:
           adb forward tcp:54321 jdwp:${getPid()}
        And press <Enter> when connected.
      """
        .trimIndent()
    )
    // ART does not support the JDWP option `suspend` so we must do it ourselves
    waitForDebugger()
  }
  val testClass = object {}.javaClass.classLoader.loadClass(testClassName)
  val method = testClass.getDeclaredMethod("start")
  method.invoke(null)
}

/**
 * Wait for the engine to send a signal to continue
 *
 * We could use `android.os.Debug.waitForDebugger()` which is the way Android Studio does it but
 * this way runs quicker because `waitForDebugger()` needs to wait a few seconds for the debugger to
 * settle down since it doesn't exactly when it's done. We on the other hand know exactly when we
 * can release the app.
 */
fun waitForDebugger() {
  Scanner(System.`in`).nextLine()
}

/**
 * Gets the pid using reflection
 *
 * This code will only need to run on Android/Dalvik. It's easier to just use reflection rather than
 * have to include the Android Framework stubs that are required to compile this normally.
 */
private fun getPid(): Int {
  val process = object {}.javaClass.classLoader.loadClass("android.os.Process")
  return process.getDeclaredMethod("myPid").invoke(null) as Int
}
