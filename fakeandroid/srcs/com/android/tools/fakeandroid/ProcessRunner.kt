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

package com.android.tools.fakeandroid

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.regex.Pattern
import kotlinx.coroutines.*

private const val SLEEP_TIME_MS: Long = 100

open class ProcessRunner
protected constructor(
  private val processArgs: Array<String>,
  private val processEnv: Array<String>,
) {
  private val processName = processArgs[0].substringAfterLast("/")
  private val input = mutableListOf<String>()
  private val error = mutableListOf<String>()
  private lateinit var process: Process
  private val job = SupervisorJob()
  private val scope = CoroutineScope(job + Dispatchers.IO)

  protected constructor(vararg processArgs: String) : this(arrayOf(*processArgs), emptyArray())

  open fun start() {
    process = Runtime.getRuntime().exec(processArgs, processEnv)
    scope.launch { listen("Input", process.inputStream, input) }
    scope.launch { listen("Error", process.errorStream, error) }
  }

  /** @return true if the process is created and alive. */
  val isAlive: Boolean
    get() = ::process.isInitialized && process.isAlive

  protected fun exitValue() = process.exitValue()

  private suspend fun listen(
    streamName: String,
    stream: InputStream,
    storage: MutableList<String>,
  ) {
    try {
      stream.bufferedReader().use { reader ->
        while (!reader.ready()) {
          yield()
        }

        reader.lineSequence().forEach {
          val output = "[$processName-$streamName]: $it"
          synchronized(storage) { storage.add(output) }
          println(output)
        }
      }
    } catch (ex: IOException) {
      // Will get stream closed exception upon completion of test.
    }
  }

  /**
   * Wait for a specific string to be retrieved from the server. This function waits forever if
   * given string statement has not been found.
   */
  @JvmOverloads
  fun waitForInput(statement: String, timeoutMs: Long = LONG_TIMEOUT_MS): Boolean =
    containsStatement(input, statement, timeoutMs)

  @JvmOverloads
  fun waitForError(statement: String, timeoutMs: Long = LONG_TIMEOUT_MS): Boolean =
    containsStatement(error, statement, timeoutMs)

  @JvmOverloads
  fun waitForInput(pattern: Pattern, timeoutMs: Long = LONG_TIMEOUT_MS): String? =
    waitForInput(pattern.toRegex(), timeoutMs)

  /**
   * @param regex that defines a pattern to match in the output. The pattern should define a group
   *   named `result` as the returned element from the input. <br></br> Input:
   *   transport.service.address=127.0.0.1:34801 <br></br> Pattern:
   *   (.*)(transport.service.address=)(?<result>.*) <br></br> Return: 127.0.0.1:34801
   * @return The value found in the result named group, or null if no value found. </result>
   */
  @JvmOverloads
  fun waitForInput(regex: Regex, timeoutMs: Long = LONG_TIMEOUT_MS): String? =
    containsStatement(input, regex, timeoutMs)

  private fun containsStatement(
    storage: List<String>,
    statement: String,
    timeoutMs: Long,
  ): Boolean {
    val regex = Regex("(.*)(?<result>" + Regex.escape(statement) + ")(.*)")
    return (containsStatement(storage, regex, timeoutMs) != null)
  }

  private fun containsStatement(storage: List<String>, regex: Regex, timeoutMs: Long): String? {
    val time = System.currentTimeMillis()
    try {
      while (true) {
        synchronized(storage) {
          storage.reversed().forEach {
            val matcher = regex.matchEntire(it) ?: return@forEach
            return matcher.groups["result"]?.value
          }
        }
        val elapsed = System.currentTimeMillis() - time
        if (elapsed > timeoutMs + SLEEP_TIME_MS) {
          println("Wait Time: ${elapsed}ms. Pattern: ${regex.pattern}")
          return null
        }
        Thread.sleep(SLEEP_TIME_MS)
      }
    } catch (ex: InterruptedException) {
      // ignore
    }
    return null
  }

  fun stop() {
    try {
      process.destroy()
      process.waitFor()
      job.cancel()
    } catch (ex: InterruptedException) {
      // Do nothing.
    }
  }

  companion object {

    const val LONG_TIMEOUT_MS: Long = 100000
    const val SHORT_TIMEOUT_MS: Long = 10000

    @JvmStatic fun getProcessPathRoot(): String = System.getProperty("user.dir") + File.separator

    @JvmStatic
    fun getProcessPath(property: String): String {
      return getProcessPathRoot() + System.getProperty(property)
    }
  }
}
