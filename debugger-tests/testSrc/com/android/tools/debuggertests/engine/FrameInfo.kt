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
package com.android.tools.debuggertests.engine

/** Describes a stack frame. */
internal class FrameInfo(private val variables: List<VariableInfo>) {

  override fun toString() = variables.joinToString("\n") { it.toString() }
}

/** Describes a variable. */
internal class VariableInfo(private val name: String, private val slot: Int) {

  override fun toString() = "$slot: $name"
}
