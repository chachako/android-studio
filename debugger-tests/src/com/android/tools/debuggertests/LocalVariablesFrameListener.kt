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
import com.jetbrains.jdi.ClassTypeImpl
import com.jetbrains.jdi.LocalVariableImpl
import com.sun.jdi.Field
import com.sun.jdi.LocalVariable
import com.sun.jdi.StackFrame

/** A [FrameListener] that builds a string representation of the frames local variables */
internal class LocalVariablesFrameListener : FrameListener {

  private val sb = StringBuilder()

  fun getText() = sb.toString()

  override fun onFrame(frame: StackFrame) {
    sb.append("Breakpoint: ${frame.location().printToString()}\n")

    val thisObject = frame.thisObject()
    if (thisObject != null) {
      sb.append("====== This Object ======================================================\n")
      val type = thisObject.referenceType() as ClassTypeImpl
      val interfaces = type.interfaces().map { it.name() }
      if (interfaces.isNotEmpty()) {
        sb.append("Interfaces:\n")
        interfaces.forEach { sb.append("  $it\n") }
      }
      val superclass = type.superclass()?.name()
      if (superclass != "java.lang.Object") {
        sb.append("Superclass:\n")
        sb.append("  $superclass\n")
      }

      val fields = type.fields()
      if (fields.isNotEmpty()) {
        sb.append("Fields:\n")
        fields.forEach { sb.append("  %-10s: %s\n".format(it.name(), it.typeName())) }
      }
    }

    sb.append("====== Local Variables ==================================================\n")
    frame
      .visibleVariables()
      .map { it as LocalVariableImpl }
      .forEach { sb.append(it.toSummaryLine()) }
    sb.append('\n')
  }
}

private fun Field.toSummaryLine() = "%-10s: %s\n".format(name(), typeName())

private fun LocalVariableImpl.toSummaryLine() =
  "%-2d %s: %-30s: %s\n".format(getSlot(), getScopes(), name(), typeName())

private fun LocalVariableImpl.getScopes(): String {
  val scopeStart = scopeStart
  val scopeEnd = scopeEnd
  val lineScope = "[%d-%d]".format(scopeStart.lineNumber(), scopeEnd.lineNumber())
  val codeScope = "[%d-%d]".format(scopeStart.codeIndex(), scopeEnd.codeIndex())
  return "%-10s %-10s".format(lineScope, codeScope)
}

private fun LocalVariable.getSlot(): Int = getFieldValue("slot")

private inline fun <reified T> Any.getFieldValue(name: String): T {
  val field = javaClass.getDeclaredField(name).apply { isAccessible = true }
  return field.get(this) as T
}
