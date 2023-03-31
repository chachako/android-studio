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
package com.android.tools.debuggertests.engine

import com.google.common.truth.Truth.assertThat
import com.sun.jdi.LocalVariable
import com.sun.jdi.StringReference
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.Event
import java.io.FileNotFoundException
import java.io.InputStreamReader
import junit.framework.TestCase.fail

private const val BREAKPOINT_CLASS = "infra.BreakpointKt"
private const val BREAKPOINT_LINE = 25

/**
 * Runs a breakpoint test for a specified main class.
 *
 * The test executes a JVM program, sets a breakpoint in the `breakpoint()` function and then runs
 * until the program terminates.
 *
 * Each time the debugger breaks, we extract the expected [FrameInfo] from the current frame and
 * compare to the actual FrameInfo from the call site frame.
 *
 * @param mainClass the main class to attach to
 * @param numberOfBreakpoints the number of breakpoints we expect to see
 */
internal suspend fun runBreakpointTest(mainClass: String, numberOfBreakpoints: Int) {
  val debugger = Debugger(mainClass, System.getProperty("java.class.path"))
  debugger.setBreakpoint(BREAKPOINT_CLASS, BREAKPOINT_LINE)
  var tested = 0
  while (true) {
    val breakpoint = debugger.resume<Event>() as? BreakpointEvent ?: break
    breakpoint.assertFrame()
    tested++
  }
  assertThat(tested).named("Number of breakpoints").isEqualTo(numberOfBreakpoints)
}

private fun BreakpointEvent?.assertFrame() {
  if (this == null) {
    fail("Expected to not be null")
    return
  }
  assertThat(getActualFrameInfo())
    .named("${describeLocation()} (${getExpectedFileResourcePath()})")
    .isEqualTo(getExpectedFrameInfo())
}

private fun BreakpointEvent.getActualFrameInfo(): String {
  val frame = thread().frames()[1]
  val variables = frame.visibleVariables().map { VariableInfo(it.name(), it.slot()) }
  return FrameInfo(variables).toString()
}

private fun BreakpointEvent.getExpectedFrameInfo(): String {
  val resourcePath = getExpectedFileResourcePath()
  val stream =
    javaClass.classLoader.getResourceAsStream(resourcePath)
      ?: throw FileNotFoundException("at ${describeLocation()}: $resourcePath")
  return InputStreamReader(stream).use { it.readText().trim() }
}

private fun BreakpointEvent.getExpectedFileResourcePath(): String {
  val filename = (thread().frame(0).argumentValues.first() as StringReference).value()
  val sourcePath = thread().frame(1).location().sourcePath().replace('\\', '/')
  return "golden/$sourcePath/$filename"
}

private fun BreakpointEvent.describeLocation(): String {
  val location = thread().frame(1).location()
  val className = location.sourcePath().replace('/', '.')
  val methodName = location.method().name()
  return "$className.$methodName(${location.sourceName()}:${location.lineNumber()})"
}

private fun LocalVariable.slot(): Int {
  val method = javaClass.getDeclaredMethod("slot") ?: return -1
  method.isAccessible = true
  return method.invoke(this) as Int
}
