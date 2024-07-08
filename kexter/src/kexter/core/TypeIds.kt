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

internal class TypeIds {
  companion object {
    private val cache: MutableMap<UInt, String> = mutableMapOf()

    fun get(dex: DexImpl, index: UInt): String {
      return cache.computeIfAbsent(index) {
        if (index > dex.header.typeIds.count) {
          dex.logger.error("Bad typeId index $index (max = ${dex.header.typeIds.count}")
        }
        dex.reader.position = dex.header.typeIds.offset + index * 4u
        val descriptorIndex = dex.reader.uint()
        dex.stringIds.get(descriptorIndex)
      }
    }
  }
}
