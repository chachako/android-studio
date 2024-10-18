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

import kexter.DexMethodDebugInfo
import kexter.LineTableEntry
import kexter.Logger

object DexMethodDebugInfo {

  private const val DBG_END_SEQUENCE: UByte = 0x00u
  private const val DBG_ADVANCE_PC: UByte = 0x01u
  private const val DBG_ADVANCE_LINE: UByte = 0x02u
  private const val DBG_START_LOCAL: UByte = 0x03u
  private const val DBG_START_LOCAL_EXTENDED: UByte = 0x04u
  private const val DBG_END_LOCAL: UByte = 0x05u
  private const val DBG_RESTART_LOCAL: UByte = 0x06u
  private const val DBG_SET_PROLOGUE_END: UByte = 0x07u
  private const val DBG_SET_EPILOGUE_BEGIN: UByte = 0x08u
  private const val DBG_SET_FILE: UByte = 0x09u

  fun fromReader(reader: DexReader, logger: Logger): DexMethodDebugInfo {
    val lineNumbers: MutableList<LineTableEntry> = mutableListOf()
    var line = reader.uLeb128().toInt()
    val parameterSize = reader.uLeb128()

    // Read parameter names
    repeat(parameterSize.toInt()) { reader.uLeb128p1() }

    var bc = 0u
    // Read debug instructions
    var opcode = reader.ubyte()
    // TODO: Move to extension function once more stable (see ag/29937779 comments)
    while (opcode != DBG_END_SEQUENCE) {
      when (opcode) {
        DBG_ADVANCE_PC -> {
          val addrDiff = reader.uLeb128()
          bc += addrDiff
        }
        DBG_ADVANCE_LINE -> {
          val lineDiff = reader.sLeb128()
          line += lineDiff
        }
        DBG_START_LOCAL -> {
          val registerNum = reader.uLeb128()
          val nameIdx = reader.uLeb128p1()
          val typeIdx = reader.uLeb128p1()
        }
        DBG_START_LOCAL_EXTENDED -> {
          val registerNum = reader.uLeb128()
          val nameIdx = reader.uLeb128p1()
          val typeIdx = reader.uLeb128p1()
          val sigIdx = reader.uLeb128p1()
        }
        DBG_END_LOCAL -> {
          val registerNum = reader.uLeb128()
        }
        DBG_RESTART_LOCAL -> {
          val registerNum = reader.uLeb128()
        }
        DBG_SET_PROLOGUE_END -> {}
        DBG_SET_EPILOGUE_BEGIN -> {}
        DBG_SET_FILE -> {
          val nameIdx = reader.uLeb128()
        }
        else -> {
          // Special opcode (see https://source.android.com/docs/core/runtime/dex-format#opcodes)
          val DBG_FIRST_SPECIAL = 0x0A // the smallest special opcode
          val DBG_LINE_BASE = -4 // the smallest line number increment
          val DBG_LINE_RANGE = 15 // the number of line increments represented

          val adjusted_opcode = opcode.toInt() - DBG_FIRST_SPECIAL
          val lineInc = DBG_LINE_BASE + (adjusted_opcode % DBG_LINE_RANGE)
          val bcInc = adjusted_opcode / DBG_LINE_RANGE

          line += lineInc
          bc += bcInc.toUInt()

          lineNumbers.add(LineTableEntry(bc, line))
        }
      }
      opcode = reader.ubyte()
    }
    return DexMethodDebugInfo(lineNumbers)
  }
}
