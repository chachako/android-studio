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

import com.android.tools.lint.checks.CredentialManagerDependencyDetector.Companion.CREDENTIAL_DEP
import org.intellij.lang.annotations.Language

class CredentialManagerDependencyDetectorTest : AbstractCheckTest() {
  override fun getDetector() = CredentialManagerDependencyDetector()

  fun testDocumentationExample() {
    lint()
      .files(
        manifest(MANIFEST_MIN_ANDROID_13).indented(),
        // Only Gradle DSL (not kts) seems to allow project.dependsOn(...) to work in unit tests.
        gradle(
            """
            dependencies {
                implementation 'androidx.credentials:credentials:+'
            }
            """
          )
          .indented(),
      )
      .issues(CREDENTIAL_DEP)
      .run()
      .expect(
        """
        src/main/AndroidManifest.xml:5: Warning: This app supports Android 13 and depends on androidx.credentials:credentials, and so should also depend on androidx.credentials:credentials-play-services-auth [CredentialDependency]
                <application>
                 ~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testHasPlayServicesDependency() {
    lint()
      .files(
        manifest(MANIFEST_MIN_ANDROID_13).indented(),
        gradle(
            """
            dependencies {
                implementation 'androidx.credentials:credentials:+'
                implementation 'androidx.credentials:credentials-play-services-auth:+'
            }
            """
          )
          .indented(),
      )
      .issues(CREDENTIAL_DEP)
      .run()
      .expectClean()
  }

  fun testAndroid14ApiLevel() {
    lint()
      .files(
        manifest(MANIFEST_MIN_ANDROID_14),
        gradle(
            """
            dependencies {
                implementation 'androidx.credentials:credentials:+'
            }
            """
          )
          .indented(),
      )
      .issues(CREDENTIAL_DEP)
      .run()
      .expectClean()
  }

  companion object {
    // We use manually written manifests because we want an <application> tag.
    @Language("XML")
    const val MANIFEST_MIN_ANDROID_13 =
      """<?xml version="1.0" encoding="utf-8"?>
      <!-- HIDE-FROM-DOCUMENTATION -->
      <manifest package="com.example.app" xmlns:android="http://schemas.android.com/apk/res/android">
        <uses-sdk android:minSdkVersion="33" android:targetSdkVersion="34" />
        <application>
          <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
              <action android:name="android.intent.action.MAIN" />
              <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
          </activity>
        </application>
      </manifest>
      """

    @Language("XML")
    const val MANIFEST_MIN_ANDROID_14 =
      """<?xml version="1.0" encoding="utf-8"?>
      <!-- HIDE-FROM-DOCUMENTATION -->
      <manifest package="com.example.app" xmlns:android="http://schemas.android.com/apk/res/android">
        <uses-sdk android:minSdkVersion="34" android:targetSdkVersion="34" />
        <application>
          <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
              <action android:name="android.intent.action.MAIN" />
              <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
          </activity>
        </application>
      </manifest>
      """
  }
}
