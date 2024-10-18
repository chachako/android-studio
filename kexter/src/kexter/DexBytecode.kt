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

/**
 * A representation of ART bytecode, typically found in classesXX.dex files inside an apk, or
 * directly returned by JDWP Method.Bytecodes command, see more details here:
 * https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp/jdwp-protocol.html#JDWP_Method_Bytecodes
 */
interface DexBytecode {

  /** The raw bytes of a method bytecode */
  val bytes: ByteArray

  /** An interpreted version of the raw bytes. Much easier to navigate than raw bytes. */
  val instructions: List<Instruction>

  fun instructionsForLineNumber(lineNumber: Int): List<Instruction>

  val debugInfo: DexMethodDebugInfo

  companion object {
    fun fromBytes(
      bytes: ByteArray,
      debugInfo: DexMethodDebugInfo = DexMethodDebugInfo(),
      logger: Logger = Logger(),
    ): DexBytecode {
      return kexter.core.DexBytecodeImpl(bytes, debugInfo, logger)
    }
  }
}
