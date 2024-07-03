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

import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.TestFiles

class CredentialManagerSignInWithGoogleDetectorTest : AbstractCheckTest() {
  override fun getDetector() = CredentialManagerSignInWithGoogleDetector()

  fun testDocumentationExample() {
    lint()
      .projects(
        STUB_LIBRARY,
        project()
          .type(ProjectDescription.Type.APP)
          .name("app")
          .dependsOn(STUB_LIBRARY)
          .files(
            kotlin(
                """
                package com.example.app

                import androidx.credentials.GetCredentialResponse
                import androidx.credentials.PublicKeyCredential
                import com.google.android.libraries.identity.googleid.GetGoogleIdOption

                class Foo {
                    fun foo() {
                        val googleIdOption = GetGoogleIdOption.Builder().build()
                    }

                    fun handleSignIn(result: GetCredentialResponse) {
                        when (val credential = result.credential) {
                            is PublicKeyCredential -> {
                                bar()
                            }
                            else -> {}
                        }
                    }

                    fun bar() { TODO() }
                }
                """
              )
              .indented()
          ),
      )
      .run()
      .expect(
        """
        src/com/example/app/Foo.kt:9: Warning: Use of :googleid classes without use of GoogleIdTokenCredential [CredentialManagerSignInWithGoogle]
                val googleIdOption = GetGoogleIdOption.Builder().build()
                                     ~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  @Suppress("KotlinConstantConditions")
  fun testClean() {
    // Same as above, except we have added the missing code.
    lint()
      .projects(
        STUB_LIBRARY,
        project()
          .type(ProjectDescription.Type.APP)
          .name("app")
          .dependsOn(STUB_LIBRARY)
          .files(
            kotlin(
                """
                package com.example.app

                import androidx.credentials.CustomCredential
                import androidx.credentials.GetCredentialResponse
                import androidx.credentials.PublicKeyCredential
                import com.google.android.libraries.identity.googleid.GetGoogleIdOption
                import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

                class Foo {
                    fun foo() {
                        val googleIdOption = GetGoogleIdOption.Builder().build()
                    }

                    fun handleSignIn(result: GetCredentialResponse) {
                        when (val credential = result.credential) {
                            is PublicKeyCredential -> {
                                bar()
                            }
                            is CustomCredential -> {
                                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                    bar()
                                }
                            }
                            else -> {}
                        }
                    }

                    fun bar() { TODO() }
                }
                """
              )
              .indented()
          ),
      )
      .run()
      .expectClean()
  }

  fun testSignInWithGoogleClass() {
    // Same as testDocumentationExample, but with GetSignInWithGoogleOption.
    lint()
      .projects(
        STUB_LIBRARY,
        project()
          .type(ProjectDescription.Type.APP)
          .name("app")
          .dependsOn(STUB_LIBRARY)
          .files(
            kotlin(
                """
                package com.example.app

                import androidx.credentials.GetCredentialResponse
                import androidx.credentials.PublicKeyCredential
                import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption

                class Foo {
                    fun foo() {
                        val googleIdOption = GetSignInWithGoogleOption.Builder().build()
                    }

                    fun handleSignIn(result: GetCredentialResponse) {
                        when (val credential = result.credential) {
                            is PublicKeyCredential -> {
                                bar()
                            }
                            else -> {}
                        }
                    }

                    fun bar() { TODO() }
                }
                """
              )
              .indented()
          ),
      )
      .run()
      .expect(
        """
        src/com/example/app/Foo.kt:9: Warning: Use of :googleid classes without use of GoogleIdTokenCredential [CredentialManagerSignInWithGoogle]
                val googleIdOption = GetSignInWithGoogleOption.Builder().build()
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  @Suppress("KotlinConstantConditions")
  fun testCleanMultiModule() {
    // The code that handles the response is in a library module.
    lint()
      .projects(
        STUB_LIBRARY,
        project()
          .type(ProjectDescription.Type.LIBRARY)
          .name("lib")
          .dependsOn(STUB_LIBRARY)
          .files(
            kotlin(
                """
                package com.example.lib

                import androidx.credentials.CustomCredential
                import androidx.credentials.GetCredentialResponse
                import androidx.credentials.PublicKeyCredential
                import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

                class Bar {

                    fun handleSignIn(result: GetCredentialResponse) {
                        when (val credential = result.credential) {
                            is PublicKeyCredential -> {
                                bar()
                            }
                            is CustomCredential -> {
                                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                    bar()
                                }
                            }
                            else -> {}
                        }
                    }

                    fun bar() { TODO() }
                }
                """
              )
              .indented()
          ),
        project()
          .type(ProjectDescription.Type.APP)
          .name("app")
          .dependsOn(STUB_LIBRARY)
          .dependsOn("lib")
          .files(
            kotlin(
                """
                package com.example.app

                import com.google.android.libraries.identity.googleid.GetGoogleIdOption

                class Foo {
                    fun foo() {
                        val googleIdOption = GetGoogleIdOption.Builder().build()
                    }
                }
                """
              )
              .indented()
          ),
      )
      .run()
      .expectClean()
  }

  fun testBadMultiModule() {
    // The code that references the Google ID classes is in a library module;
    // the warning should still be reported when we reach the app module.
    lint()
      .projects(
        STUB_LIBRARY,
        project()
          .type(ProjectDescription.Type.LIBRARY)
          .name("lib")
          .dependsOn(STUB_LIBRARY)
          .files(
            kotlin(
                """
                package com.example.lib

                import com.google.android.libraries.identity.googleid.GetGoogleIdOption

                class Bar {
                    fun foo() {
                        val googleIdOption = GetGoogleIdOption.Builder().build()
                    }
                }
                """
              )
              .indented()
          ),
        project()
          .type(ProjectDescription.Type.APP)
          .name("app")
          .dependsOn(STUB_LIBRARY)
          .dependsOn("lib")
          .files(
            kotlin(
                """
                package com.example.app

                class Foo {
                    fun foo() {}
                }
                """
              )
              .indented()
          ),
      )
      .run()
      .expect(
        """
        ../lib/src/com/example/lib/Bar.kt:7: Warning: Use of :googleid classes without use of GoogleIdTokenCredential [CredentialManagerSignInWithGoogle]
                val googleIdOption = GetGoogleIdOption.Builder().build()
                                     ~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }
}

private val STUB_LIBRARY =
  ProjectDescription()
    .files(
      TestFiles.kotlin(
          """
          package com.google.android.libraries.identity.googleid

          /*HIDE-FROM-DOCUMENTATION*/

          class GetGoogleIdOption {
            class Builder
          }
          class GetSignInWithGoogleOption {
            class Builder(s: String)
          }
          class GoogleIdTokenCredential {
            class Builder
          }
          """
        )
        .indented(),
      TestFiles.kotlin(
          """
          package androidx.credentials

          /*HIDE-FROM-DOCUMENTATION*/

          class GetCredentialResponse(val credential: Credential)

          abstract class Credential(
              val type: String
          )

          open class CustomCredential(
              type: String
          ) : Credential(type)

          class PublicKeyCredential(
              val authenticationResponseJson: String
          ) : Credential(TYPE_PUBLIC_KEY_CREDENTIAL)

          """
        )
        .indented(),
    )
    .type(ProjectDescription.Type.LIBRARY)
    .name("StubLib")
