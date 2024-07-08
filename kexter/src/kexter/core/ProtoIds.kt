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

data class ProtoId(val shortyIndex: UInt, val returnTypeIndex: UInt, val parameterOffset: UInt)

internal class ProtoIds(private val span: Span, private val dex: DexImpl) {

  private val cache: MutableMap<UShort, ProtoId> = mutableMapOf()

  fun get(index: UShort): ProtoId {
    return cache.computeIfAbsent(index) {
      if (index > span.count) {
        throw IllegalStateException("Unable to retrieve protoId $index (max=${span.count - 1u}")
      }
      dex.reader.position = span.offset + (index * ENTRY_SIZE)
      ProtoId(dex.reader.uint(), dex.reader.uint(), dex.reader.uint())
    }
  }

  companion object {
    val ENTRY_SIZE = UInt.SIZE_BYTES.toUInt() * 3u
  }
}
