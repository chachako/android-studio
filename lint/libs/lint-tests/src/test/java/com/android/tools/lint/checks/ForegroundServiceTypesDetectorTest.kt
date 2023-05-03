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

class ForegroundServiceTypesDetectorTest : AbstractCheckTest() {
  /**
   * In MY_SERVICE file, startForeground() is a member in subclass of "android.app.Service" class
   * (isMemberInSubClassOf() returns true).
   */
  private val MY_SERVICE =
    java(
      """package test.pkg;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
public class MyService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, null);
        return START_NOT_STICKY;
      }
      @Override
      public IBinder onBind(Intent intent) {
        return null;
      }
}
"""
    )

  /**
   * In MY_SERVICE_COMPAT file, androidx.core.app.ServiceCompat.startForeground() is called (instead
   * of android.app.Service.startForeground), startForeground() is a member of
   * "androidx.core.app.ServiceCompat" class (isMemberInClass() returns true).
   */
  private val MY_SERVICE_COMPAT =
    java(
      """package test.pkg;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.core.app.ServiceCompat;

public class MyService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ServiceCompat.startForeground(this, 1, null, 8);
        return START_NOT_STICKY;
      }
      @Override
      public IBinder onBind(Intent intent) {
        return null;
      }
}
"""
    )

  /**
   * In MY_CLASS, startForeground() is not member of subclass of "android.app.Service" class
   * (isMemberInSubClassOf() returns false).
   */
  private val MY_CLASS =
    java(
      """package test.pkg;
import android.content.Intent;
public class MyClass {
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, null);
        return START_NOT_STICKY;
      }
      public void startForeground(int i, Object object) {;}
}
"""
    )

  override fun getDetector(): Detector {
    return ForegroundServiceTypesDetector()
  }

  override fun allowCompilationErrors(): Boolean {
    // Some of these unit tests are still relying on source code that references
    // unresolved symbols etc.
    return true
  }

  /**
   * Manifest file's <service> element does not have foregroundServiceType attribute, lint reports
   * error. Original name: testStartForegroundMissingType
   */
  fun testDocumentationExample() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="test.pkg">
    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <application>
        <service
            android:exported="true"
            android:name="test.pkg.MyService">
        </service>
    </application>
</manifest>

"""
        ),
        MY_SERVICE
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """src/test/pkg/MyService.java:8: Error: To call Service.startForeground(), the <service> element of manifest file must have the foregroundServiceType attribute specified [ForegroundServiceType]
        startForeground(1, null);
        ~~~~~~~~~~~~~~~
1 errors, 0 warnings
"""
      )
  }

  /** Manifest file's targetSdkVersion is 33 (less than 34), lint does not report error. */
  fun testTargetSdkVersion33() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="test.pkg">
    <uses-sdk android:targetSdkVersion="33" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <application>
        <service
            android:exported="true"
            android:name="test.pkg.MyService">
        </service>
    </application>
</manifest>

"""
        ),
        MY_SERVICE
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /**
   * Manifest file's <service> element has foregroundServiceType attribute, lint does not report
   * error.
   */
  fun testStartForegroundHasType() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="test.pkg">
    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <application>
        <service
            android:exported="true"
            android:name="test.pkg.MyService"
            android:foregroundServiceType="location">
        </service>
    </application>
</manifest>

"""
        ),
        MY_SERVICE
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /**
   * The startForeground() is not a member in subclass of "android.app.Service" class, lint does not
   * report error.
   */
  fun testStartForegroundOutOfService() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="test.pkg">
    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <application>
        <service
            android:exported="true"
            android:name="test.pkg.MyService">
        </service>
    </application>
</manifest>

"""
        ),
        MY_CLASS
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /**
   * Manifest file does not have <service> element, lint does not report error. If a library module
   * contains a call to startForeground but does not contain any <service> tags (because the code is
   * designed to be consumed and/or extended by other modules that will declare the <service> tags)
   * then we don't force developers to add <service> tags to the library module's manifest.
   */
  fun testStartForegroundNoServiceElement() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="test.pkg">
    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <application>
    </application>
</manifest>

"""
        ),
        MY_SERVICE
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  /**
   * androidx.core.app.ServiceCompat.startForeground() is called (instead of
   * android.app.Service.startForeground), manifest file's <service> element does not have
   * foregroundServiceType attribute, lint reports error.
   */
  fun testStartForegroundFromServiceCompatMissingType() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="test.pkg">
    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <application>
        <service
            android:exported="true"
            android:name="test.pkg.MyService">
        </service>
    </application>
</manifest>

"""
        ),
        MY_SERVICE_COMPAT,
        *serviceCompatStubs
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """src/test/pkg/MyService.java:10: Error: To call Service.startForeground(), the <service> element of manifest file must have the foregroundServiceType attribute specified [ForegroundServiceType]
        ServiceCompat.startForeground(this, 1, null, 8);
                      ~~~~~~~~~~~~~~~
1 errors, 0 warnings
"""
      )
  }

  /**
   * androidx.core.app.ServiceCompat.startForeground() is called (instead of
   * android.app.Service.startForeground). Manifest file's <service> element has
   * foregroundServiceType attribute, lint does not report error.
   */
  fun testStartForegroundFromServiceCompatHasType() {
    lint()
      .files(
        xml(
          "AndroidManifest.xml",
          """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="test.pkg">
    <uses-sdk android:targetSdkVersion="34" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <application>
        <service
            android:exported="true"
            android:name="test.pkg.MyService"
            android:foregroundServiceType="location">
        </service>
    </application>
</manifest>

"""
        ),
        MY_SERVICE_COMPAT,
        *serviceCompatStubs
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  companion object {
    val serviceCompatStubs =
      arrayOf(
        java(
            """
                package androidx.core.app;
                import android.app.Notification;
                import android.app.Service;
                public final class ServiceCompat {
                  public static void startForeground(@NonNull Service service, int id,
                          @NonNull Notification notification, int foregroundServiceType) {}
                }
                """
          )
          .indented()
      )
  }
}
