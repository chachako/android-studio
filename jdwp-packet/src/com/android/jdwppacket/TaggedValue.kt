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

data class TaggedValue(val tag: Byte, val value: Long)

fun Writer.putTaggedValue(taggedValue: TaggedValue) {
  putByte(taggedValue.tag)
  when (taggedValue.tag.toInt().toChar()) {
    '[' -> putObjectID(taggedValue.value)
    'B' -> putByte(taggedValue.value.toByte())
    'C' -> putShort(taggedValue.value.toShort())
    'L' -> putObjectID(taggedValue.value)
    'F' -> putInt(taggedValue.value.toInt())
    'D' -> putLong(taggedValue.value)
    'I' -> putInt(taggedValue.value.toInt())
    'J' -> putLong(taggedValue.value)
    'S' -> putShort(taggedValue.value.toShort())
    'V' -> {}
    'Z' -> putByte(taggedValue.value.toByte())
    's' -> putObjectID(taggedValue.value)
    't' -> putObjectID(taggedValue.value)
    'g' -> putObjectID(taggedValue.value)
    'l' -> putObjectID(taggedValue.value)
    'c' -> putObjectID(taggedValue.value)
    else -> {
      throw IllegalStateException("Unrecognized tag " + taggedValue.tag)
    }
  }
}

fun MessageReader.getTagValue(): TaggedValue {
  val tag = getByte()
  val value: Long =
    when (tag.toInt().toChar()) {
      '[' -> getObjectID()
      'B' -> getByte().toLong()
      'C' -> getShort().toLong()
      'L' -> getObjectID()
      'F' -> getInt().toLong()
      'D' -> getLong()
      'I' -> getInt().toLong()
      'J' -> getLong()
      'S' -> getShort().toLong()
      'V' -> {
        0
      }
      'Z' -> getByte().toLong()
      's' -> getObjectID()
      't' -> getObjectID()
      'g' -> getObjectID()
      'l' -> getObjectID()
      'c' -> getObjectID()
      else -> {
        0
      }
    }
  return TaggedValue(tag, value)
}
