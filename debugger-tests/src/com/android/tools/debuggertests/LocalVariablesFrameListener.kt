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

import com.android.tools.debuggertests.Engine.FrameListener
import com.jetbrains.jdi.LocalVariableImpl
import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import com.sun.jdi.StackFrame

/** A [FrameListener] that builds a string representation of the frames local variables */
internal class LocalVariablesFrameListener : FrameListener {

  private val sb = StringBuilder()

  fun getText() = sb.toString()

  override fun onFrame(frame: StackFrame) {
    val location = frame.location()
    sb.append("Breakpoint: ${location.printToString()}\n")
    sb.append("========================================================\n")
    frame
      .visibleVariables()
      .map { it as LocalVariableImpl }
      .forEach { variable ->
        val scopeStart = variable.scopeStart
        val scopeEnd = variable.scopeEnd
        val lineScope = "[%d-%d]".format(scopeStart.lineNumber(), scopeEnd.lineNumber())
        val codeScope = "[%d-%d]".format(scopeStart.codeIndex(), scopeEnd.codeIndex())
        val line =
          "%-2d %-10s %-10s: %-30s: %s\n".format(
            variable.getSlot(),
            lineScope,
            codeScope,
            variable.name(),
            variable.typeName()
          )
        sb.append(line)
      }
    sb.append('\n')
  }
}

private fun Location.printToString() = "${sourceName()}:${lineNumber()} - ${method()}"

private fun LocalVariable.getSlot(): Int = getFieldValue("slot")

private inline fun <reified T> Any.getFieldValue(name: String): T {
  val field = javaClass.getDeclaredField(name).apply { isAccessible = true }
  return field.get(this) as T
}
