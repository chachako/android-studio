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

class MessageWriter(idSizes: IDSizes, size: Int) : Writer(idSizes) {

  private var buffer: ByteBuffer = ByteBuffer.allocate(size)

  fun start(capacity: Int) {
    buffer = ByteBuffer.allocate(capacity)
  }

  fun finish(): ByteBuffer {
    buffer.clear()
    return buffer
  }

  override fun putByte(byte: Byte) {
    buffer.put(byte)
  }

  override fun putInt(int: Int) {
    buffer.putInt(int)
  }

  override fun putShort(short: Short) {
    buffer.putShort(short)
  }

  override fun putLong(long: Long) {
    buffer.putLong(long)
  }

  override fun putID(size: Int, value: Long) {
    when (size) {
      1 -> buffer.put(value.toByte())
      2 -> buffer.putShort(value.toShort())
      4 -> buffer.putInt(value.toInt())
      8 -> buffer.putLong(value)
      else -> throw IllegalArgumentException("Unsupported id size: $size")
    }
  }

  override fun putString(s: String) {
    val array = s.toByteArray()
    buffer.putInt(s.length)
    buffer.put(array)
  }
}
