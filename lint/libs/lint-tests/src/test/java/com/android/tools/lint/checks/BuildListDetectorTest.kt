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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class BuildListDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return BuildListDetector()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        kotlin(
            """
            class Cubic(id: Int)
            val _morphMatch = listOf(1)
            fun asCubics_broken(progress: Float): List<Cubic> {
                return buildList { // ERROR
                    for (i in _morphMatch.indices) {
                        Cubic(i)
                    }
                }
            }

            fun asCubics_correct(progress: Float): List<Cubic> {
                return buildList { // OK
                    for (i in _morphMatch.indices) {
                        add(Cubic(i))
                    }
                }
            }
            """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/Cubic.kt:4: Warning: No add calls within buildList lambda; this is usually a mistake [BuildListAdds]
            return buildList { // ERROR
                   ~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testAddAll() {
    lint()
      .files(
        kotlin(
            """
            fun test(existing: List<String>): List<String> {
                return buildList { // OK
                    addAll(existing)
                }
            }
            """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testWrongList() {
    lint()
      .files(
        kotlin(
            """
            fun test(): List<String> {
                val list = mutableListOf<String>()
                return buildList { // ERROR
                    list.add("test") // wrong list
                }
            }

            fun test2(): List<String> {
                return buildList { // OK 1
                    add("test")
                }
            }

            fun test3(): List<String> {
                return buildList { // OK 2
                    this.add("test")
                }
            }
            """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/test.kt:3: Warning: No add calls within buildList lambda; this is usually a mistake [BuildListAdds]
            return buildList { // ERROR
                   ~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testCustomAdd() {
    lint()
      .files(
        kotlin(
            """
            fun example(): List<String> {
              return buildList {
                addIfPresent("test")
              }
            }

            private fun <E> MutableList<E>.addIfPresent(e: E) {
              if (contains(e)) {
                add(e)
              }
            }
            """
          )
          .indented()
      )
      .run()
      .expectClean()
  }
}
