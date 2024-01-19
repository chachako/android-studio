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
package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles
import org.junit.Test

class AdbDeviceServicesCommandDetectorTest {
  companion object {
    private val adbDeviceServicesFile: TestFile =
      TestFiles.kotlin(
          """
                package com.android.adblib

                interface AdbDeviceServices {
                  fun <T> exec(): Flow<T>

                  fun <T> shell(): Flow<T>

                  fun <T> shellV2(): Flow<T>
                }
                """
        )
        .indented()
  }

  @Test
  fun testUseOfExecMethodIsDiscouraged() {
    studioLint()
      .files(
        adbDeviceServicesFile,
        TestFiles.kotlin(
            """
                    package test.pkg
                    import com.android.adblib.AdbDeviceServices

                    fun someMethod(adbDeviceServices: AdbDeviceServices) {
                      adbDeviceServices.exec()
                    }
                """
          )
          .indented(),
      )
      .issues(AdbDeviceServicesCommandDetector.ISSUE)
      .run()
      .expect(
        """
                src/test/pkg/test.kt:5: Error: Use of com.android.adblib.AdbDeviceServices#exec is discouraged. Consider using AdbDeviceServices.shellCommand() instead [AdbDeviceServicesCommand]
                  adbDeviceServices.exec()
                  ~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  @Test
  fun testUseOfShellMethodIsDiscouraged() {
    studioLint()
      .files(
        adbDeviceServicesFile,
        TestFiles.kotlin(
            """
                    package test.pkg
                    import com.android.adblib.AdbDeviceServices

                    fun someMethod(adbDeviceServices: AdbDeviceServices) {
                      adbDeviceServices.shell()
                    }
                """
          )
          .indented(),
      )
      .issues(AdbDeviceServicesCommandDetector.ISSUE)
      .run()
      .expect(
        """
                src/test/pkg/test.kt:5: Error: Use of com.android.adblib.AdbDeviceServices#shell is discouraged. Consider using AdbDeviceServices.shellCommand() instead [AdbDeviceServicesCommand]
                  adbDeviceServices.shell()
                  ~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  @Test
  fun testUseOfShellV2MethodIsDiscouraged() {
    studioLint()
      .files(
        adbDeviceServicesFile,
        TestFiles.kotlin(
            """
                    package test.pkg
                    import com.android.adblib.AdbDeviceServices

                    fun someMethod(adbDeviceServices: AdbDeviceServices) {
                      adbDeviceServices.shellV2()
                    }
                """
          )
          .indented(),
      )
      .issues(AdbDeviceServicesCommandDetector.ISSUE)
      .run()
      .expect(
        """
                src/test/pkg/test.kt:5: Error: Use of com.android.adblib.AdbDeviceServices#shellV2 is discouraged. Consider using AdbDeviceServices.shellCommand() instead [AdbDeviceServicesCommand]
                  adbDeviceServices.shellV2()
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  @Test
  fun testUseOfExecMethodInUnrelatedClassIsNotTriggeringWarnings() {
    studioLint()
      .files(
        adbDeviceServicesFile,
        TestFiles.kotlin(
            """
                    package test.pkg
                    import com.android.adblib.AdbDeviceServices

                    fun someMethod(unrelatedInterface: UnrelatedInterface, unrelatedClass: UnrelatedClass) {
                      unrelatedInterface.exec()
                      unrelatedClass.exec()
                    }

                    interface UnrelatedInterface {
                      fun exec()
                    }

                    interface UnrelatedClass {
                      fun exec()
                    }
                """
          )
          .indented(),
      )
      .issues(AdbDeviceServicesCommandDetector.ISSUE)
      .run()
      .expect("No warnings.")
  }

  @Test
  fun testDoesNotTriggerViolationsInAdblibInternalImplementation() {
    // Note that the name of the package which starts with com.android.adblib
    studioLint()
      .files(
        adbDeviceServicesFile,
        TestFiles.kotlin(
            """
                  package com.android.adblib.impl

                  import com.android.adblib.AdbDeviceServices

                  internal class ShellCommandImpl<T> {
                      fun someMethod(adbDeviceServices: AdbDeviceServices) {
                        adbDeviceServices.exec()
                      }
                  }
              """
          )
          .indented(),
      )
      .issues(AdbDeviceServicesCommandDetector.ISSUE)
      .run()
      .expect("No warnings.")
  }
}
