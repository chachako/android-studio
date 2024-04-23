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

import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.UnknownHostException
import org.intellij.lang.annotations.Language

class AppLinksAutoVerifyDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return AppLinksAutoVerifyDetector()
  }

  private fun TestLintTask.networkDataJson(
    url: String,
    @Language("JSON") data: String,
  ): TestLintTask {
    return this.networkData(url, data)
      .networkData(
        url,
        HttpURLConnection.HTTP_OK,
        mapOf("Content-Type" to listOf("application/json")),
      )
  }

  /**
   * The same as [networkDataJson] but without the language annotation, so we can write invalid JSON
   * without warnings.
   */
  private fun TestLintTask.networkDataJsonInvalid(url: String, data: String): TestLintTask {
    return this.networkDataJson(url, data)
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
      .networkDataJson(
        "https://example.com/.well-known/assetlinks.json",
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
    headers["location"] = listOf("https://links.dropbox.com/.well-known/other_assetlinks.json")
    // There must not be any redirects.
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
      .networkData("https://links.dropbox.com/.well-known/assetlinks.json", 301, headers)
      .run()
      .expect(
        """AndroidManifest.xml:12: Warning: HTTP request for Digital Asset Links JSON file https://links.dropbox.com/.well-known/assetlinks.json fails. HTTP response code: 301 [AppLinksAutoVerify]
                    android:host="links.dropbox.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings"""
      )
  }

  fun testInvalidPackage() {
    val expected =
      """AndroidManifest.xml:12: Error: This host does not support app links to your app. Checks the Digital Asset Links JSON file: https://example.com/.well-known/assetlinks.json [AppLinksAutoVerify]
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
      .networkDataJson(
        "https://example.com/.well-known/assetlinks.json",
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
      """AndroidManifest.xml:12: Error: This host does not support app links to your app. Checks the Digital Asset Links JSON file: https://example.com/.well-known/assetlinks.json [AppLinksAutoVerify]
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
      .networkDataJson(
        "https://example.com/.well-known/assetlinks.json",
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
      """AndroidManifest.xml:12: Warning: HTTP request for Digital Asset Links JSON file https://example.com/.well-known/assetlinks.json fails. HTTP response code: 404 [AppLinksAutoVerify]
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
      .networkData("https://example.com/.well-known/assetlinks.json", 404)
      .run()
      .expect(expected)
  }

  fun testFailedHttpConnection() {
    val expected =
      """AndroidManifest.xml:12: Warning: Connection to Digital Asset Links JSON file https://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerify]
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
      .networkData("https://example.com/.well-known/assetlinks.json", IOException())
      .run()
      .expect(expected)
  }

  fun testMalformedUrl() {
    val expected =
      """AndroidManifest.xml:12: Error: Malformed URL of Digital Asset Links JSON file: https://example.com/.well-known/assetlinks.json. An unknown protocol is specified [AppLinksAutoVerify]
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
      .networkData("https://example.com/.well-known/assetlinks.json", MalformedURLException())
      .run()
      .expect(expected)
  }

  fun testUnknownHost() {
    val expected =
      """AndroidManifest.xml:12: Warning: Unknown host: https://example.com. Check if the host exists, and check your network connection [AppLinksAutoVerify]
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
      .networkData("https://example.com/.well-known/assetlinks.json", UnknownHostException())
      .run()
      .expect(expected)
  }

  fun testNotFound() {
    val expected =
      """AndroidManifest.xml:12: Error: Digital Asset Links JSON file https://example.com/.well-known/assetlinks.json is not found on the host [AppLinksAutoVerify]
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
      .networkData("https://example.com/.well-known/assetlinks.json", FileNotFoundException())
      .run()
      .expect(expected)
  }

  fun testWrongJsonSyntax() {
    val expected =
      """AndroidManifest.xml:12: Error: https://example.com/.well-known/assetlinks.json has incorrect JSON syntax [AppLinksAutoVerify]
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
      .networkDataJsonInvalid("https://example.com/.well-known/assetlinks.json", "[")
      .allowCompilationErrors()
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
      """AndroidManifest.xml:12: Warning: Connection to Digital Asset Links JSON file https://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
AndroidManifest.xml:15: Warning: Unknown host: https://www.example.com. Check if the host exists, and check your network connection [AppLinksAutoVerify]
                <data android:host="www.example.com" />
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
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
      .networkData("https://example.com/.well-known/assetlinks.json", IOException())
      .networkData("https://www.example.com/.well-known/assetlinks.json", UnknownHostException())
      .run()
      .expect(expected)
  }

  fun testMultipleIntents() {
    val expected =
      """AndroidManifest.xml:12: Warning: Unknown host: https://www.example.com. Check if the host exists, and check your network connection [AppLinksAutoVerify]
                    android:host="www.example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
AndroidManifest.xml:20: Warning: Connection to Digital Asset Links JSON file https://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerify]
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
            <intent-filter android:autoVerify="true">
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
      .networkData("https://example.com/.well-known/assetlinks.json", IOException())
      .networkData("https://www.example.com/.well-known/assetlinks.json", UnknownHostException())
      .run()
      .expect(expected)
  }

  fun testUnknownHostWithManifestPlaceholders() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=205990
    // Skip hosts that use manifest placeholders

    // Note: the check has changed to use the merged manifest, but we still skip
    // hosts if we see a placeholder, just in case.
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
      .run()
      .expectClean()
  }

  fun testBadContentType() {
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
        "https://example.com/.well-known/assetlinks.json",
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
      .expect(
        """AndroidManifest.xml:12: Warning: HTTP response for Digital Asset Links JSON file https://example.com/.well-known/assetlinks.json should have Content-Type application/json, but has null [AppLinksAutoVerify]
                    android:host="example.com"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings"""
      )
  }

  fun testWildcard() {
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
                    android:host="*.example.com"
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
      .networkDataJson(
        "https://example.com/.well-known/assetlinks.json",
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
}
