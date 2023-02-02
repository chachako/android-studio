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
package com.android.jdwppacket.stackframe

import com.android.jdwppacket.Cmd
import com.android.jdwppacket.MessageReader
import com.android.jdwppacket.StackFrame
import com.android.jdwppacket.Writer

data class GetValuesCmd(val threadID: Long, val frameID: Long, val slots: List<Slot>) :
  Cmd(StackFrame.GetValues) {
  data class Slot(val slot: Int, val sibByte: Byte) {
    fun write(writer: Writer) {
      writer.putInt(slot)
      writer.putByte(sibByte)
    }
  }

  companion object {

    @JvmStatic
    fun parse(reader: MessageReader): GetValuesCmd {
      val threadID = reader.getThreadID()
      val frameID = reader.getFrameID()

      val slots = List(reader.getInt()) { Slot(reader.getInt(), reader.getByte()) }
      return GetValuesCmd(threadID, frameID, slots)
    }
  }

  override fun paramsKey(): String {
    throw IllegalStateException("Not keyable (slots can vary too much)")
  }

  override fun writePayload(writer: Writer) {
    writer.putThreadID(threadID)
    writer.putFrameID(frameID)
    writer.putInt(slots.size)
    slots.forEach { it.write(writer) }
  }
}
