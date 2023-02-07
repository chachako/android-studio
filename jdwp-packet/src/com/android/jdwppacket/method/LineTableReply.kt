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

data class LineTableReply(val start: Long, val end: Long, val lines: List<Line>) : Reply() {

  data class Line(val lineCodeIndex: Long, val lineNumber: Int) {
    fun writePayload(writer: Writer) {
      writer.putLong(lineCodeIndex)
      writer.putInt(lineNumber)
    }
  }

  override fun writePayload(writer: Writer) {
    writer.putLong(start)
    writer.putLong(end)
    writer.putInt(lines.size)
    lines.forEach { it.writePayload(writer) }
  }

  companion object {
    @JvmStatic
    fun parse(reader: MessageReader): LineTableReply {
      val start = reader.getLong()
      val end = reader.getLong()
      val lines = List(reader.getInt()) { Line(reader.getLong(), reader.getInt()) }
      return LineTableReply(start, end, lines)
    }
  }
}
