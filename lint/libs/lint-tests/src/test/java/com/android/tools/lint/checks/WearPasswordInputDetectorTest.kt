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

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.manifest
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.detector.api.Detector

private val INPUT_TYPE_TEXT =
  xml(
      "res/layout/main.xml",
      """
          <?xml version="1.0" encoding="utf-8"?>
          <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              android:layout_width="match_parent"
              android:layout_height="match_parent">
            <EditText
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:autofillHints=""
                android:inputType="text"
                tools:ignore="LabelFor" />
          </LinearLayout>
    """
    )
    .indented()

private val INPUT_TYPE_PASSWORD =
  xml(
      "res/layout/main.xml",
      """
          <?xml version="1.0" encoding="utf-8"?>
          <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              android:layout_width="match_parent"
              android:layout_height="match_parent">
            <EditText
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:autofillHints=""
                android:inputType="textPassword"
                tools:ignore="LabelFor" />
          </LinearLayout>
    """
    )
    .indented()

private val KOTLIN_INPUT_TYPE_NORMAL =
  kotlin(
      """
          package com.example.application

          import android.text.InputType
          import android.widget.EditText

          fun normalEditTextCreator() {
            val editText = EditText()
            editText.inputType = InputType.TYPE_TEXT_VARIATION_NORMAL
          }
      """
    )
    .indented()

private val KOTLIN_INPUT_TYPE_PASSWORD =
  kotlin(
      """
          package com.example.application

          import android.text.InputType
          import android.widget.EditText

          fun passwordEditTextCreator() {
            val editText = EditText()
            editText.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
          }
      """
    )
    .indented()

private val JAVA_INPUT_TYPE_NORMAL =
  java(
      """
          package com.example.application;

          import android.text.InputType;
          import android.widget.EditText;
          import android.widget.TextView;

          class MainActivity {

            MainActivity() {
              TextView textView = new TextView();
              textView.setInputType(InputType.TYPE_TEXT_VARIATION_NORMAL);

              EditText editText = new EditText();
              editText.setInputType(InputType.TYPE_NUMBER_VARIATION_NORMAL);
            }
          }
      """
    )
    .indented()

private val JAVA_INPUT_TYPE_PASSWORD =
  java(
      """
          package com.example.application;

          import android.text.InputType;
          import android.widget.EditText;
          import android.widget.TextView;

          class MainActivity {

            MainActivity() {
              TextView textView = new TextView();
              textView.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);

              textView.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

              EditText editText = new EditText();
              editText.setInputType(InputType.TYPE_NUMBER_VARIATION_PASSWORD);

              editText.setInputType(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
            }
          }
      """
    )
    .indented()

private val NON_WEAR_MANIFEST =
  manifest(
      """
          <manifest xmlns:android="http://schemas.android.com/apk/res/android"
               package="com.example.helloworld"
               android:versionCode="1"
               android:versionName="1.0">
             <uses-sdk android:minSdkVersion="30" />
             <application android:icon="@drawable/icon" android:label="@string/app_name" />
          </manifest>
      """
    )
    .indented()

private val WEAR_MANIFEST =
  manifest(
      """
          <manifest xmlns:android="http://schemas.android.com/apk/res/android"
               package="com.example.helloworld"
               android:versionCode="1"
               android:versionName="1.0">
             <uses-sdk android:minSdkVersion="30" />
             <uses-feature android:name="android.hardware.type.watch" />
             <application android:icon="@drawable/icon" android:label="@string/app_name" />
          </manifest>
      """
    )
    .indented()

class WearPasswordInputDetectorTest : AbstractCheckTest() {

  override fun getDetector(): Detector = WearPasswordInputDetector()

  fun testDocumentationExample() {
    lint()
      .files(INPUT_TYPE_PASSWORD, WEAR_MANIFEST)
      .run()
      .expect(
        """
        res/layout/main.xml:10: Error: Don't ask Wear OS users for a password [WearPasswordInput]
              android:inputType="textPassword"
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
      """
      )
  }

  fun testInputTypeTextOnWear() {
    lint().files(INPUT_TYPE_TEXT, WEAR_MANIFEST).run().expectClean()
  }

  fun testInputTypeTextOnNonWear() {
    lint().files(INPUT_TYPE_TEXT, NON_WEAR_MANIFEST).run().expectClean()
  }

  fun testInputTypePasswordOnNonWear() {
    lint().files(INPUT_TYPE_PASSWORD, NON_WEAR_MANIFEST).run().expectClean()
  }

  fun testKotlinInputTypePasswordOnWear() {
    lint()
      .files(KOTLIN_INPUT_TYPE_PASSWORD, WEAR_MANIFEST)
      .run()
      .expect(
        """
          src/com/example/application/test.kt:8: Error: Don't ask Wear OS users for a password [WearPasswordInput]
            editText.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          1 errors, 0 warnings"""
      )
  }

  fun testKotlinInputTypeNormalOnWear() {
    lint().files(KOTLIN_INPUT_TYPE_NORMAL, WEAR_MANIFEST).run().expectClean()
  }

  fun testKotlinInputTypeNormalOnNonWear() {
    lint().files(KOTLIN_INPUT_TYPE_NORMAL, NON_WEAR_MANIFEST).run().expectClean()
  }

  fun testKotlinInputTypePasswordOnNonWear() {
    lint().files(KOTLIN_INPUT_TYPE_PASSWORD, NON_WEAR_MANIFEST).run().expectClean()
  }

  fun testJavaInputTypePasswordOnWear() {
    lint()
      .files(JAVA_INPUT_TYPE_PASSWORD, WEAR_MANIFEST)
      .run()
      .expect(
        """
        src/com/example/application/MainActivity.java:11: Error: Don't ask Wear OS users for a password [WearPasswordInput]
            textView.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/com/example/application/MainActivity.java:13: Error: Don't ask Wear OS users for a password [WearPasswordInput]
            textView.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/com/example/application/MainActivity.java:16: Error: Don't ask Wear OS users for a password [WearPasswordInput]
            editText.setInputType(InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/com/example/application/MainActivity.java:18: Error: Don't ask Wear OS users for a password [WearPasswordInput]
            editText.setInputType(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        4 errors, 0 warnings
      """
      )
  }

  fun testJavaInputTypeNormalOnWear() {
    lint().files(JAVA_INPUT_TYPE_NORMAL, WEAR_MANIFEST).run().expectClean()
  }

  fun testJavaInputTypeNormalOnNonWear() {
    lint().files(JAVA_INPUT_TYPE_NORMAL, NON_WEAR_MANIFEST).run().expectClean()
  }

  fun testJavaInputTypePasswordOnNonWear() {
    lint().files(JAVA_INPUT_TYPE_PASSWORD, NON_WEAR_MANIFEST).run().expectClean()
  }
}
