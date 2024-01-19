package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import org.junit.Test

class ContextReceiverDetectorTest {

  @Test
  fun testContextReceivers() {
    studioLint()
      .files(
        kotlin(
            """
            package test.pkg

            class Test {
              inner class Logger {
                fun log(str: String) {
                  println()
                }
              }
              context(Test.Logger)
              fun store(s: String) {
                log("Method Context Receiver")
              }

              context(Test.Logger)
              inner class Printer {
                fun main() {
                  log("Class Context Receiver")
                }
              }
          }
          """
          )
          .indented()
      )
      .issues(ContextReceiverDetector.ISSUE)
      .run()
      .expect(
        """
          src/test/pkg/Test.kt:9: Error: Do not use context receivers. They are an experimental feature at this time. [ContextReceiver]
              context(Test.Logger)
                      ~~~~~~~~~~~
          src/test/pkg/Test.kt:14: Error: Do not use context receivers. They are an experimental feature at this time. [ContextReceiver]
              context(Test.Logger)
                      ~~~~~~~~~~~
          2 errors, 0 warnings
        """
      )
  }
}
