/*
 * Copyright (C) 2015 The Android Open Source Project
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

class AppLinksAutoVerifyDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return AppLinksAutoVerifyDetector()
  }

  fun testOk() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData(
        "http://example.com/.well-known/assetlinks.json", // language=JSON
        """[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.example.helloworld",
    "sha256_cert_fingerprints":
    ["14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5"]
  }
}]""",
      )
      .run()
      .expectClean()
  }

  fun testRedirect() {
    val headers: MutableMap<String, List<String>> = HashMap()
    headers["date"] = listOf("Thu, 01 Dec 2022 00:08:21 GMT")
    headers["content-length"] = listOf("0")
    headers["location"] = listOf("https://links.dropbox.com/.well-known/assetlinks.json")

    // https://issuetracker.google.com/260129624
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.dropbox.links" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="links.dropbox.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData("http://links.dropbox.com/.well-known/assetlinks.json", 301, headers)
      .networkData(
        "https://links.dropbox.com/.well-known/assetlinks.json", // Not the real data from that
                                                                 // link!
        // language=JSON
        """[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.dropbox.links",
    "sha256_cert_fingerprints":
    ["14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5"]
  }
}]""",
      )
      .run()
      .expectClean()
  }

  fun testInvalidPackage() {
    val expected =
      """AndroidManifest.xml:12: Error: This host does not support app links to your app. Checks the Digital Asset Links JSON file: http://example.com/.well-known/assetlinks.json [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings
"""
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData(
        "http://example.com/.well-known/assetlinks.json", // language=JSON
        """[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.example",
    "sha256_cert_fingerprints":
    ["14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5"]
  }
}]""",
      )
      .run()
      .expect(expected)
  }

  fun testNotAppTarget() {
    val expected =
      """AndroidManifest.xml:12: Error: This host does not support app links to your app. Checks the Digital Asset Links JSON file: http://example.com/.well-known/assetlinks.json [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings
"""
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData(
        "http://example.com/.well-known/assetlinks.json", // language=JSON
        """[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "web",
    "package_name": "com.example.helloworld",
    "sha256_cert_fingerprints":
    ["14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5"]
  }
}]""",
      )
      .run()
      .expect(expected)
  }

  fun testHttpResponseError() {
    val expected =
      """AndroidManifest.xml:12: Warning: HTTP request for Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json fails. HTTP response code: 404 [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings
"""
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData("http://example.com/.well-known/assetlinks.json", 404)
      .run()
      .expect(expected)
  }

  fun testFailedHttpConnection() {
    val expected =
      """AndroidManifest.xml:12: Warning: Connection to Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings
"""
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData(
        "http://example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_HTTP_CONNECT_FAIL,
      )
      .run()
      .expect(expected)
  }

  fun testMalformedUrl() {
    val expected =
      """AndroidManifest.xml:12: Error: Malformed URL of Digital Asset Links JSON file: http://example.com/.well-known/assetlinks.json. An unknown protocol is specified [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings
"""
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData(
        "http://example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_MALFORMED_URL,
      )
      .run()
      .expect(expected)
  }

  fun testUnknownHost() {
    val expected =
      """AndroidManifest.xml:12: Warning: Unknown host: http://example.com. Check if the host exists, and check your network connection [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings
"""
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData(
        "http://example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST,
      )
      .run()
      .expect(expected)
  }

  fun testNotFound() {
    val expected =
      """AndroidManifest.xml:12: Error: Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json is not found on the host [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings
"""
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData(
        "http://example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_NOT_FOUND,
      )
      .run()
      .expect(expected)
  }

  fun testWrongJsonSyntax() {
    val expected =
      """AndroidManifest.xml:12: Error: http://example.com/.well-known/assetlinks.json has incorrect JSON syntax [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings
"""
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData(
        "http://example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_WRONG_JSON_SYNTAX,
      )
      .run()
      .expect(expected)
  }

  fun testFailedJsonParsing() {
    val expected =
      """AndroidManifest.xml:12: Error: Parsing JSON file http://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings
"""
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData(
        "http://example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_JSON_PARSE_FAIL,
      )
      .run()
      .expect(expected)
  }

  fun testNoAutoVerify() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .run()
      .expectClean()
  }

  fun testNotAppLinkInIntents() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .run()
      .expectClean()
  }

  fun testMultipleLinks() {
    val expected =
      """AndroidManifest.xml:12: Error: Digital Asset Links JSON file https://example.com/.well-known/assetlinks.json is not found on the host [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
AndroidManifest.xml:15: Error: https://www.example.com/.well-known/assetlinks.json has incorrect JSON syntax [AppLinksAutoVerify]
                <data android:host="www.example.com" />
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
AndroidManifest.xml:12: Warning: Connection to Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
AndroidManifest.xml:15: Warning: Unknown host: http://www.example.com. Check if the host exists, and check your network connection [AppLinksAutoVerify]
                <data android:host="www.example.com" />
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
2 errors, 2 warnings
"""
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <data android:scheme="https" />
                <data android:host="www.example.com" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData(
        "http://example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_HTTP_CONNECT_FAIL,
      )
      .networkData(
        "https://example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_NOT_FOUND,
      )
      .networkData(
        "http://www.example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST,
      )
      .networkData(
        "https://www.example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_WRONG_JSON_SYNTAX,
      )
      .run()
      .expect(expected)
  }

  fun testMultipleIntents() {
    val expected =
      """AndroidManifest.xml:12: Warning: Unknown host: http://www.example.com. Check if the host exists, and check your network connection [AppLinksAutoVerify]
                    android:host="www.example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
AndroidManifest.xml:20: Warning: Connection to Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 2 warnings
"""
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="www.example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="http"
                    android:host="example.com"
                    android:pathPrefix="/gizmos" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData(
        "http://example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_HTTP_CONNECT_FAIL,
      )
      .networkData(
        "http://www.example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST,
      )
      .run()
      .expect(expected)
  }

  fun testUnknownHostWithManifestPlaceholders() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=205990
    // Skip hosts that use manifest placeholders
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data
                    android:host="＄{intentFilterHost}"
                    android:pathPrefix="/path/"
                    android:scheme="https" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",
        )
      )
      .networkData(
        "http://example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST,
      )
      .run()
      .expectClean()
  }

  fun testUnknownHostWithResolvedManifestPlaceholders() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=205990
    // Skip hosts that use manifest placeholders
    val expected =
      """src/main/AndroidManifest.xml:12: Warning: Unknown host: http://example.com. Check if the host exists, and check your network connection [AppLinksAutoVerify]
                    android:host="${"$"}{intentFilterHost}"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings
"""

    lint()
      .files(
        manifest(
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.helloworld" >

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher" >
        <activity android:name=".MainActivity" >
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <data
                    android:host="＄{intentFilterHost}"
                    android:pathPrefix="/gizmos/"
                    android:scheme="http" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
        </activity>
    </application>

</manifest>
"""
        ),
        gradle(
          """buildscript {
    dependencies {
        classpath 'com.android.tools.build:gradle:2.0.0'
    }
}
android {
    defaultConfig {
        manifestPlaceholders = [ intentFilterHost:"example.com"]
    }
}
"""
        ),
      )
      .networkData(
        "http://example.com/.well-known/assetlinks.json",
        AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST,
      )
      .run()
      .expect(expected)
  }
}
