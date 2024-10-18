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

import kexter.Opcode
import org.junit.Assert
import org.junit.Test

class ByteCodeTest {
  @Test
  fun testSimpleBytecode() {
    val bytecode = DexUtils.getByteCode("LByteCodeClass;", "invokeMath(II)")
    Assert.assertNotEquals(0, bytecode.instructions.size)

    var instr = bytecode.instructionsForLineNumber(20)
    Assert.assertEquals(Opcode.MOVE, instr[0].opcode)

    instr = bytecode.instructionsForLineNumber(25)
    Assert.assertEquals(1, instr.size)
    Assert.assertEquals(instr[0].opcode, Opcode.RETURN)
  }

  @Test
  fun testMultiInstructionPerLine() {
    val bytecode = DexUtils.getByteCode("LByteCodeClass;", "multiInstructionPerLine(II)")
    Assert.assertNotEquals(0, bytecode.instructions.size)

    var instr = bytecode.instructionsForLineNumber(30)
    Assert.assertEquals(4, instr.size)
    Assert.assertEquals(Opcode.ADD_INT, instr[0].opcode)
    Assert.assertEquals(Opcode.ADD_INT_2ADDR, instr[1].opcode)
    Assert.assertEquals(Opcode.ADD_INT_2ADDR, instr[2].opcode)
    Assert.assertEquals(Opcode.ADD_INT_2ADDR, instr[3].opcode)

    instr = bytecode.instructionsForLineNumber(31)
    Assert.assertEquals("", 1, instr.size)
    Assert.assertEquals("", instr[0].opcode, Opcode.RETURN)
  }
}
