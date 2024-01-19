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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.LintMap

fun LintMap.getOrPutLintMap(key: String): LintMap {
  val result = this.getMap(key)
  if (result != null) return result
  val newMap = LintMap()
  this.put(key, newMap)
  return newMap
}

/**
 * Returns a Sequence of Location, assuming the LintMap is being used as a list, where the keys do
 * not matter and the values are the entries of the list.
 */
fun LintMap.toLocationSequence() = this.keys().map { index -> this.getLocation(index)!! }
