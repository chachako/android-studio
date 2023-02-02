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

class MessageWriterCounter(idSizes: IDSizes) : Writer(idSizes) {

  var bytesCounted = 0

  override fun putByte(byte: Byte) {
    bytesCounted += 1
  }

  override fun putInt(int: Int) {
    bytesCounted += 4
  }

  override fun putShort(short: Short) {
    bytesCounted += 2
  }

  override fun putLong(long: Long) {
    bytesCounted += 8
  }

  override fun putID(size: Int, value: Long) {
    bytesCounted += size
  }

  override fun putString(s: String) {
    bytesCounted += Integer.BYTES + s.toByteArray().size
  }
}
