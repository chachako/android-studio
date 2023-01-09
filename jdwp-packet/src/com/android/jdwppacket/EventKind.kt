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

enum class EventKind(val id: Int) {
  SINGLE_STEP(1),
  BREAKPOINT(2),
  FRAME_POP(3),
  EXCEPTION(4),
  USER_DEFINED(5),
  THREAD_START(6),
  THREAD_DEATH(7),
  CLASS_PREPARE(8),
  CLASS_UNLOAD(9),
  CLASS_LOAD(10),
  FIELD_ACCESS(20),
  FIELD_MODIFICATION(21),
  EXCEPTION_CATCH(30),
  METHOD_ENTRY(40),
  METHOD_EXIT(41),
  METHOD_EXIT_WITH_RETURN_VALUE(42),
  MONITOR_CONTENDED_ENTER(43),
  MONITOR_CONTENDED_ENTERED(44),
  MONITOR_WAIT(45),
  MONITOR_WAITED(46),
  VM_START(90),
  VM_DEATH(99);

  companion object {

    private val map = EventKind.values().associateBy(EventKind::id)

    @JvmStatic
    fun fromID(value: Int) = map[value] ?: throw IllegalStateException("No EventKind for $value")
  }
}
