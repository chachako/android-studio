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

import java.util.stream.Collectors
import kexter.DexBytecode
import kexter.DexMethodDebugInfo
import kexter.Instruction
import kexter.Logger
import kexter.Opcode

internal class DexBytecodeImpl(
  override val bytes: ByteArray,
  override val debugInfo: DexMethodDebugInfo,
  private val logger: Logger,
) : DexBytecode {
  override val instructions by lazy((LazyThreadSafetyMode.NONE)) { retrieveInstructions() }

  private fun retrieveInstructions(): List<Instruction> {
    val instrs = mutableListOf<Instruction>()
    val reader = DexReader(bytes)
    while (reader.position < bytes.size.toUInt()) {
      val index = reader.position / 2u // Dex bytecode index are in unit of 16-bit.
      val opcode = Opcode.fromUByte(reader.ubyte())
      var payloadSize = opcode.format.payloadSize
      if (opcode == Opcode.NOP) {
        // This could be a data-bearing pseudo-instructions, check opcode identity
        val pos = reader.position
        payloadSize += pseudoCodeSize(reader)
        reader.position = pos
      }
      instrs.add(Instruction(opcode, index, reader.bytes(payloadSize)))
    }
    return instrs
  }

  private val NOOP_IDENTITY = 0x00u
  private val PACKED_SWITCH_IDENTITY = 0x01u
  private val SPARSE_SWITCH_IDENTITY = 0x02u
  private val FILL_ARRAY_DATA_IDENTITY = 0x03u

  private fun pseudoCodeSize(reader: DexReader): UInt {
    val size =
      when (val identity = reader.ubyte().toUInt()) {
        NOOP_IDENTITY -> 0u
        PACKED_SWITCH_IDENTITY -> {
          val size = reader.ushort()
          // first_key is int, we don't need to read it
          UShort.SIZE_BYTES.toUInt() + Int.SIZE_BYTES.toUInt() + size * Int.SIZE_BYTES.toUInt()
        }
        SPARSE_SWITCH_IDENTITY -> {
          val size = reader.ushort()
          UShort.SIZE_BYTES.toUInt() +
            size * Int.SIZE_BYTES.toUInt() +
            size * Int.SIZE_BYTES.toUInt()
        }
        FILL_ARRAY_DATA_IDENTITY -> {
          val elementWidth = reader.ushort()
          val size = reader.uint()

          // Account for padding because dex instructions must be aligned.
          var totalSize =
            UShort.SIZE_BYTES.toUInt() + UInt.SIZE_BYTES.toUInt() + size * elementWidth
          if (totalSize.mod(2u) == 1u) {
            totalSize += 1u
          }
          return totalSize
        }
        else ->
          throw IllegalStateException(
            "Bad pseudo-code identity  (${ "0x%02x".format( identity.toInt())})"
          )
      }
    return size
  }

  override fun instructionsForLineNumber(lineNumber: Int): List<Instruction> {
    val lineTable = debugInfo.lineTable
    if (lineTable.isEmpty()) {
      throw IllegalStateException("Unable to extract instruction without DebugInfo")
    }

    val startIndex = lineTable.indexOfFirst { e -> e.lineNumber == lineNumber }

    if (startIndex == -1) {
      throw IllegalStateException("Line $lineNumber not found in TableLine")
    }
    val startBc = lineTable[startIndex].index

    val endIndex = startIndex + 1
    val endBc =
      if (endIndex < lineTable.size) {
        lineTable[endIndex].index
      } else {
        UInt.MAX_VALUE
      }
    return instructions
      .stream()
      .filter { e -> (startBc..<endBc).contains(e.index) }
      .collect(Collectors.toList())
  }
}
