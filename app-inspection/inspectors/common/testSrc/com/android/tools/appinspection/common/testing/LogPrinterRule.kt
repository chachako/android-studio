/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.tools.appinspection.common.testing

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.rules.ExternalResource
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLog.LogItem

/** A [org.junit.rules.TestRule] that prints out logs after running a test. */
class LogPrinterRule(private val level: Int = Log.WARN, vararg includeTags: String) :
  ExternalResource() {
  private val includeTags = includeTags.toSet()

  override fun after() {
    ShadowLog.getLogs().filter { it.shouldLog() }.forEach { it.print() }
  }

  private fun LogItem.shouldLog() =
    type >= level && (includeTags.isEmpty() || includeTags.contains(tag))
}

private fun LogItem.print() {
  val stream = ByteArrayOutputStream()
  val indent = " ".repeat(36)
  print("[%s]: %-30s ".format(type.asLogLevel(), tag))
  println(msg.lines().joinToString(indent) { it })
  if (throwable != null) {
    PrintStream(stream).use { printStream -> throwable.printStackTrace(printStream) }
    println(stream.toString().trim().prependIndent(indent))
  }
}

private val LOG_LEVELS =
  mapOf(
    Log.VERBOSE to "V",
    Log.DEBUG to "D",
    Log.INFO to "I",
    Log.WARN to "W",
    Log.ERROR to "E",
    Log.ASSERT to "A",
  )

private fun Int.asLogLevel() = LOG_LEVELS.getValue(this)
