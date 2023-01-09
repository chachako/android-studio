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

class AllClassesWithGenericsReply(val classes: MutableList<Class>) {
  class Class(
    val refTypeTag: Byte,
    val referenceTypeID: Long,
    val signature: String,
    val genericSignature: String,
    val status: Int
  )

  companion object {

    @JvmStatic
    fun parse(reader: MessageReader): AllClassesWithGenericsReply {
      val classes = mutableListOf<Class>()
      val numClasses = reader.getInt()

      repeat(numClasses) {
        val refTypeTag = reader.getByte()
        val typeID = reader.getReferenceTypeID()
        val signature = reader.getString()
        val genericSignature = reader.getString()
        val status = reader.getInt()
        classes.add(Class(refTypeTag, typeID, signature, genericSignature, status))
      }
      return AllClassesWithGenericsReply(classes)
    }
  }
}
