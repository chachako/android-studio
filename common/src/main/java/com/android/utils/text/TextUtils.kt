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
package com.android.utils.text

/**
 * Generates a comma-separated list consisting of passed collection of [items]. Function is
 * parameterized by [lastSeparator] so enumerations like "A, B or C" and "A, B and C" can be
 * generated.
 *
 * Specify [oxfordComma] as `true` if you want a comma after the penultimate item.
 */
@JvmOverloads
fun Collection<Any>.toCommaSeparatedList(
  lastSeparator: String,
  oxfordComma: Boolean = false
): String =
  when (size) {
    0 -> ""
    1 -> single().toString()
    else ->
      chunked(size - 1).let {
        val postfix = if (oxfordComma) "," else ""
        "${it[0].joinToString(", ")}$postfix $lastSeparator ${it[1].single()}"
      }
  }
