/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.jdwppacket

enum class CmdSet(val id: Int) {
  NoSet(0),
  Vm(1),
  ReferenceType(2),
  ClassType(3),
  ArrayType(4),
  InterfaceType(5),
  Method(6),
  // 7 unused
  Field(8),
  ObjectReference(9),
  StringReference(10),
  ThreadReference(11),
  ThreadGroupReference(12),
  ArrayReference(13),
  ClassLoaderReference(14),
  EventRequest(15),
  StackFrame(16),
  ClassObjectReference(17),
  Event(64),
  DDM(199);

  companion object {

    private val map = CmdSet.values().associateBy(CmdSet::id)

    @JvmStatic
    fun fromInt(value: Int) =
      CmdSet.map[value] ?: throw IllegalStateException("No CmdSet for $value")
  }
}
