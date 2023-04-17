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

@Suppress("NOTHING_TO_INLINE", "SameParameterValue", "unused")
object SpilledScopeVariable {

  @JvmStatic
  fun start() {
    foo()
  }

  /**
   * The following code forces the function scope variable `$i$f$foo` to spill. It does this by
   * having a lot of local variables and executing some code that uses them.
   *
   * We set 2 breakpoints. When running in a Java VM, the $i$f$foo` function scope variable has the
   * same scope when the breakpoints are hit, but then running in a DEX VM, the variable spills
   * between the breakpoints and it has a different scope for each one.
   */
  private inline fun foo() {
    val foo01 = 1
    val foo02 = 1
    val foo03 = 1
    val foo04 = 1
    val foo05 = 1
    breakpoint()
    val foo06 = 1
    val foo07 = 1
    val foo08 = 1
    val foo09 = 1
    val foo10 = 1
    val foo11 = 1
    val foo12 = 1
    val foo13 = 1
    val foo14 = 1
    val foo15 = 1
    function(foo01, foo02, foo03, foo04, foo05)
    function(foo06, foo07, foo08, foo09, foo10)
    function(foo11, foo12, foo13, foo14, foo15)
    breakpoint()
  }
}
