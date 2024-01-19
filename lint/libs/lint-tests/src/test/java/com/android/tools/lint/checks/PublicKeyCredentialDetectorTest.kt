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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles

class PublicKeyCredentialDetectorTest : AbstractCheckTest() {
  override fun getDetector() = PublicKeyCredentialDetector()

  fun testDocumentationExample() {
    lint()
      .files(
        manifest().minSdk(27),
        gradle(
            """
            dependencies {
                implementation 'androidx.credentials:credentials-play-services-auth:+'
            }
          """
          )
          .indented(),
        kotlin(
            """
                package test.pkg

                import androidx.credentials.CreatePublicKeyCredentialRequest

                class Test {
                  fun test() {
                    val request = CreatePublicKeyCredentialRequest()
                  }
                }
                """
          )
          .indented(),
        publicKeyCredentialStub,
      )
      .run()
      .expect(
        """
        src/main/kotlin/test/pkg/Test.kt:7: Warning: PublicKeyCredential is only supported from Android 9 (API level 28) and higher [PublicKeyCredential]
            val request = CreatePublicKeyCredentialRequest()
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testPublicKeyCredentialNoDependency() {
    lint()
      .files(
        manifest().minSdk(27),
        kotlin(
            """
                package test.pkg

                import androidx.credentials.CreatePublicKeyCredentialRequest

                class Test {
                  fun test() {
                    val request = CreatePublicKeyCredentialRequest()
                  }
                }
                """
          )
          .indented(),
        publicKeyCredentialStub,
      )
      .run()
      .expectClean()
  }

  fun testPublicKeyCredentialMinApiAtLeast28() {
    lint()
      .files(
        manifest().minSdk(28),
        gradle(
            """
            dependencies {
                implementation 'androidx.credentials:credentials-play-services-auth:+'
            }
          """
          )
          .indented(),
        kotlin(
            """
                package test.pkg

                import androidx.credentials.CreatePublicKeyCredentialRequest

                class Test {
                  fun test() {
                    val request = CreatePublicKeyCredentialRequest()
                  }
                }
                """
          )
          .indented(),
        publicKeyCredentialStub,
      )
      .run()
      .expectClean()
  }

  fun testPublicKeyCredentialMinApiChecksInCode() {
    lint()
      .files(
        manifest().minSdk(27),
        gradle(
            """
            dependencies {
                implementation 'androidx.credentials:credentials-play-services-auth:+'
            }
          """
          )
          .indented(),
        kotlin(
            """
                package test.pkg

                import androidx.credentials.CreatePublicKeyCredentialRequest

                class Test {
                  fun test() {
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                      CreatePublicKeyCredentialRequest()
                    }

                    if (android.os.Build.VERSION.SDK_INT < 28) return

                    CreatePublicKeyCredentialRequest()
                  }
                }
                """
          )
          .indented(),
        publicKeyCredentialStub,
      )
      .run()
      .expectClean()
  }
}

private val publicKeyCredentialStub: TestFile =
  TestFiles.kotlin(
      """
    package androidx.credentials

    class CreatePublicKeyCredentialRequest
    """
    )
    .indented()
