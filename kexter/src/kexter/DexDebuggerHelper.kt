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

package kexter

fun Opcode.isGetter(): Boolean {
  return this == Opcode.IGET ||
    this == Opcode.IGET_WIDE ||
    this == Opcode.IGET_OBJECT ||
    this == Opcode.IGET_BOOLEAN ||
    this == Opcode.IGET_BOOLEAN_QUICK ||
    this == Opcode.IGET_BYTE ||
    this == Opcode.IGET_BYTE_QUICK ||
    this == Opcode.IGET_CHAR ||
    this == Opcode.IGET_CHAR_QUICK ||
    this == Opcode.IGET_SHORT ||
    this == Opcode.IGET_SHORT_QUICK ||
    this == Opcode.AGET ||
    this == Opcode.AGET_WIDE ||
    this == Opcode.AGET_OBJECT ||
    this == Opcode.AGET_BOOLEAN ||
    this == Opcode.AGET_BYTE ||
    this == Opcode.AGET_CHAR ||
    this == Opcode.AGET_SHORT
}

fun Opcode.isReturnValue(): Boolean {
  return this == Opcode.RETURN || this == Opcode.RETURN_WIDE || this == Opcode.RETURN_OBJECT
}

fun Opcode.isStaticGetter(): Boolean {
  return this == Opcode.SGET ||
    this == Opcode.SGET_WIDE ||
    this == Opcode.SGET_OBJECT ||
    this == Opcode.SGET_BOOLEAN ||
    this == Opcode.SGET_BYTE ||
    this == Opcode.SGET_CHAR ||
    this == Opcode.SGET_SHORT
}

class DexDebuggerHelper(bytes: ByteArray) {

  private val instructions: List<Instruction> = DexBytecode.fromBytes(bytes).instructions

  private fun isSimpleMemberVariableGetter(): Boolean {
    if (instructions.size != 2) {
      return false
    }

    return instructions[0].opcode.isGetter() && instructions[1].opcode.isReturnValue()
  }

  private fun isSimpleStaticVariableGetter(): Boolean {
    if (instructions.size != 2) {
      return false
    }

    return instructions[0].opcode.isStaticGetter() && instructions[1].opcode.isReturnValue()
  }

  private fun isJVMStaticVariableGetter(): Boolean {
    // TODO
    return false
  }

  fun isSimpleGetter(): Boolean {
    return isSimpleMemberVariableGetter() || isSimpleStaticVariableGetter()
    // || isJVMStaticVariableGetter()
  }

  fun hasStaticInvocations(): Boolean {
    instructions.forEach { instr ->
      if (instr.opcode == Opcode.INVOKE_STATIC || instr.opcode == Opcode.INVOKE_STATIC_RANGE) {
        return true
      }
    }
    return false
  }
}
