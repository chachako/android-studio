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

internal data class EncodedField(val fieldIndex: UInt, val accessFlags: UInt) {

  companion object {
    fun fromReader(reader: DexReader, previousFieldIndex: UInt): EncodedField {
      return EncodedField(reader.uLeb128() + previousFieldIndex, reader.uLeb128())
    }
  }
}

internal data class EncodedMethod(
  val methodIndex: UInt,
  val accessFlags: UInt,
  val codeOffset: UInt,
) {

  companion object {
    fun fromReader(reader: DexReader, previousMethodIndex: UInt): EncodedMethod {
      val methodIndex = reader.uLeb128() + previousMethodIndex
      val accessFlags = reader.uLeb128()
      val codeOffset = reader.uLeb128()
      return EncodedMethod(methodIndex, accessFlags, codeOffset)
    }
  }
}

internal class ClassData(
  val staticFields: List<EncodedField>,
  val instanceFields: List<EncodedField>,
  val directMethods: List<EncodedMethod>,
  val virtualMethods: List<EncodedMethod>,
) {

  companion object {
    fun fromReader(reader: DexReader): ClassData {
      val numStaticField = reader.uLeb128()
      val numInstanceField = reader.uLeb128()
      val numDirectMethod = reader.uLeb128()
      val numVirtualMethod = reader.uLeb128()

      val staticField = readFields(reader, numStaticField)
      val instanceField = readFields(reader, numInstanceField)
      val directMethods = readMethods(reader, numDirectMethod)
      val virtualMethods = readMethods(reader, numVirtualMethod)

      return ClassData(staticField, instanceField, directMethods, virtualMethods)
    }

    private fun readFields(reader: DexReader, numFields: UInt): List<EncodedField> {
      val fields = mutableListOf<EncodedField>()
      var previousIndex = 0u
      for (i in 0u..<numFields) {
        val field = EncodedField.fromReader(reader, previousIndex)
        fields.add(field)
        previousIndex = field.fieldIndex
      }
      return fields
    }

    private fun readMethods(reader: DexReader, numMethods: UInt): List<EncodedMethod> {
      val methods = mutableListOf<EncodedMethod>()
      var previousIndex = 0u
      for (i in 0u..<numMethods) {
        val method = EncodedMethod.fromReader(reader, previousIndex)
        methods.add(method)
        previousIndex = method.methodIndex
      }
      return methods
    }
  }
}
