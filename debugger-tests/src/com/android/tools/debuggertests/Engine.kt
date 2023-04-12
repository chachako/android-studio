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

import com.jetbrains.jdi.LocalVariableImpl
import com.sun.jdi.LocalVariable
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.Event

private const val BREAKPOINT_CLASS = "BreakpointKt"
private const val BREAKPOINT_LINE = 20

/** A simple test engine */
internal abstract class Engine(val vmName: String) {

  protected abstract suspend fun startDebugger(mainClass: String): Debugger

  protected open fun onBreakpointAdded() {}

  /**
   * Executes a single test.
   * 1. Starts a [DebuggerImpl]
   * 2. Sets a breakpoint at Breakpoint.breakpoint()
   * 3. Resumes the program each time it hits a breakpoint
   * 4. On each breakpoint, emits information about the frame into a string
   */
  suspend fun runTest(mainClass: String): String {
    return startDebugger(mainClass).use { debugger ->
      debugger.setBreakpoint(BREAKPOINT_CLASS, BREAKPOINT_LINE)
      onBreakpointAdded()
      buildString {
        while (true) {
          val breakpoint = debugger.resume(Event::class.java) as? BreakpointEvent ?: break
          val frame = breakpoint.thread().frames()[1]
          val location = frame.location()
          append(
            "Breakpoint: ${location.sourceName()}.${location.method()}:${location.lineNumber()}\n"
          )
          append("========================================================\n")
          frame
            .visibleVariables()
            .map { it as LocalVariableImpl }
            .forEach { variable ->
              val codeScope =
                "[%d-%d]".format(variable.scopeStart.codeIndex(), variable.scopeEnd.codeIndex())
              val lineScope =
                "[%d-%d]".format(variable.scopeStart.lineNumber(), variable.scopeEnd.lineNumber())
              val line =
                "%-2d %-10s %-10s: %-30s: %s\n".format(
                  variable.getSlot(),
                  lineScope,
                  codeScope,
                  variable.name(),
                  variable.typeName()
                )
              append(line)
            }
          append('\n')
        }
      }
    }
  }
}

private fun LocalVariable.getSlot(): Int = getFieldValue("slot")

private inline fun <reified T> Any.getFieldValue(name: String): T {
  val field = javaClass.getDeclaredField(name).apply { isAccessible = true }
  return field.get(this) as T
}
