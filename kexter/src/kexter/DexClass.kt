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

/** A representation of a class as contained in a byte code. */
interface DexClass {
  /**
   * The internal name of the class e.g.: Lkotlin/math/Constants; Syntax is described here:
   * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3
   */
  val name: String

  /** All fields of this class, indexed by their name */
  val fields: Map<String, DexField>

  /**
   * All methods of this class, indexed by their <NAME><DESCRIPTOR>. Note that the descriptor does
   * not use JVM format but ART "shorty notation" described here:
   * https://source.android.com/docs/core/runtime/dex-format#used-by-proto_id_item e.g.: equals(ZL)
   */
  val methods: Map<String, DexMethod>
}
