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
import function

@Suppress("NOTHING_TO_INLINE", "SameParameterValue", "UNUSED_PARAMETER", "unused")
object InlineSpilledVariables {

  @JvmStatic
  fun start() {
    val main1 = 1
    val main2 = 2
    val main3 = 3
    val main4 = 4
    function(main1, main2, main3, main4)
    foo(1, "Hello")
  }

  private inline fun foo(i: Int, s: String) {
    val foo1 = 1
    val foo2 = 2
    val foo3 = 3
    val foo4 = 4
    function(foo1, foo2, foo3, foo4)
    breakpoint()
    bar(i + 1, s + "1")
  }

  private inline fun bar(i: Int, s: String) {
    val bar1 = 1
    val bar2 = 2
    val bar3 = 3
    val bar4 = 4
    function(bar1, bar2, bar3, bar4)
    breakpoint()
  }
}
