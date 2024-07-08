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

internal data class ClassDef(
  val classIndex: UInt,
  val accessFlag: UInt,
  val superClassIndex: UInt,
  val interfaceOffset: UInt,
  val sourceFileIndex: UInt,
  val annotationOffset: UInt,
  val classDataOffset: UInt,
  val staticValues: UInt,
)

internal class ClassDefs(private val span: Span, private val dex: DexImpl) {

  fun numElements() = span.count

  fun getClassDef(index: UInt): ClassDef {
    dex.reader.position = span.offset + ENTRY_SIZE * index
    with(dex.reader) {
      return ClassDef(uint(), uint(), uint(), uint(), uint(), uint(), uint(), uint())
    }
  }

  private companion object {
    val ENTRY_SIZE = UInt.SIZE_BYTES.toUInt() * 8u
  }
}
