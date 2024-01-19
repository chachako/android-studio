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
package com.android.jdwppacket

data class VariableTableWithGenericReply(val argCnt: Int, val slots: List<Slot>) : Reply() {

  data class Slot(
    val codeIndex: Long,
    val name: String,
    val signature: String,
    val genericSignature: String,
    val length: Int,
    val slot: Int,
  ) {
    fun write(writer: Writer) {
      writer.putLong(codeIndex)
      writer.putString(name)
      writer.putString(signature)
      writer.putString(genericSignature)
      writer.putInt(length)
      writer.putInt(slot)
    }
  }

  override fun writePayload(writer: Writer) {
    writer.putInt(argCnt)
    writer.putInt(slots.size)
    slots.forEach { it.write(writer) }
  }

  companion object {
    @JvmStatic
    fun parse(reader: MessageReader): VariableTableWithGenericReply {
      val argCnt = reader.getInt()
      val numSlots = reader.getInt()
      val slots =
        List(numSlots) {
          Slot(
            reader.getLong(),
            reader.getString(),
            reader.getString(),
            reader.getString(),
            reader.getInt(),
            reader.getInt(),
          )
        }
      return VariableTableWithGenericReply(argCnt, slots)
    }
  }
}
