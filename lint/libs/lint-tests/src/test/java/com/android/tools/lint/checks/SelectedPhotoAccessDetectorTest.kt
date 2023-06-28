/*
 * Copyright (C) 2016 The Android Open Source Project
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

class SelectedPhotoAccessDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return SelectedPhotoAccessDetector()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        manifest(
            """
                  <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="foo.bar"
                    android:versionCode="990000000"
                    android:versionName="9.1.0.0.0x">

                    <uses-sdk android:minSdkVersion="14" android:targetSdkVersion="34" />

                    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>
                    <!-- Media permissions introduced in Android 13 (T) -->
                    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" android:minSdkVersion="33" />
                    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" android:minSdkVersion="33" />
                    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" android:minSdkVersion="33"/>
                    <uses-permission android:name="android.permission.CAMERA" />

                    <application
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name"
                            android:permission="android.permission.READ_CONTACTS">
                    </application>

                </manifest>

                """
          )
          .indented()
      )
      .run()
      .expect(
        """
              AndroidManifest.xml:11: Warning: Your app is currently not handling Selected Photos Access introduced in Android 14+ [SelectedPhotoAccess]
                  <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" android:minSdkVersion="33" />
                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
              AndroidManifest.xml:13: Warning: Your app is currently not handling Selected Photos Access introduced in Android 14+ [SelectedPhotoAccess]
                  <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" android:minSdkVersion="33"/>
                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
              0 errors, 2 warnings
            """
      )
  }

  fun testPermissionIsPresent() {
    lint()
      .files(
        manifest(
            """
                  <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="foo.bar"
                    android:versionCode="990000000"
                    android:versionName="9.1.0.0.0x">

                    <uses-sdk android:minSdkVersion="14" android:targetSdkVersion="33" />

                    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>

                    <!-- Media permissions introduced in Android 13 (T) -->
                    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
                    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
                    <uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />

                </manifest>

                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testSdk32() {
    lint()
      .files(
        manifest(
            """
                  <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="foo.bar"
                    android:versionCode="990000000"
                    android:versionName="9.1.0.0.0x">

                    <uses-sdk android:minSdkVersion="14" android:targetSdkVersion="32" />

                    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>

                    <!-- Media permissions introduced in Android 13 (T) -->
                    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
                    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />

                </manifest>

                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }
}
