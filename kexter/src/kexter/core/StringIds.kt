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

internal class StringIds(private val span: Span, private val dex: DexImpl) {

  private val cache: MutableMap<UInt, String> = mutableMapOf()

  fun get(index: UInt): String {
    return cache.computeIfAbsent(index) {
      dex.reader.position = span.offset + (index * ENTRY_SIZE)
      val dataOffset = dex.reader.uint()
      dex.reader.position = dataOffset

      val size = dex.reader.uLeb128()
      val bytes = dex.reader.bytes(size)
      bytes.toString(Charsets.UTF_8)
    }
  }

  companion object {
    val ENTRY_SIZE = UInt.SIZE_BYTES.toUInt()
  }
}
