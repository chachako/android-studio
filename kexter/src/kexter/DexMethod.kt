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

/** A representation of a class Method */
interface DexMethod {

  /** The name of them method (does not include the short descriptor) */
  val name: String

  /**
   * The method descriptor, using shorty notation:
   * https://source.android.com/docs/core/runtime/dex-format#used-by-proto_id_item
   */
  val shorty: String

  /**
   * The return type of this Method. The shorty descriptor notation does not details the return type
   * like the JVM descriptor (e.g.: An object is just L). The exact name of the return type is here.
   */
  val returnType: String

  /**
   * A direct method is invoked without walking the inheritance chain of an object. DEX separate
   * direct and indirect methods.
   */
  val isDirect: Boolean

  /** The bytecode of this method. The content will be an empty list if this method is native. */
  val byteCode: DexBytecode

  /** The internal name of the class this method belongs to. e.g.: Ljava/lang/Object; */
  val type: String

  /**
   * A native method does not have bytecode [byteCode] returns a DexBytecode with empty list of
   * instructions.
   */
  val isNative: Boolean
}
