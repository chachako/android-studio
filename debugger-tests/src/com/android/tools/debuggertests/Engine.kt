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

import com.sun.jdi.StackFrame
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.Event

private const val BREAKPOINT_CLASS = "BreakpointKt"
private const val BREAKPOINT_LINE = 20

/** A simple test engine */
internal abstract class Engine(val vmName: String) {

  protected abstract suspend fun startDebugger(mainClass: String): Debugger

  /**
   * Executes a single test.
   * 1. Starts a [DebuggerImpl]
   * 2. Sets a breakpoint at Breakpoint.breakpoint()
   * 3. Resumes the program each time it hits a breakpoint
   * 4. On each breakpoint, calls provided callback with a [StackFrame]
   *
   * Note that we can't just return a list of frames because some of the methods in StackFrame
   * require a live VM behind the scene.
   */
  suspend fun runTest(mainClass: String, listener: FrameListener) {
    return startDebugger(mainClass).use { debugger ->
      debugger.setBreakpoint(BREAKPOINT_CLASS, BREAKPOINT_LINE)
      buildString {
        while (true) {
          val breakpoint = debugger.resume(Event::class.java) as? BreakpointEvent ?: break
          listener.onFrame(breakpoint.thread().frames()[1])
        }
      }
    }
  }

  fun interface FrameListener {

    fun onFrame(frame: StackFrame)
  }
}
