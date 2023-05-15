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

// JDWP packet have fixed size data types such as int (4  bytes), byte (1 byte), and even variable
// length like "string" (4 bytes length + UTF-8 payload). However some data types sizes are specific
// to a VM.
//
// Components fieldID, methodID, objectID, referenceTypeID, and frameID size are retrieved
// at runtime with a command VM.IDSizes which this class holds. All messages (packet payload)
// parsing
// MUST be done through this class.

package com.android.jdwppacket

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class MessageReader(val idSizes: IDSizes, val buffer: ByteBuffer) {

  fun duplicate(): MessageReader {
    return MessageReader(idSizes, buffer.duplicate())
  }

  fun getByte() = buffer.get()

  fun getTypeTag() = getByte()

  fun getBoolean(): Boolean {
    return buffer.get() != 0.toByte()
  }

  fun getShort() = buffer.getShort()

  fun getInt() = buffer.getInt()

  fun getLong() = buffer.getLong()

  fun getLocation(): Location {
    return Location.parse(this)
  }

  // tag
  fun getTagValue() {
    when (getByte().toInt().toChar()) { // tag
      '[' -> getObjectID()
      'B' -> getByte()
      'C' -> getShort()
      'L' -> getObjectID()
      'F' -> getInt()
      'D' -> getLong()
      'I' -> getInt()
      'J' -> getLong()
      'S' -> getShort()
      'V' -> {}
      'Z' -> getByte()
      's' -> getObjectID()
      't' -> getObjectID()
      'g' -> getObjectID()
      'l' -> getObjectID()
      'c' -> getObjectID()
    }
  }

  fun getFieldID() = getID(idSizes.fieldIDSize)

  fun getMethodID() = getID(idSizes.methodIDSize)

  fun getClassID() = getReferenceTypeID()

  fun getObjectID() = getID(idSizes.objectIDSize)

  fun getThreadID() = getObjectID()

  fun getTaggedObjectID() {
    getObjectID()
    getByte()
  }

  fun getReferenceTypeID() = getID(idSizes.referenceTypeIDSize)

  fun getFrameID() = getID(idSizes.frameIDSize)

  fun getString(): String {
    val bytes = ByteArray(getInt())
    buffer.get(bytes)
    return String(bytes, StandardCharsets.UTF_8)
  }

  private fun getID(size: Int): Long {
    return when (size) {
      1 -> buffer.get().toLong()
      2 -> buffer.getShort().toLong()
      4 -> buffer.getInt().toLong()
      8 -> buffer.getLong()
      else -> throw IllegalArgumentException("Unsupported id size: $size")
    }
  }

  fun remaining() = buffer.remaining()

  fun hasRemaining() = buffer.hasRemaining()

  fun getCharString(len: Int) = String(CharArray(len) { buffer.getChar() })
}
