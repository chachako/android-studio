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

import kexter.DexClass
import kexter.DexField
import kexter.DexMethod

internal class DexClassImpl(private val classDef: ClassDef, private val dex: DexImpl) : DexClass {

  override val name: String by lazy(LazyThreadSafetyMode.NONE) { retrieveName() }
  override val fields: Map<String, DexField> by lazy(LazyThreadSafetyMode.NONE) { retrieveFields() }
  override val methods: Map<String, DexMethod> by
    lazy(LazyThreadSafetyMode.NONE) { retrieveMethods() }

  private fun retrieveName(): String {
    return TypeIds.get(dex, (classDef.classIndex))
  }

  private fun retrieveFields(): Map<String, DexField> {
    throw IllegalStateException("Not implemented")
    if (classDef.classDataOffset == 0u) {
      return emptyMap()
    }
  }

  private fun retrieveMethods(): Map<String, DexMethod> {
    val methods = mutableMapOf<String, DexMethod>()

    // Class is interface class
    if (classDef.classDataOffset == 0u) {
      return emptyMap()
    }

    dex.reader.position = classDef.classDataOffset
    val classData = ClassData.fromReader(dex.reader)
    classData.directMethods.forEach {
      val method = getMethod(it, true)
      methods["${method.name}(${method.shorty})"] = method
    }
    classData.virtualMethods.forEach {
      val method = getMethod(it, false)
      methods["${method.name}(${method.shorty})"] = method
    }
    return methods
  }

  private fun getMethod(method: EncodedMethod, isDirect: Boolean): DexMethodImpl {
    return DexMethodImpl(method, isDirect, dex)
  }
}
