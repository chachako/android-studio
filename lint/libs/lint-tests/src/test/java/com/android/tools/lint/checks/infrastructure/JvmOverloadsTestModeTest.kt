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

package com.android.tools.lint.checks.infrastructure

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.JvmOverloadsTestMode.Companion.DEFAULT_PROPERTY_PARAMETER
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.Test

class JvmOverloadsTestModeTest {
  private fun convert(@Language("kotlin") source: String): String {
    return convert(kotlin(source))
  }

  private fun convert(testFile: TestFile): String {
    val sdkHome = TestUtils.getSdk().toFile()
    var source = testFile.contents
    JvmOverloadsTestMode().processTestFiles(listOf(testFile), sdkHome) { _, s -> source = s }
    return source
  }

  @Test
  fun testBasic() {
    @Language("kotlin")
    val kotlin =
      """
      @file:Suppress("ALL")
      // Insert @JvmOverloads here, plus a default argument to ensure that it creates multiple copies of the method
      fun test(owner: String, name: String, desc: String): Int = 0

      // Insert @JvmOverloads here (already have default so no need to inject that)
      fun testWithDefaults(owner: String, name: String, desc: String = ""): Int = 0

      // Here the lambda is last, but we have a default parameter, so we can insert
      // a @JvmOverloads
      fun hasLambdaLastWithDefault(owner: String = "test", lambda: ()->Boolean = {}): Int = 0

      // Insert @JvmOverloads, but no trailing comma in argument list
      fun blank(): Int = 0

      class MyBuilder { fun build(): String = "done" }

      // Make sure we handle trailing commas
      fun ofList(
         contentUri: String,
      ): String? = null

      // Constructors
      class Test(val property: String) {
          constructor(secondary: Int) : this(secondary.toString())
          companion object {
            fun test() {}
          }
      }

      object Test {
          fun test() {}
      }
      """
        .trimIndent()
        .trim()

    val injectedParameter = DEFAULT_PROPERTY_PARAMETER

    @Suppress("LocalVariableName")
    @Language("kotlin")
    val expected =
      """
      @file:Suppress("ALL")
      // Insert @JvmOverloads here, plus a default argument to ensure that it creates multiple copies of the method
      @JvmOverloads
      fun test(owner: String, name: String, desc: String, $injectedParameter): Int = 0

      // Insert @JvmOverloads here (already have default so no need to inject that)
      @JvmOverloads
      fun testWithDefaults(owner: String, name: String, desc: String = ""): Int = 0

      // Here the lambda is last, but we have a default parameter, so we can insert
      // a @JvmOverloads
      @JvmOverloads
      fun hasLambdaLastWithDefault(owner: String = "test", lambda: ()->Boolean = {}): Int = 0

      // Insert @JvmOverloads, but no trailing comma in argument list
      @JvmOverloads
      fun blank($injectedParameter): Int = 0

      class MyBuilder { @JvmOverloads fun build($injectedParameter): String = "done" }

      // Make sure we handle trailing commas
      @JvmOverloads
      fun ofList(
         contentUri: String,
      $injectedParameter): String? = null

      // Constructors
      class Test @JvmOverloads constructor (val property: String, $injectedParameter) {
          @JvmOverloads
          constructor(secondary: Int, $injectedParameter) : this(secondary.toString())
          companion object {
            @JvmOverloads
            fun test($injectedParameter) {}
          }
      }

      object Test {
          @JvmOverloads
          fun test($injectedParameter) {}
      }
      """
        .trimIndent()
        .trim()

    val modified = convert(kotlin)
    assertEquals(expected, modified)
  }

  @Suppress("RedundantOverride")
  @Test
  fun testCornerCases() {
    @Language("kotlin")
    val kotlin =
      """
      // Already has @JvmOverloads; nothing to do
      @JvmOverloads
      fun alreadyOverloaded(owner: String = "test"): Int = 0

      // Don't try to insert a new default parameter at the end when there is
      // a lambda there (since it can break calls specifying lambda outside of argument list
      fun hasLambdaLast(owner: String = "test", lambda: ()->Boolean): Int = 0

      class Test {
          // Don't change overridden functions
          override fun toString(): String {return super.toString() }
          // Also leave operators alone; they have a specific signature. Infix implicitly two arguments.
          operator fun get(key: Int) {}
          infix fun Int.colorize2(o: Int) { }
      }

      // Don't put arguments after vararg arguments
      fun addListeners(args: Int, vararg listeners: String) {}

      // @JvmOverloads and default parameters not allowed on interface methods
      fun interface Foo {
         fun foo()
      }

      // Also not for annotations
      annotation class MyAnnotation(val int: Int)

      // If there is a single parameter, and it's of an interface type, we may try
      // to use this as a SAM method
      fun test(foo: Foo) {}
      """
        .trimIndent()
        .trim()

    val modified = convert(kotlin)
    assertEquals(kotlin, modified)
  }
}
