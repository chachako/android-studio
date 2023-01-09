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

import java.nio.ByteBuffer

class MessageWriter(
  var fieldIDSize: Int = 8,
  var methodIDSize: Int = 8,
  var objectIDSize: Int = 8,
  var referenceTypeIDSize: Int = 8,
  var frameIDSize: Int = 8
) {

  val locationSize: Int
    get() = Byte.SIZE_BYTES + referenceTypeIDSize + methodIDSize + Long.SIZE_BYTES

  private var buffer: ByteBuffer = ByteBuffer.allocate(0)

  fun start(capacity: Int) {
    buffer = ByteBuffer.allocate(capacity)
  }

  fun finish(): ByteBuffer {
    // Prevent more putX until start is called again?
    buffer.clear()
    return buffer
  }

  fun putFrameID(value: Long) {
    putID(referenceTypeIDSize, value)
  }

  fun putByte(byte: Byte) {
    buffer.put(byte)
  }

  fun putInt(int: Int) {
    buffer.putInt(int)
  }

  fun putLong(long: Long) {
    buffer.putLong(long)
  }

  fun putTypeTag(typeTag: Byte) {
    putByte(typeTag)
  }

  fun putReferenceTypeID(ref: Long) {
    putID(referenceTypeIDSize, ref)
  }

  private fun putMethodID(methodID: Long) {
    putID(methodIDSize, methodID)
  }

  fun putLocation(location: Location) {
    putTypeTag(location.typeTag)
    putReferenceTypeID(location.classID)
    putMethodID(location.methodID)
    putLong(location.index)
  }

  private fun putID(size: Int, value: Long) {
    when (size) {
      1 -> buffer.put(value.toByte())
      2 -> buffer.putShort(value.toShort())
      4 -> buffer.putInt(value.toInt())
      8 -> buffer.putLong(value)
      else -> throw IllegalArgumentException("Unsupported id size: $size")
    }
  }
}
