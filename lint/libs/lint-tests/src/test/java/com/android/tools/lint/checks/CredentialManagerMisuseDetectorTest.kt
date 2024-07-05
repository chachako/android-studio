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

class CredentialManagerMisuseDetectorTest : AbstractCheckTest() {
  override fun getDetector() = CredentialManagerMisuseDetector()

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

                import android.content.Context
                import android.os.Build
                import android.os.CancellationSignal
                import androidx.credentials.CredentialManager
                import androidx.credentials.CredentialManagerCallback
                import androidx.credentials.GetCredentialRequest
                import androidx.credentials.GetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse.PendingGetCredentialHandle
                import androidx.credentials.exceptions.GetCredentialException
                import androidx.credentials.exceptions.NoCredentialException
                import java.util.concurrent.Executor

                class Foo {
                    suspend fun foo(
                        credentialManager: CredentialManager,
                        context: Context,
                        request: GetCredentialRequest,
                        cancellationSignal: CancellationSignal,
                        executor: Executor,
                        callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>) {
                        try {
                            val prepareGetCredentialResponse = credentialManager.prepareGetCredential(request)
                            credentialManager.getCredential(context, request)
                            credentialManager.getCredential(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!)
                            credentialManager.getCredentialAsync(context, request, cancellationSignal, executor, callback)
                            credentialManager.getCredentialAsync(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!, cancellationSignal, executor, callback)
                        } catch (e: GetCredentialException) {
                            bar(e)
                        }
                    }

                    fun bar(e: GetCredentialException) {
                        TODO()
                    }
                }
                """
              )
              .indented()
          ),
      )
      .run()
      .expect(
        """
        src/com/example/app/Foo.kt:26: Warning: Call to CredentialManager.getCredential without use of NoCredentialException [CredentialManagerMisuse]
                    credentialManager.getCredential(context, request)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/com/example/app/Foo.kt:27: Warning: Call to CredentialManager.getCredential without use of NoCredentialException [CredentialManagerMisuse]
                    credentialManager.getCredential(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/com/example/app/Foo.kt:28: Warning: Call to CredentialManager.getCredential without use of NoCredentialException [CredentialManagerMisuse]
                    credentialManager.getCredentialAsync(context, request, cancellationSignal, executor, callback)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/com/example/app/Foo.kt:29: Warning: Call to CredentialManager.getCredential without use of NoCredentialException [CredentialManagerMisuse]
                    credentialManager.getCredentialAsync(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!, cancellationSignal, executor, callback)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 4 warnings
        """
      )
  }

  fun testCatchException() {
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

                import android.content.Context
                import android.os.Build
                import android.os.CancellationSignal
                import androidx.credentials.CredentialManager
                import androidx.credentials.CredentialManagerCallback
                import androidx.credentials.GetCredentialRequest
                import androidx.credentials.GetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse.PendingGetCredentialHandle
                import androidx.credentials.exceptions.GetCredentialException
                import androidx.credentials.exceptions.NoCredentialException
                import java.util.concurrent.Executor

                class Foo {
                    suspend fun foo(
                        credentialManager: CredentialManager,
                        context: Context,
                        request: GetCredentialRequest,
                        cancellationSignal: CancellationSignal,
                        executor: Executor,
                        callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>) {
                        try {
                            val prepareGetCredentialResponse = credentialManager.prepareGetCredential(request)
                            credentialManager.getCredential(context, request)
                            credentialManager.getCredential(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!)
                            credentialManager.getCredentialAsync(context, request, cancellationSignal, executor, callback)
                            credentialManager.getCredentialAsync(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!, cancellationSignal, executor, callback)
                        } catch (e: NoCredentialException) {
                            bar()
                        }
                    }

                    fun bar() {
                        TODO()
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

  fun testIfIsException() {
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

                import android.content.Context
                import android.os.Build
                import android.os.CancellationSignal
                import androidx.credentials.CredentialManager
                import androidx.credentials.CredentialManagerCallback
                import androidx.credentials.GetCredentialRequest
                import androidx.credentials.GetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse.PendingGetCredentialHandle
                import androidx.credentials.exceptions.GetCredentialException
                import androidx.credentials.exceptions.NoCredentialException
                import java.util.concurrent.Executor

                class Foo {
                    suspend fun foo(
                        credentialManager: CredentialManager,
                        context: Context,
                        request: GetCredentialRequest,
                        cancellationSignal: CancellationSignal,
                        executor: Executor,
                        callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>) {
                        try {
                            val prepareGetCredentialResponse = credentialManager.prepareGetCredential(request)
                            credentialManager.getCredential(context, request)
                            credentialManager.getCredential(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!)
                            credentialManager.getCredentialAsync(context, request, cancellationSignal, executor, callback)
                            credentialManager.getCredentialAsync(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!, cancellationSignal, executor, callback)
                        } catch (e: GetCredentialException) {
                            bar(e)
                        }
                    }

                    fun bar(e: GetCredentialException) {
                      if (e is NoCredentialException) {
                        TODO()
                      }
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

  fun testWhenIsException() {
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

                import android.content.Context
                import android.os.Build
                import android.os.CancellationSignal
                import androidx.credentials.CredentialManager
                import androidx.credentials.CredentialManagerCallback
                import androidx.credentials.GetCredentialRequest
                import androidx.credentials.GetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse.PendingGetCredentialHandle
                import androidx.credentials.exceptions.GetCredentialException
                import androidx.credentials.exceptions.NoCredentialException
                import java.util.concurrent.Executor

                class Foo {
                    suspend fun foo(
                        credentialManager: CredentialManager,
                        context: Context,
                        request: GetCredentialRequest,
                        cancellationSignal: CancellationSignal,
                        executor: Executor,
                        callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>) {
                        try {
                            val prepareGetCredentialResponse = credentialManager.prepareGetCredential(request)
                            credentialManager.getCredential(context, request)
                            credentialManager.getCredential(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!)
                            credentialManager.getCredentialAsync(context, request, cancellationSignal, executor, callback)
                            credentialManager.getCredentialAsync(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!, cancellationSignal, executor, callback)
                        } catch (e: GetCredentialException) {
                            bar(e)
                        }
                    }

                    fun bar(e: GetCredentialException) {
                      when (e) {
                        is NoCredentialException -> TODO()
                      }
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

  fun testJavaCatchException() {
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

                import android.content.Context
                import android.os.Build
                import android.os.CancellationSignal
                import androidx.credentials.CredentialManager
                import androidx.credentials.CredentialManagerCallback
                import androidx.credentials.GetCredentialRequest
                import androidx.credentials.GetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse.PendingGetCredentialHandle
                import androidx.credentials.exceptions.GetCredentialException
                import androidx.credentials.exceptions.NoCredentialException
                import java.util.concurrent.Executor

                class Foo {
                    suspend fun foo(
                        credentialManager: CredentialManager,
                        context: Context,
                        request: GetCredentialRequest,
                        cancellationSignal: CancellationSignal,
                        executor: Executor,
                        callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>) {
                        try {
                            val prepareGetCredentialResponse = credentialManager.prepareGetCredential(request)
                            credentialManager.getCredential(context, request)
                            credentialManager.getCredential(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!)
                            credentialManager.getCredentialAsync(context, request, cancellationSignal, executor, callback)
                            credentialManager.getCredentialAsync(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!, cancellationSignal, executor, callback)
                        } catch (e: GetCredentialException) {
                            bar(e)
                        }
                    }
                }
                """
              )
              .indented(),
            java(
                """
                package com.example.app;

                import androidx.credentials.exceptions.NoCredentialException;

                public class Bar {
                    public static void b() {
                      try {
                        c();
                      } catch (NoCredentialException e) {
                        c();
                      }
                    }

                    public static void c() {}
                }
                """
              )
              .indented(),
          ),
      )
      .run()
      .expectClean()
  }

  fun testJavaCatchMultiException() {
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

                import android.content.Context
                import android.os.Build
                import android.os.CancellationSignal
                import androidx.credentials.CredentialManager
                import androidx.credentials.CredentialManagerCallback
                import androidx.credentials.GetCredentialRequest
                import androidx.credentials.GetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse.PendingGetCredentialHandle
                import androidx.credentials.exceptions.GetCredentialException
                import androidx.credentials.exceptions.NoCredentialException
                import java.util.concurrent.Executor

                class Foo {
                    suspend fun foo(
                        credentialManager: CredentialManager,
                        context: Context,
                        request: GetCredentialRequest,
                        cancellationSignal: CancellationSignal,
                        executor: Executor,
                        callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>) {
                        try {
                            val prepareGetCredentialResponse = credentialManager.prepareGetCredential(request)
                            credentialManager.getCredential(context, request)
                            credentialManager.getCredential(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!)
                            credentialManager.getCredentialAsync(context, request, cancellationSignal, executor, callback)
                            credentialManager.getCredentialAsync(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!, cancellationSignal, executor, callback)
                        } catch (e: GetCredentialException) {
                            bar(e)
                        }
                    }
                }
                """
              )
              .indented(),
            java(
                """
                package com.example.app;

                import androidx.credentials.exceptions.NoCredentialException;
                import androidx.credentials.exceptions.GetCredentialCustomException;

                public class Bar {
                    public static void b() {
                      try {
                        c();
                      } catch (GetCredentialCustomException|NoCredentialException e) {
                        c();
                      }
                    }

                    public static void c() {}
                }
                """
              )
              .indented(),
          ),
      )
      .run()
      .expectClean()
  }

  fun testCatchInOtherModule() {
    lint()
      .projects(
        STUB_LIBRARY,
        project()
          .type(ProjectDescription.Type.LIBRARY)
          .name("lib")
          .dependsOn(STUB_LIBRARY)
          .files(
            java(
                """
                package com.example.app;

                import androidx.credentials.exceptions.NoCredentialException;

                public class Bar {
                    public static void b() {
                      try {
                        c();
                      } catch (NoCredentialException e) {
                        c();
                      }
                    }

                    public static void c() {}
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

                import android.content.Context
                import android.os.Build
                import android.os.CancellationSignal
                import androidx.credentials.CredentialManager
                import androidx.credentials.CredentialManagerCallback
                import androidx.credentials.GetCredentialRequest
                import androidx.credentials.GetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse.PendingGetCredentialHandle
                import androidx.credentials.exceptions.GetCredentialException
                import androidx.credentials.exceptions.NoCredentialException
                import java.util.concurrent.Executor

                class Foo {
                    suspend fun foo(
                        credentialManager: CredentialManager,
                        context: Context,
                        request: GetCredentialRequest,
                        cancellationSignal: CancellationSignal,
                        executor: Executor,
                        callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>) {
                        try {
                            val prepareGetCredentialResponse = credentialManager.prepareGetCredential(request)
                            credentialManager.getCredential(context, request)
                            credentialManager.getCredential(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!)
                            credentialManager.getCredentialAsync(context, request, cancellationSignal, executor, callback)
                            credentialManager.getCredentialAsync(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!, cancellationSignal, executor, callback)
                        } catch (e: GetCredentialException) {
                            bar(e)
                        }
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

  fun testBadCallInOtherModule() {
    // The getCredential calls are in a library; the warnings are still reported
    // once we reach the app module.
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
                package com.example.app

                import android.content.Context
                import android.os.Build
                import android.os.CancellationSignal
                import androidx.credentials.CredentialManager
                import androidx.credentials.CredentialManagerCallback
                import androidx.credentials.GetCredentialRequest
                import androidx.credentials.GetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse
                import androidx.credentials.PrepareGetCredentialResponse.PendingGetCredentialHandle
                import androidx.credentials.exceptions.GetCredentialException
                import androidx.credentials.exceptions.NoCredentialException
                import java.util.concurrent.Executor

                class Foo {
                    suspend fun foo(
                        credentialManager: CredentialManager,
                        context: Context,
                        request: GetCredentialRequest,
                        cancellationSignal: CancellationSignal,
                        executor: Executor,
                        callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>) {
                        try {
                            val prepareGetCredentialResponse = credentialManager.prepareGetCredential(request)
                            credentialManager.getCredential(context, request)
                            credentialManager.getCredential(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!)
                            credentialManager.getCredentialAsync(context, request, cancellationSignal, executor, callback)
                            credentialManager.getCredentialAsync(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!, cancellationSignal, executor, callback)
                        } catch (e: GetCredentialException) {
                            bar(e)
                        }
                    }
                }
                """
              )
              .indented()
          ),
        project()
          .type(ProjectDescription.Type.APP)
          .name("app")
          .dependsOn("lib")
          .files(
            java(
                """
                package com.example.app;

                public class Bar {
                    public static void b() {}

                    public static void c() {}
                }
                """
              )
              .indented()
          ),
      )
      .run()
      .expect(
        """
        ../lib/src/com/example/app/Foo.kt:26: Warning: Call to CredentialManager.getCredential without use of NoCredentialException [CredentialManagerMisuse]
                    credentialManager.getCredential(context, request)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        ../lib/src/com/example/app/Foo.kt:27: Warning: Call to CredentialManager.getCredential without use of NoCredentialException [CredentialManagerMisuse]
                    credentialManager.getCredential(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        ../lib/src/com/example/app/Foo.kt:28: Warning: Call to CredentialManager.getCredential without use of NoCredentialException [CredentialManagerMisuse]
                    credentialManager.getCredentialAsync(context, request, cancellationSignal, executor, callback)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        ../lib/src/com/example/app/Foo.kt:29: Warning: Call to CredentialManager.getCredential without use of NoCredentialException [CredentialManagerMisuse]
                    credentialManager.getCredentialAsync(context, prepareGetCredentialResponse.pendingGetCredentialHandle!!, cancellationSignal, executor, callback)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 4 warnings
        """
      )
  }
}

private val STUB_LIBRARY =
  ProjectDescription()
    .files(
      TestFiles.kotlin(
          """
          package androidx.credentials

          /*HIDE-FROM-DOCUMENTATION*/

          import android.content.Context
          import android.os.CancellationSignal
          import androidx.credentials.exceptions.GetCredentialException
          import java.util.concurrent.Executor


          interface CredentialManager {
              suspend fun prepareGetCredential(
                  request: GetCredentialRequest,
              ): PrepareGetCredentialResponse

              suspend fun getCredential(
                  context: Context,
                  request: GetCredentialRequest,
              ): GetCredentialResponse

              suspend fun getCredential(
                  context: Context,
                  pendingGetCredentialHandle: PrepareGetCredentialResponse.PendingGetCredentialHandle,
              ): GetCredentialResponse

              fun getCredentialAsync(
                  context: Context,
                  request: GetCredentialRequest,
                  cancellationSignal: CancellationSignal?,
                  executor: Executor,
                  callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
              )

              fun getCredentialAsync(
                  context: Context,
                  pendingGetCredentialHandle: PrepareGetCredentialResponse.PendingGetCredentialHandle,
                  cancellationSignal: CancellationSignal?,
                  executor: Executor,
                  callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
              )
          }

          class PrepareGetCredentialResponse(val pendingGetCredentialHandle: PendingGetCredentialHandle?) {
              class PendingGetCredentialHandle
          }
          class GetCredentialRequest
          class GetCredentialResponse
          abstract class GetCredentialException
          class NoCredentialException : GetCredentialException
          interface CredentialManagerCallback<R : Any?, E : Any>
          """
        )
        .indented(),
      TestFiles.kotlin(
          """
          package androidx.credentials.exceptions

          /*HIDE-FROM-DOCUMENTATION*/

          abstract class GetCredentialException
          class GetCredentialCustomException : GetCredentialException
          class NoCredentialException : GetCredentialException
          """
        )
        .indented(),
    )
    .type(ProjectDescription.Type.LIBRARY)
    .name("StubLib")
