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
package com.android.jdwppacket.vm

import com.android.jdwppacket.MessageReader
import com.android.jdwppacket.Reply
import com.android.jdwppacket.Writer

data class IDSizesReply(
  val fieldIDSize: Int,
  val methodIDSize: Int,
  val objectIDSize: Int,
  val referenceTypeIDSize: Int,
  val frameIDSize: Int
) : Reply() {

  companion object {

    @JvmStatic
    fun parse(reader: MessageReader): IDSizesReply {
      val fieldIDSize = reader.getInt()
      val methodIDSize = reader.getInt()
      val objectIDSize = reader.getInt()
      val referenceTypeIDSize = reader.getInt()
      val frameIDSize = reader.getInt()
      return IDSizesReply(fieldIDSize, methodIDSize, objectIDSize, referenceTypeIDSize, frameIDSize)
    }
  }

  override fun writePayload(writer: Writer) {
    writer.putInt(fieldIDSize)
    writer.putInt(methodIDSize)
    writer.putInt(objectIDSize)
    writer.putInt(referenceTypeIDSize)
    writer.putInt(frameIDSize)
  }
}
