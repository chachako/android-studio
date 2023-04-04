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
package tests

import breakpoint

@Suppress("NOTHING_TO_INLINE", "SameParameterValue", "UNUSED_PARAMETER", "unused")
object Inline {

  @JvmStatic
  fun start() {
    breakpoint()
    foo(1, "Hello")
  }

  private inline fun foo(i: Int, s: String) {
    breakpoint()
    bar(i + 1, s + "1")
  }

  private inline fun bar(i: Int, s: String) {
    breakpoint()
  }
}
