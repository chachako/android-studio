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

import kexter.Dex
import kexter.Logger

internal class DexImpl(val bytes: ByteArray, val logger: Logger) : Dex() {

  val reader = DexReader(bytes)
  val header: DexHeader = DexHeader(reader)
  val stringIds: StringIds = StringIds(header.stringIds, this)
  val classDefs: ClassDefs = ClassDefs(header.classDefs, this)
  val methodIds: MethodIds = MethodIds(header.methodsIds, this)
  val protoIds: ProtoIds = ProtoIds(header.protoIds, this)

  override val classes by lazy(LazyThreadSafetyMode.NONE) { retrieveClasses() }

  private fun retrieveClasses(): Map<String, DexClassImpl> {
    val map = mutableMapOf<String, DexClassImpl>()
    for (index in 0u..<classDefs.numElements()) {
      val classDef = classDefs.getClassDef(index)
      val clazz = DexClassImpl(classDef, this)
      map[clazz.name] = clazz
    }
    return map
  }
}
