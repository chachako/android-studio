/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.Detector

class DeprecatedSinceApiDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return DeprecatedSinceApiDetector()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        manifest().minSdk(24),
        kotlin(
            """
            @file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE", "unused")
            package test.pkg
            import androidx.annotation.DeprecatedSinceApi

            class Test {
                fun test(api: Api, api2: Api2) {
                    // minSdkVersion = 24:

                    api.noLongerNecessary1(0) // WARN 1
                    api.noLongerNecessary2(0) // WARN 2
                    api.noLongerNecessary3(0) // WARN 3

                    api.unnecessaryInTheFuture1(0) // OK 1
                    api.unnecessaryInTheFuture2(0) // OK 2

                    // The above enforced calls (the most common scenario); check
                    // some other reference types.
                    val clz = Api2::class.java      // WARN 4
                    val method1 = api2::someMethod1 // WARN 5
                    val method2 = api2::someMethod2 // WARN 6
                }
            }

            class Api {
                @DeprecatedSinceApi(api = 21)
                fun noLongerNecessary1(arg: Int) { }

                @DeprecatedSinceApi(api = 23, message = "Use AlarmManager.notify instead")
                fun noLongerNecessary2(arg: Int) { }

                @DeprecatedSinceApi(api = 24)
                fun noLongerNecessary3(arg: Int) { }

                @DeprecatedSinceApi(api = 25)
                fun unnecessaryInTheFuture1(arg: Int) { }

                @DeprecatedSinceApi(api = 33)
                fun unnecessaryInTheFuture2(arg: Int) { }
            }

            @DeprecatedSinceApi(api = 19)
            class Api2 {
                @DeprecatedSinceApi(api = 23)
                fun someMethod1(arg: Int) { }
                @DeprecatedSinceApi(api = 21)
                fun someMethod2(arg: Int) { }
            }

            """
          )
          .indented(),
        deprecatedSdkVersionStub,
      )
      .run()
      .expect(
        """
        src/test/pkg/Test.kt:9: Warning: This method is deprecated as of API level 21 [DeprecatedSinceApi]
                api.noLongerNecessary1(0) // WARN 1
                ~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/Test.kt:10: Warning: This method is deprecated as of API level 23; Use AlarmManager.notify instead [DeprecatedSinceApi]
                api.noLongerNecessary2(0) // WARN 2
                ~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/Test.kt:11: Warning: This method is deprecated as of API level 24 [DeprecatedSinceApi]
                api.noLongerNecessary3(0) // WARN 3
                ~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/Test.kt:18: Warning: This class is deprecated as of API level 19 [DeprecatedSinceApi]
                val clz = Api2::class.java      // WARN 4
                          ~~~~~~~~~~~
        src/test/pkg/Test.kt:19: Warning: This method is deprecated as of API level 23 [DeprecatedSinceApi]
                val method1 = api2::someMethod1 // WARN 5
                              ~~~~~~~~~~~~~~~~~
        src/test/pkg/Test.kt:20: Warning: This method is deprecated as of API level 21 [DeprecatedSinceApi]
                val method2 = api2::someMethod2 // WARN 6
                              ~~~~~~~~~~~~~~~~~
        0 errors, 6 warnings
        """
      )
  }

  fun testMethodOverride() {
    lint()
      .files(
        manifest().minSdk(24),
        kotlin(
            """
            package test.pkg

            import android.content.Intent
            import android.os.IBinder
            import androidx.core.app.NotificationCompatSideChannelService

            abstract class MyNotificationService : NotificationCompatSideChannelService() {
                override fun onBind(intent: Intent?): IBinder? {
                    return super.onBind(intent)
                }
            }
                """
          )
          .indented(),
        // Stubs
        deprecatedSdkVersionStub,
        java(
            """
            /*HIDE-FROM-DOCUMENTATION*/
            package androidx.core.app;
            import android.app.Service;
            import android.content.Intent;
            import android.os.IBinder;
            import androidx.annotation.DeprecatedSinceApi;

            @SuppressWarnings("all")
            public abstract class NotificationCompatSideChannelService extends Service {
                @Override
                @DeprecatedSinceApi(api = 19, message = "SDKs past 19 have no need for side channeling.")
                public IBinder onBind(Intent intent) {
                    return null;
                }
            }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/test/pkg/MyNotificationService.kt:8: Warning: This method is deprecated as of API level 19; SDKs past 19 have no need for side channeling. [DeprecatedSinceApi]
            override fun onBind(intent: Intent?): IBinder? {
                         ~~~~~~
        src/test/pkg/MyNotificationService.kt:9: Warning: This method is deprecated as of API level 19; SDKs past 19 have no need for side channeling. [DeprecatedSinceApi]
                return super.onBind(intent)
                       ~~~~~~~~~~~~~~~~~~~~
        0 errors, 2 warnings
        """
      )
  }
}

val deprecatedSdkVersionStub: TestFile =
  kotlin(
      """
      package androidx.annotation
      import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
      import kotlin.annotation.AnnotationTarget.CLASS
      import kotlin.annotation.AnnotationTarget.CONSTRUCTOR
      import kotlin.annotation.AnnotationTarget.FUNCTION
      import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
      import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER
      @MustBeDocumented
      @Retention(AnnotationRetention.BINARY)
      @Target(FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, ANNOTATION_CLASS, CLASS, CONSTRUCTOR)
      annotation class DeprecatedSinceApi(
          val api: Int,
          val message: String = ""
      )
      """
    )
    .indented()
