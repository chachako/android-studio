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

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector

class ForegroundServicePermissionDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return ForegroundServicePermissionDetector()
  }

  override fun allowCompilationErrors(): Boolean {
    // Some of these unit tests are still relying on source code that references
    // unresolved symbols etc.
    return true
  }

  /**
   * foregroundServiceType permission check is only effective when targetSdkVersion >=34. Lower
   * targetSdkVersion does not check permission.
   */
  fun testTargetSdkVersion33() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="33" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="mediaPlayback"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /**
   * If permission "android.permission.FOREGROUND_SERVICE" is missing, foreground service is not
   * allowed, no need to check individual permission if any foregroundServiceType is present.
   */
  fun testMissingForegroundServicePermission() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="mediaPlayback"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** If no "foregroundServiceType" is found, no check needed. */
  fun testMissingForegroundServiceType() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="33" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="dataSync". */
  fun testForegroundServiceTypeDataSyncHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="dataSync"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="dataSync" */
  fun testForegroundServiceTypeDataSyncMissingPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="dataSync"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:dataSync requires permission:[android.permission.FOREGROUND_SERVICE_DATA_SYNC] [ForegroundServicePermission]
        <service
        ^
1 errors, 0 warnings
"""
      )
  }

  /** Test foregroundServiceType="mediaPlayback" */
  fun testForegroundServiceTypeMediaPlaybackHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="mediaPlayback"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="mediaPlayback" */
  fun testForegroundServiceTypeMediaPlaybackMissingPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="mediaPlayback"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:mediaPlayback requires permission:[android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK] [ForegroundServicePermission]
        <service
        ^
1 errors, 0 warnings
"""
      )
  }

  /** Test foregroundServiceType="phoneCall" */
  fun testForegroundServiceTypePhoneCallHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
    <uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="phoneCall"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="phoneCall" */
  fun testForegroundServiceTypePhoneCallMissingPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="phoneCall"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:phoneCall requires permission:[android.permission.FOREGROUND_SERVICE_PHONE_CALL] AND any permission in list:[android.permission.MANAGE_OWN_CALLS] [ForegroundServicePermission]
        <service
        ^
1 errors, 0 warnings
"""
      )
  }

  /** Test foregroundServiceType="location" */
  fun testForegroundServiceTypeLocationHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="location"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="location" */
  fun testForegroundServiceTypeLocationMissingPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="location"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:location requires permission:[android.permission.FOREGROUND_SERVICE_LOCATION] AND any permission in list:[android.permission.ACCESS_COARSE_LOCATION, android.permission.ACCESS_FINE_LOCATION] [ForegroundServicePermission]
        <service
        ^
1 errors, 0 warnings
"""
      )
  }

  /** Test foregroundServiceType="connectedDevice" */
  fun testForegroundServiceTypeConnectedDeviceHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="connectedDevice"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="connectedDevice" */
  fun testForegroundServiceTypeConnectedDeviceMissingPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="connectedDevice"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:connectedDevice requires permission:[android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE] AND any permission in list:[android.permission.BLUETOOTH_ADVERTISE, android.permission.BLUETOOTH_CONNECT, android.permission.BLUETOOTH_SCAN, android.permission.CHANGE_NETWORK_STATE, android.permission.CHANGE_WIFI_STATE, android.permission.CHANGE_WIFI_MULTICAST_STATE, android.permission.NFC, android.permission.TRANSMIT_IR, android.permission.UWB_RANGING] [ForegroundServicePermission]
        <service
        ^
1 errors, 0 warnings
"""
      )
  }

  /** Test foregroundServiceType="mediaProjection" */
  fun testForegroundServiceTypeMediaProjectionHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="mediaProjection"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="mediaProjection" */
  fun testForegroundServiceTypeMediaProjectionMissingPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="mediaProjection"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:mediaProjection requires permission:[android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION] [ForegroundServicePermission]
        <service
        ^
1 errors, 0 warnings
"""
      )
  }

  /** Test foregroundServiceType="camera" */
  fun testForegroundServiceTypeCameraHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-permission android:name="android.permission.CAMERA" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="camera"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /**
   * Test foregroundServiceType="camera" Original name:
   * testForegroundServiceTypeCameraMissingPermissions()
   */
  fun testDocumentationExample() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="camera"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:camera requires permission:[android.permission.FOREGROUND_SERVICE_CAMERA] AND any permission in list:[android.permission.CAMERA, android.permission.SYSTEM_CAMERA] [ForegroundServicePermission]
        <service
        ^
1 errors, 0 warnings
"""
      )
  }

  /** Test foregroundServiceType="microphone" */
  fun testForegroundServiceTypeMicrophoneHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.CAPTURE_AUDIO_OUTPUT" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="microphone"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="microphone" */
  fun testForegroundServiceTypeMicrophoneMissingPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="microphone"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:microphone requires permission:[android.permission.FOREGROUND_SERVICE_MICROPHONE] AND any permission in list:[android.permission.CAPTURE_AUDIO_HOTWORD, android.permission.CAPTURE_AUDIO_OUTPUT, android.permission.CAPTURE_MEDIA_OUTPUT, android.permission.CAPTURE_TUNER_AUDIO_INPUT, android.permission.CAPTURE_VOICE_COMMUNICATION_OUTPUT, android.permission.RECORD_AUDIO] [ForegroundServicePermission]
        <service
        ^
1 errors, 0 warnings
"""
      )
  }

  /** Test foregroundServiceType="health" */
  fun testForegroundServiceTypeHealthHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="health"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="health" */
  fun testForegroundServiceTypeHealthMissingPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="health"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:health requires permission:[android.permission.FOREGROUND_SERVICE_HEALTH] AND any permission in list:[android.permission.ACTIVITY_RECOGNITION, android.permission.BODY_SENSORS, android.permission.HIGH_SAMPLING_RATE_SENSORS] [ForegroundServicePermission]
        <service
        ^
1 errors, 0 warnings
"""
      )
  }

  /** Test foregroundServiceType="remoteMessaging" */
  fun testForegroundServiceTypeRemoteMessagingHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="remoteMessaging"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="remoteMessaging" */
  fun testForegroundServiceTypeRemoteMessagingMissingPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="remoteMessaging"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:remoteMessaging requires permission:[android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING] [ForegroundServicePermission]
        <service
        ^
1 errors, 0 warnings
"""
      )
  }

  /** Test foregroundServiceType="systemExempted" */
  fun testForegroundServiceTypeSystemExemptedHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="systemExempted"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="systemExempted" */
  fun testForegroundServiceTypeSystemExemptedMissingPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="systemExempted"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:systemExempted requires permission:[android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED] AND any permission in list:[android.permission.SCHEDULE_EXACT_ALARM, android.permission.USE_EXACT_ALARM] [ForegroundServicePermission]
        <service
        ^
1 errors, 0 warnings
"""
      )
  }

  /** Test foregroundServiceType="fileManagement" */
  fun testForegroundServiceTypeFileManagementHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_FILE_MANAGEMENT" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="fileManagement"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="fileManagement" */
  fun testForegroundServiceTypeFileManagementdMissingPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="fileManagement"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:fileManagement requires permission:[android.permission.FOREGROUND_SERVICE_FILE_MANAGEMENT] [ForegroundServicePermission]
        <service
        ^
1 errors, 0 warnings
"""
      )
  }

  /** Test foregroundServiceType="specialUse" */
  fun testForegroundServiceTypeSpecialUseHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="specialUse"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="specialUse" */
  fun testForegroundServiceTypeSpecialUseMissingPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="specialUse"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:specialUse requires permission:[android.permission.FOREGROUND_SERVICE_SPECIAL_USE] [ForegroundServicePermission]
        <service
        ^
1 errors, 0 warnings
"""
      )
  }

  /** Test foregroundServiceType="location|camera|microphone" */
  fun testMultipleForegroundServiceTypeHasPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.CAPTURE_AUDIO_OUTPUT" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="location|camera|microphone"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /** Test foregroundServiceType="location|camera|microphone" */
  fun testMultipleForegroundServiceTypeSpecialUseMissingPermissions() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="foo.bar2"
    android:versionCode="1"
    android:versionName="1.0" >

    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name" >
        <service
            android:exported="true"
            android:label="@string/app_name"
            android:name="com.sample.service.serviceClass"
            android:foregroundServiceType="location|camera|microphone"
            android:process=":remote" >
            <intent-filter >
                <action android:name="com.sample.service.serviceClass" >
                </action>
            </intent-filter>
        </service>
    </application>

</manifest>

""",
        ),
        mStrings,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """AndroidManifest.xml:13: Error: foregroundServiceType:camera requires permission:[android.permission.FOREGROUND_SERVICE_CAMERA] AND any permission in list:[android.permission.CAMERA, android.permission.SYSTEM_CAMERA] [ForegroundServicePermission]
        <service
        ^
AndroidManifest.xml:13: Error: foregroundServiceType:location requires permission:[android.permission.FOREGROUND_SERVICE_LOCATION] AND any permission in list:[android.permission.ACCESS_COARSE_LOCATION, android.permission.ACCESS_FINE_LOCATION] [ForegroundServicePermission]
        <service
        ^
AndroidManifest.xml:13: Error: foregroundServiceType:microphone requires permission:[android.permission.FOREGROUND_SERVICE_MICROPHONE] AND any permission in list:[android.permission.CAPTURE_AUDIO_HOTWORD, android.permission.CAPTURE_AUDIO_OUTPUT, android.permission.CAPTURE_MEDIA_OUTPUT, android.permission.CAPTURE_TUNER_AUDIO_INPUT, android.permission.CAPTURE_VOICE_COMMUNICATION_OUTPUT, android.permission.RECORD_AUDIO] [ForegroundServicePermission]
        <service
        ^
3 errors, 0 warnings
"""
      )
  }

  // Sample code
  private val mStrings =
    xml(
      "res/values/strings.xml",
      """<?xml version="1.0" encoding="utf-8"?>
<!-- Copyright (C) 2007 The Android Open Source Project

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
-->

<resources>
    <!-- Home -->
    <string name="home_title">Home Sample</string>
    <string name="show_all_apps">All</string>

    <!-- Home Menus -->
    <string name="menu_wallpaper">Wallpaper</string>
    <string name="menu_search">Search</string>
    <string name="menu_settings">Settings</string>
    <string name="sample" translatable="false">Ignore Me</string>

    <!-- Wallpaper -->
    <string name="wallpaper_instructions">Tap picture to set portrait wallpaper</string>
</resources>

""",
    )
}
