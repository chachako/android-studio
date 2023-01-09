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
package com.android.jdwppacket.threadreference

import com.android.jdwppacket.Location
import com.android.jdwppacket.MessageReader
import com.android.jdwppacket.MessageWriter
import com.android.jdwppacket.Writable

class FramesReply(val frames: List<Frame>) : Writable() {

  class Frame(val id: Long, val location: Location)

  override fun writeTo(writer: MessageWriter) {
    writer.putInt(frames.size)
    for (frame in frames) {
      writer.putFrameID(frame.id)
      writer.putLocation(frame.location)
    }
  }

  override fun serializedSize(writer: MessageWriter): Int {
    return Int.SIZE_BYTES + frames.size * (writer.referenceTypeIDSize + writer.locationSize)
  }

  companion object {

    @JvmStatic
    fun parse(reader: MessageReader): FramesReply {
      val num = reader.getInt()
      val frames = mutableListOf<Frame>()

      repeat(num) {
        val frameID = reader.getFrameID()
        val location = reader.getLocation()
        frames.add(Frame(frameID, location))
      }
      return FramesReply(frames)
    }
  }
}
