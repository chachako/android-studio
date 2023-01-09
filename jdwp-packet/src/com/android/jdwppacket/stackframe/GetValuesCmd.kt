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

import com.android.jdwppacket.MessageReader

class GetValuesCmd(val threadID: Long, val frameID: Long, val slots: List<Slot>) {
  class Slot(val slot: Int, val sibByte: Byte)

  companion object {

    @JvmStatic
    fun parse(reader: MessageReader): GetValuesCmd {

      val threadID = reader.getThreadID()
      val frameID = reader.getFrameID()
      val numSlots = reader.getInt()

      val slots = mutableListOf<Slot>()
      repeat(numSlots) {
        val slot: Int = reader.getInt()
        val sigbyte: Byte = reader.getByte()
        slots.add(Slot(slot, sigbyte))
      }

      return GetValuesCmd(threadID, frameID, slots)
    }
  }
}
