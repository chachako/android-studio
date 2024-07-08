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

class OpcodeTest {

  private fun checkClassesHasOpcode(className: String, methodName: String, opcode: Opcode) {
    val instructions = DexUtils.getByteCode(className, methodName).instructions
    instructions.forEach {
      if (it.opcode == opcode) {
        return
      }
    }
    Assert.fail("Unable to find $opcode in class $className.$methodName")
  }

  @Test
  fun testAdders() {
    val className = "LAddClass;"
    checkClassesHasOpcode(className, "addInt(III)", Opcode.ADD_INT)
    checkClassesHasOpcode(className, "addLong(JJJ)", Opcode.ADD_LONG)
    checkClassesHasOpcode(className, "addFloat(FFF)", Opcode.ADD_FLOAT)
    checkClassesHasOpcode(className, "addDouble(DDD)", Opcode.ADD_DOUBLE)
  }

  @Test
  fun testReturn() {
    val className = "LReturnClass;"
    checkClassesHasOpcode(className, "returnVoid(V)", Opcode.RETURN_VOID)
    checkClassesHasOpcode(className, "returnPlain(I)", Opcode.RETURN)
    checkClassesHasOpcode(className, "returnWide(J)", Opcode.RETURN_WIDE)
    checkClassesHasOpcode(className, "returnObject(L)", Opcode.RETURN_OBJECT)
  }

  @Test
  fun testConst() {
    // TODO
  }

  // TODO, we need to test all opcodes (WIP while this CL is reviewed).
}
