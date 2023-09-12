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

import com.android.tools.lint.detector.api.Detector

class SdkSuppressDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return SdkSuppressDetector()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        manifest().minSdk(4),
        kotlin(
            "src/test/java/test/pkg/UnitTestKotlin.kt",
            """
            import android.widget.GridLayout
            import androidx.annotation.RequiresApi

            @RequiresApi(29) // ERROR: don't use in tests, use @SdkSuppress instead
            class UnitTestKotlin {
                private val field1 = GridLayout(null) // OK via @RequiresApiSuppress
            }
            """
          )
          .indented(),
        gradle(
            """
            android {
                lintOptions {
                    checkTestSources true
                }
            }
            """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR
      )
      .run()
      .expect(
        """
        src/test/java/test/pkg/UnitTestKotlin.kt:4: Error: Don't use @RequiresApi from tests; use @SdkSuppress on UnitTestKotlin instead [UseSdkSuppress]
        @RequiresApi(29) // ERROR: don't use in tests, use @SdkSuppress instead
        ~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/java/test/pkg/UnitTestKotlin.kt line 4: Replace with @androidx.test.filters.SdkSuppress(minSdkVersion=29):
        @@ -4 +4
        - @RequiresApi(29) // ERROR: don't use in tests, use @SdkSuppress instead
        + @androidx.test.filters.SdkSuppress(minSdkVersion=29) // ERROR: don't use in tests, use @SdkSuppress instead
        """
      )
  }

  fun testBasic() {
    lint()
      .files(
        manifest().minSdk(4),
        java(
            "src/test/java/test/pkg/UnitTestJava.java",
            """
            package test.pkg;

            import androidx.annotation.RequiresApi;
            import android.widget.GridLayout;

            @RequiresApi(29) // ERROR: don't use in tests, use @SdkSuppress instead
            public class UnitTestJava {
                private GridLayout field1 = new GridLayout(null); // OK via @RequiresApiSuppress
                @androidx.annotation.RequiresApi(api=31) // ERROR: don't use in tests, use @SdkSuppress instead
                public void test() { }
            }
            """
          )
          .indented(),
        kotlin(
            "src/test/java/test/pkg/UnitTestKotlin.kt",
            """
            import android.widget.GridLayout
            import androidx.test.filters.SdkSuppress
            import androidx.annotation.RequiresApi

            @RequiresApi(29) // ERROR: don't use in tests, use @SdkSuppress instead
            class UnitTestKotlin {
                private val field1 = GridLayout(null) // OK via @RequiresApiSuppress

                @RequiresApi(api = 31) // ERROR: don't use in tests, use @SdkSuppress instead
                fun test() {
                }

                @SdkSuppress(minSdkVersion = 31) // OK
                fun test2() {
                }

                @RequiresApi(api = 31) // OK because we only flag annotations on classes & methods
                private val field2 = GridLayout(null)
            }
            """
          )
          .indented(),
        gradle(
            """
            android {
                lintOptions {
                    checkTestSources true
                }
            }
            """
          )
          .indented(),
        // Stub
        java(
            """
            /*HIDE-FROM-DOCUMENTATION*/package androidx.test.filters;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ElementType.TYPE, ElementType.METHOD})
            public @interface SdkSuppress {
              int minSdkVersion() default 1;
              int maxSdkVersion() default Integer.MAX_VALUE;
              int[] excludedSdks() default {};
              String codeName() default "unset";
            }
            """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR
      )
      .run()
      .expect(
        """
        src/test/java/test/pkg/UnitTestJava.java:6: Error: Don't use @RequiresApi from tests; use @SdkSuppress on UnitTestJava instead [UseSdkSuppress]
        @RequiresApi(29) // ERROR: don't use in tests, use @SdkSuppress instead
        ~~~~~~~~~~~~~~~~
        src/test/java/test/pkg/UnitTestJava.java:9: Error: Don't use @RequiresApi from tests; use @SdkSuppress on test instead [UseSdkSuppress]
            @androidx.annotation.RequiresApi(api=31) // ERROR: don't use in tests, use @SdkSuppress instead
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/java/test/pkg/UnitTestKotlin.kt:5: Error: Don't use @RequiresApi from tests; use @SdkSuppress on UnitTestKotlin instead [UseSdkSuppress]
        @RequiresApi(29) // ERROR: don't use in tests, use @SdkSuppress instead
        ~~~~~~~~~~~~~~~~
        src/test/java/test/pkg/UnitTestKotlin.kt:9: Error: Don't use @RequiresApi from tests; use @SdkSuppress on test instead [UseSdkSuppress]
            @RequiresApi(api = 31) // ERROR: don't use in tests, use @SdkSuppress instead
            ~~~~~~~~~~~~~~~~~~~~~~
        4 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/java/test/pkg/UnitTestJava.java line 6: Replace with @androidx.test.filters.SdkSuppress(minSdkVersion=29):
        @@ -6 +6
        - @RequiresApi(29) // ERROR: don't use in tests, use @SdkSuppress instead
        + @androidx.test.filters.SdkSuppress(minSdkVersion=29) // ERROR: don't use in tests, use @SdkSuppress instead
        Fix for src/test/java/test/pkg/UnitTestJava.java line 9: Replace with @androidx.test.filters.SdkSuppress(minSdkVersion=31):
        @@ -9 +9
        -     @androidx.annotation.RequiresApi(api=31) // ERROR: don't use in tests, use @SdkSuppress instead
        +     @androidx.test.filters.SdkSuppress(minSdkVersion=31) // ERROR: don't use in tests, use @SdkSuppress instead
        Fix for src/test/java/test/pkg/UnitTestKotlin.kt line 5: Replace with @androidx.test.filters.SdkSuppress(minSdkVersion=29):
        @@ -5 +5
        - @RequiresApi(29) // ERROR: don't use in tests, use @SdkSuppress instead
        + @SdkSuppress(minSdkVersion=29) // ERROR: don't use in tests, use @SdkSuppress instead
        Fix for src/test/java/test/pkg/UnitTestKotlin.kt line 9: Replace with @androidx.test.filters.SdkSuppress(minSdkVersion=31):
        @@ -9 +9
        -     @RequiresApi(api = 31) // ERROR: don't use in tests, use @SdkSuppress instead
        +     @SdkSuppress(minSdkVersion=31) // ERROR: don't use in tests, use @SdkSuppress instead
        """
      )
  }
}
