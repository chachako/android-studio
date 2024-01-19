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
package com.android.jdwppacket.eventrequest

import com.android.jdwppacket.EventKind
import com.android.jdwppacket.Location
import com.android.jdwppacket.MessageReader
import com.android.jdwppacket.ModKind

data class SetCmd(val kind: EventKind, val suspendPolicy: Byte, val modifiers: List<Modifier>) {

  open class Modifier(val kind: ModKind)

  class ModifierCount(val count: Int) : Modifier(ModKind.COUNT)

  class ModifierConditional(val exprID: Int) : Modifier(ModKind.CONDITIONAL)

  class ModifierThreadOnly(val threadID: Long) : Modifier(ModKind.THREAD_ONLY)

  class ModifierClassOnly(val referenceTypeID: Long) : Modifier(ModKind.CLASS_ONLY)

  class ModifierClassMatch(val pattern: String) : Modifier(ModKind.CLASS_MATCH)

  class ModifierClassExclude(val pattern: String) : Modifier(ModKind.CLASS_EXCLUDE)

  class ModifierLocationOnly(val location: Location) : Modifier(ModKind.LOCATION_ONLY)

  class ModifierExceptionOnly(
    val exceptionOrNull: Long,
    val caught: Boolean,
    val uncaught: Boolean,
  ) : Modifier(ModKind.EXCEPTION_ONLY)

  class ModifierFieldOnly(val declaring: Long, val fieldID: Long) : Modifier(ModKind.FIELD_ONLY)

  class ModifierStep(val threadID: Long, val size: Int, val depth: Int) : Modifier(ModKind.STEP)

  class ModifierInstanceOnly(val instance: Long) : Modifier(ModKind.INSTANCE_ONLY)

  class ModifierSourceNameMatch(val sourceNameMatch: String) : Modifier(ModKind.SOURCE_NAME_MATCH)

  companion object {

    @JvmStatic
    fun parse(reader: MessageReader): SetCmd {
      val eventKind = EventKind.fromID(reader.getByte().toInt())
      val suspendPolicy = reader.getByte()

      val numModifiers = reader.getInt()
      val modifiers = mutableListOf<Modifier>()

      repeat(numModifiers) {
        val kind = ModKind.fromID(reader.getByte().toInt())
        val modifier =
          when (kind) {
            ModKind.COUNT -> ModifierCount(reader.getInt())
            ModKind.CONDITIONAL -> ModifierConditional(reader.getInt())
            ModKind.THREAD_ONLY -> ModifierThreadOnly(reader.getThreadID())
            ModKind.CLASS_ONLY -> ModifierClassOnly(reader.getReferenceTypeID())
            ModKind.CLASS_MATCH -> ModifierClassMatch(reader.getString())
            ModKind.CLASS_EXCLUDE -> ModifierClassExclude(reader.getString())
            ModKind.LOCATION_ONLY -> ModifierLocationOnly(reader.getLocation())
            ModKind.EXCEPTION_ONLY ->
              ModifierExceptionOnly(
                reader.getReferenceTypeID(),
                reader.getBoolean(),
                reader.getBoolean(),
              )
            ModKind.FIELD_ONLY ->
              ModifierFieldOnly(reader.getReferenceTypeID(), reader.getFieldID())
            ModKind.STEP -> ModifierStep(reader.getThreadID(), reader.getInt(), reader.getInt())
            ModKind.INSTANCE_ONLY -> ModifierInstanceOnly(reader.getObjectID())
            ModKind.SOURCE_NAME_MATCH -> ModifierSourceNameMatch(reader.getString())
          }
        modifiers.add(modifier)
      }

      return SetCmd(eventKind, suspendPolicy, modifiers)
    }
  }
}
