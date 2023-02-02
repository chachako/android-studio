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

data class ClassesBySignatureReply(val classes: List<Class>) : Reply() {

  data class Class(val refTypeTag: Byte, val referenceTypeID: Long, val status: Int) {
    fun write(writer: Writer) {
      writer.putByte(refTypeTag)
      writer.putReferenceTypeID(referenceTypeID)
      writer.putInt(status)
    }
  }

  companion object {

    @JvmStatic
    fun parse(reader: MessageReader): ClassesBySignatureReply {
      val classes = mutableListOf<Class>()
      val numClasses = reader.getInt()

      repeat(numClasses) {
        val refTypeTag = reader.getByte()
        val typeID = reader.getReferenceTypeID()
        val status = reader.getInt()
        classes.add(Class(refTypeTag, typeID, status))
      }
      return ClassesBySignatureReply(classes)
    }
  }

  override fun writePayload(writer: Writer) {
    writer.putInt(classes.size)
    classes.forEach { it.write(writer) }
  }
}
