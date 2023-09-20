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

// Authoring JDPW packets uses a Writer abstract class
abstract class Writer(val s: IDSizes) {

  val intSize = Integer.BYTES
  val byteSize = Byte.SIZE_BYTES
  val longSize = Long.SIZE_BYTES
  val locationSize: Int = byteSize + s.referenceTypeIDSize + s.methodIDSize + longSize
  val threadIDSize = s.objectIDSize
  val classIDSize = s.referenceTypeIDSize

  abstract fun putByte(byte: Byte)

  abstract fun putInt(int: Int)

  abstract fun putShort(short: Short)

  abstract fun putLong(long: Long)

  abstract fun putID(size: Int, value: Long)

  abstract fun putString(s: String)

  fun putFrameID(value: Long) = putID(s.frameIDSize, value)

  fun putClassID(classID: Long) = putReferenceTypeID(classID)

  fun putTypeTag(typeTag: Byte) = putByte(typeTag)

  fun putReferenceTypeID(ref: Long) = putID(s.referenceTypeIDSize, ref)

  fun putMethodID(methodID: Long) = putID(s.methodIDSize, methodID)

  fun putObjectID(id: Long) = putID(s.objectIDSize, id)

  fun putThreadID(id: Long) = putObjectID(id)

  fun putTaggedObjectID(id: TaggedObjectID) {
    putByte(id.tag)
    putObjectID(id.objectID)
  }

  fun putLocation(location: Location): Int {
    location.write(this)
    return locationSize
  }

  fun putBoolean(boolean: Boolean) {
    if (boolean) {
      putByte(1)
    } else {
      putByte(0)
    }
  }
}
