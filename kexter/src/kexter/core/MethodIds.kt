/*
 * Copyright (C) 2024 The Android Open Source Project
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

package kexter.core

internal class MethodId(reader: DexReader) {
  val classIndex = reader.ushort()
  val protoIndex = reader.ushort()
  val nameIndex = reader.uint()
}

internal class MethodIds(private val span: Span, private val dex: DexImpl) {

  private val cache: MutableMap<UInt, MethodId> = mutableMapOf()

  fun get(index: UInt): MethodId {
    return cache.computeIfAbsent(index) {
      if (index > span.count) {
        throw IllegalStateException("Invalid methodId index ($index), max=${span.count-1u}")
      }
      dex.reader.position = span.offset + index * ENTRY_SIZE
      MethodId(dex.reader)
    }
  }

  private companion object {
    val ENTRY_SIZE =
      UShort.SIZE_BYTES.toUInt() + UShort.SIZE_BYTES.toUInt() + UInt.SIZE_BYTES.toUInt()
  }
}
