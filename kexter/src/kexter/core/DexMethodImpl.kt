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

import kexter.DexMethod
import kexter.DexMethodDebugInfo

internal class CodeItem(
  val registerSize: UShort,
  val insSize: UShort,
  val outsSize: UShort,
  val triesSize: UShort,
  val debugInfoOffset: UInt,
  val sizeInstructions: UInt,
  val instructions: ByteArray,
) {

  companion object {
    fun from(reader: DexReader): CodeItem {
      val registerSize = reader.ushort()
      val insSize = reader.ushort()
      val outsSize = reader.ushort()
      val triesSize = reader.ushort()
      val debugInfoOffset = reader.uint()
      val sizeInstructions = reader.uint() * UShort.SIZE_BYTES.toUInt()
      val instructions = reader.bytes(sizeInstructions)
      // TODO
      // padding
      // tries
      // handlers
      return CodeItem(
        registerSize,
        insSize,
        outsSize,
        triesSize,
        debugInfoOffset,
        sizeInstructions,
        instructions,
      )
    }
  }
}

internal class DexMethodImpl(
  private val method: EncodedMethod,
  override val isDirect: Boolean,
  private val dex: DexImpl,
) : DexMethod {

  override val name: String by lazy(LazyThreadSafetyMode.NONE) { retrieveName() }

  override val byteCode by lazy(LazyThreadSafetyMode.NONE) { retrieveByteCode() }

  override val type: String by
    lazy(LazyThreadSafetyMode.NONE) { TypeIds.get(dex, methodId.classIndex.toUInt()) }

  override val isNative: Boolean
    get() = byteCode.instructions.size == 0

  override val shorty: String
    get() = dex.stringIds.get(protoId.shortyIndex)

  override val returnType: String
    get() = TypeIds.get(dex, protoId.returnTypeIndex)

  private val methodId by lazy(LazyThreadSafetyMode.NONE) { dex.methodIds.get(method.methodIndex) }
  private val protoId by lazy(LazyThreadSafetyMode.NONE) { dex.protoIds.get(methodId.protoIndex) }

  private fun retrieveName(): String {
    return dex.stringIds.get(methodId.nameIndex)
  }

  private fun retrieveByteCode(): DexBytecodeImpl {
    val bytecode =
      if (method.codeOffset == 0u) {
        // Native method don't have bytecode
        ByteArray(0)
      } else {
        dex.reader.position = method.codeOffset
        val codeItem = CodeItem.from(dex.reader)
        codeItem.instructions
      }

    // Debug info
    val debugInfo =
      if (bytecode.isEmpty()) {
        DexMethodDebugInfo()
      } else {
        retrieveDebugInfo()
      }
    return DexBytecodeImpl(bytecode, debugInfo, dex.logger)
  }

  private fun retrieveDebugInfo(): DexMethodDebugInfo {
    dex.reader.position = method.codeOffset
    val codeItem = CodeItem.from(dex.reader)
    dex.reader.position = codeItem.debugInfoOffset
    return kexter.core.DexMethodDebugInfo.fromReader(dex.reader, dex.logger)
  }
}
