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

import com.android.tools.lint.detector.api.Detector

class RequiredFeatureDetectorTest : AbstractCheckTest() {

  override fun getDetector(): Detector {
    return RequiredFeatureDetector()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="test.pkg">

                <uses-sdk android:minSdkVersion="28" />
                <uses-feature android:name="android.hardware.type.watch" /> <!-- OK -->
                <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" /> <!-- OK -->
                <uses-feature android:name="android.hardware.camera.autofocus" /> <!-- WARN 1 -->
                <uses-feature android:name="android.hardware.camera.flash" /> <!-- WARN 2 -->
                <uses-feature android:name="android.hardware.location.gps" /> <!-- WARN 3 -->
                <uses-feature android:name="android.hardware.nfc" /> <!-- WARN 4 -->
                <uses-feature android:name="android.hardware.nfc.hce" /> <!-- WARN 5 -->
                <uses-feature android:name="android.hardware.telephony" /> <!-- WARN 6 -->
                <uses-feature android:name="android.hardware.touchscreen" /> <!-- WARN 7 -->
                <uses-feature android:name="android.hardware.touchscreen.multitouch" /> <!-- WARN 8 -->
                <uses-feature android:name="android.hardware.screen.portrait" /> <!-- WARN 9 -->
                <uses-feature android:name="android.hardware.screen.portrait" android:required="true" /> <!-- WARN 10 -->

                <application
                    android:icon="@drawable/ic_launcher"
                    android:label="@string/app_name" >
                    <activity
                        android:name=".BytecodeTestsActivity"
                        android:label="@string/app_name" >
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />

                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>

            </manifest>
            """
          )
          .indented()
      )
      .run()
      .expect(
        """
        AndroidManifest.xml:7: Warning: Consider whether this feature (android.hardware.camera.autofocus) really is required for the app to function; you can set android:required="false" to indicate that the feature is used but not required [UnnecessaryRequiredFeature]
            <uses-feature android:name="android.hardware.camera.autofocus" /> <!-- WARN 1 -->
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        AndroidManifest.xml:8: Warning: Consider whether this feature (android.hardware.camera.flash) really is required for the app to function; you can set android:required="false" to indicate that the feature is used but not required [UnnecessaryRequiredFeature]
            <uses-feature android:name="android.hardware.camera.flash" /> <!-- WARN 2 -->
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        AndroidManifest.xml:9: Warning: Consider whether this feature (android.hardware.location.gps) really is required for the app to function; you can set android:required="false" to indicate that the feature is used but not required [UnnecessaryRequiredFeature]
            <uses-feature android:name="android.hardware.location.gps" /> <!-- WARN 3 -->
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        AndroidManifest.xml:10: Warning: Consider whether this feature (android.hardware.nfc) really is required for the app to function; you can set android:required="false" to indicate that the feature is used but not required [UnnecessaryRequiredFeature]
            <uses-feature android:name="android.hardware.nfc" /> <!-- WARN 4 -->
                                        ~~~~~~~~~~~~~~~~~~~~
        AndroidManifest.xml:11: Warning: Consider whether this feature (android.hardware.nfc.hce) really is required for the app to function; you can set android:required="false" to indicate that the feature is used but not required [UnnecessaryRequiredFeature]
            <uses-feature android:name="android.hardware.nfc.hce" /> <!-- WARN 5 -->
                                        ~~~~~~~~~~~~~~~~~~~~~~~~
        AndroidManifest.xml:12: Warning: Consider whether this feature (android.hardware.telephony) really is required for the app to function; you can set android:required="false" to indicate that the feature is used but not required [UnnecessaryRequiredFeature]
            <uses-feature android:name="android.hardware.telephony" /> <!-- WARN 6 -->
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
        AndroidManifest.xml:13: Warning: Consider whether this feature (android.hardware.touchscreen) really is required for the app to function; you can set android:required="false" to indicate that the feature is used but not required [UnnecessaryRequiredFeature]
            <uses-feature android:name="android.hardware.touchscreen" /> <!-- WARN 7 -->
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        AndroidManifest.xml:14: Warning: Consider whether this feature (android.hardware.touchscreen.multitouch) really is required for the app to function; you can set android:required="false" to indicate that the feature is used but not required [UnnecessaryRequiredFeature]
            <uses-feature android:name="android.hardware.touchscreen.multitouch" /> <!-- WARN 8 -->
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        AndroidManifest.xml:15: Warning: Consider whether this feature (android.hardware.screen.portrait) really is required for the app to function; you can set android:required="false" to indicate that the feature is used but not required [UnnecessaryRequiredFeature]
            <uses-feature android:name="android.hardware.screen.portrait" /> <!-- WARN 9 -->
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        AndroidManifest.xml:16: Warning: Consider whether this feature (android.hardware.screen.portrait) really is required for the app to function; you can set android:required="false" to indicate that the feature is used but not required [UnnecessaryRequiredFeature]
            <uses-feature android:name="android.hardware.screen.portrait" android:required="true" /> <!-- WARN 10 -->
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 10 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for AndroidManifest.xml line 7: Set required="false":
        @@ -11 +11
        -     <uses-feature android:name="android.hardware.camera.autofocus" /> <!-- WARN 1 -->
        +     <uses-feature
        +         android:name="android.hardware.camera.autofocus"
        +         android:required="false" /> <!-- WARN 1 -->
        Fix for AndroidManifest.xml line 8: Set required="false":
        @@ -12 +12
        -     <uses-feature android:name="android.hardware.camera.flash" /> <!-- WARN 2 -->
        +     <uses-feature
        +         android:name="android.hardware.camera.flash"
        +         android:required="false" /> <!-- WARN 2 -->
        Fix for AndroidManifest.xml line 9: Set required="false":
        @@ -13 +13
        -     <uses-feature android:name="android.hardware.location.gps" /> <!-- WARN 3 -->
        +     <uses-feature
        +         android:name="android.hardware.location.gps"
        +         android:required="false" /> <!-- WARN 3 -->
        Fix for AndroidManifest.xml line 10: Set required="false":
        @@ -14 +14
        -     <uses-feature android:name="android.hardware.nfc" /> <!-- WARN 4 -->
        +     <uses-feature
        +         android:name="android.hardware.nfc"
        +         android:required="false" /> <!-- WARN 4 -->
        Fix for AndroidManifest.xml line 11: Set required="false":
        @@ -15 +15
        -     <uses-feature android:name="android.hardware.nfc.hce" /> <!-- WARN 5 -->
        +     <uses-feature
        +         android:name="android.hardware.nfc.hce"
        +         android:required="false" /> <!-- WARN 5 -->
        Fix for AndroidManifest.xml line 12: Set required="false":
        @@ -16 +16
        -     <uses-feature android:name="android.hardware.telephony" /> <!-- WARN 6 -->
        +     <uses-feature
        +         android:name="android.hardware.telephony"
        +         android:required="false" /> <!-- WARN 6 -->
        Fix for AndroidManifest.xml line 13: Set required="false":
        @@ -17 +17
        -     <uses-feature android:name="android.hardware.touchscreen" /> <!-- WARN 7 -->
        +     <uses-feature
        +         android:name="android.hardware.touchscreen"
        +         android:required="false" /> <!-- WARN 7 -->
        Fix for AndroidManifest.xml line 14: Set required="false":
        @@ -18 +18
        -     <uses-feature android:name="android.hardware.touchscreen.multitouch" /> <!-- WARN 8 -->
        +     <uses-feature
        +         android:name="android.hardware.touchscreen.multitouch"
        +         android:required="false" /> <!-- WARN 8 -->
        Fix for AndroidManifest.xml line 15: Set required="false":
        @@ -19 +19
        -     <uses-feature android:name="android.hardware.screen.portrait" /> <!-- WARN 9 -->
        @@ -22 +21
        +         android:required="false" /> <!-- WARN 9 -->
        +     <uses-feature
        +         android:name="android.hardware.screen.portrait"
        Fix for AndroidManifest.xml line 16: Set required="false":
        @@ -22 +22
        -         android:required="true" /> <!-- WARN 10 -->
        +         android:required="false" /> <!-- WARN 10 -->
        """
      )
  }
}
