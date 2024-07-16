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
                        Cubic(i) // ERROR: Should have been wrapped in an add call.
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

  fun test331666842() {
    lint()
      .files(
        kotlin(
            """
            fun test() {
              buildList {
                this += ""
              }
            }
            """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testMethodReferences() {
    // Regression test for 331666842 comment 2
    lint()
      .files(
        kotlin(
            """
            import android.database.Cursor

            fun testOk1(): List<String> {
              return buildList { // OK 1
                val m: (String)->Boolean = ::add
                m("test")
              }
            }

            fun testOk2(): List<String> {
              return buildList { // OK 2
                val m: (String)->Boolean = this::add
                m("test")
              }
            }

            fun testWrongList(): List<String> {
              val list = mutableListOf<String>()
              return buildList { // ERROR: wrong list
                val m: (String)->Boolean = list::add
                m("test")
              }
            }

            class PartData
            private fun retrievePartData(): PartData? = nullo

            private fun queryMmsPartTable(query: Cursor?) {
                query.use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) {
                                retrievePartData()
                                    ?.let(::add)
                            }
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
        src/PartData.kt:19: Warning: No add calls within buildList lambda; this is usually a mistake [BuildListAdds]
          return buildList { // ERROR: wrong list
                 ~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testScenarios() {
    lint()
      .files(
        kotlin(
            """
            fun example(b: List<String>) {
                buildList { add(" ") } // OK
                buildList { addAll(listOf("", "")) } // OK
                buildList { this.add(" ") } // OK
                buildList { this.addAll(b) } // OK
                buildList { someFunc(this, " ") } // OK
                buildList { this.extensionFunc(" ") } // OK
                buildList { extensionFunc(" ") } // OK
                buildList { this += " " } // OK
                buildList { plusAssign(" ") } // OK
                buildList { this.plusAssign(" ") } // OK
                buildList { listIterator().add(" ") } // OK
                buildList { this.listIterator().add(" ") } // OK


                buildList { val t = this; t.add(" ") } // OK
                buildList { val t = this; t.addAll(listOf("", "")) } // OK
                buildList { val t = this; t += " " } // OK
                buildList { val t = this; t.extensionFunc(" ") } // OK
                buildList { val t = this; t.plusAssign(" ") } // OK
                buildList { val t = this; someFunc(t, " ") } // OK

                buildList { this + this } // OK
                buildList { this + " " } // OK
                buildList { plus(" ") } // OK
                buildList { returnsNewList(this) } // OK
            }

            private fun <E> MutableList<E>.extensionFunc(e: E) {
                if (contains(e)) {
                    add(e)
                }
            }

            private fun <E> someFunc(l: MutableList<E>, e: E) {
                if (l.contains(e)) {
                    l.add(e)
                }
            }

            private fun returnsNewList(l: MutableList<String>) = listOf(1, 2, 3) + l
            """
          )
          .indented()
      )
      .run()
      .expectClean()
  }
}
