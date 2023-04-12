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
import com.sun.jdi.LocalVariable
import com.sun.jdi.StackFrame
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.computeKotlinStackFrameInfos

/**
 * A [FrameListener] that builds a string representation of the inline stack frames
 *
 * Inline stack frames are created by `StackFrame.computeKotlinStackFrameInfos()` in
 * `InlineStackTraceCalculator.kt`.
 */
internal class InlineStackFrameFrameListener : Engine.FrameListener {

  private val sb = StringBuilder()

  fun getText() = sb.toString()

  /**
   * When converting a KotlinStackFrameInfo to a XStackFrame, the following fields are used:
   * 1. displayName
   * 2. callLocation
   * 3. depth
   * 4. visibleVariables
   *
   * We emit these fields to text.
   *
   * Note that some of the data in the variables is not identical between JVM & DEX. The following
   * have been observed to be different:
   * 1. slot
   * 2. scope codeIndex
   * 3. scope lineNumber for spilled variables
   * 4. Variable order - derived from slot and scopeStart
   *
   * Items #1 & #2 are obviously implementation dependant, so we don't need to worry about them.
   *
   * Item #3 might be an issue TODO(aalbert): Investigate
   *
   * Item #4 Probably not an issue but we do need to address it so that we can perform a diff. We
   * handle this by sorting the variables by name.
   */
  override fun onFrame(frame: StackFrame) {
    sb.append("Breakpoint: ${frame.location().printToString()}\n")
    sb.append("========================================================\n")

    frame.computeKotlinStackFrameInfos().forEach { info ->
      when (info.displayName) {
        null -> sb.append("Not an Inline Stack Frame\n")
        else -> sb.append("Inline frame for function '${info.displayName}' Depth: ${info.depth}\n")
      }
      sb.append("Call location: ${info.callLocation?.printToString()}\n")
      sb.append("------------------------------------------------------\n")
      val sortedVariables = info.visibleVariables.sortedBy { it.name() }
      sortedVariables.forEach { sb.append(it.toSummaryLine()) }
      sb.append('\n')
    }
    sb.append('\n')
  }
}

private fun LocalVariable.toSummaryLine() = "  %-30s: %s\n".format(name(), typeName())
