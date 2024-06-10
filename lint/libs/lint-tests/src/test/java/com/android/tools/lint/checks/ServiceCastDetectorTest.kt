/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.lint.checks.ServiceCastDetector.Companion.getExpectedType
import com.android.tools.lint.detector.api.Detector

class ServiceCastDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return ServiceCastDetector()
  }

  fun testServiceCast() {
    val expected =
      """
      src/test/pkg/SystemServiceTest.java:13: Error: Suspicious cast to DisplayManager for a DEVICE_POLICY_SERVICE: expected DevicePolicyManager [ServiceCast]
              DisplayManager displayServiceWrong = (DisplayManager) getSystemService(
                                                   ^
      src/test/pkg/SystemServiceTest.java:16: Error: Suspicious cast to WallpaperService for a WALLPAPER_SERVICE: expected WallpaperManager [ServiceCast]
              WallpaperService wallPaperWrong = (WallpaperService) getSystemService(WALLPAPER_SERVICE);
                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      src/test/pkg/SystemServiceTest.java:22: Error: Suspicious cast to DisplayManager for a DEVICE_POLICY_SERVICE: expected DevicePolicyManager [ServiceCast]
              DisplayManager displayServiceWrong = (DisplayManager) context
                                                   ^
      3 errors, 0 warnings
      """
    lint()
      .files(
        java(
            """
            package test.pkg;
            import android.content.ClipboardManager;
            import android.app.Activity;
            import android.app.WallpaperManager;
            import android.content.Context;
            import android.hardware.display.DisplayManager;
            import android.service.wallpaper.WallpaperService;

            public class SystemServiceTest extends Activity {

                public void test1() {
                    DisplayManager displayServiceOk = (DisplayManager) getSystemService(DISPLAY_SERVICE);
                    DisplayManager displayServiceWrong = (DisplayManager) getSystemService(
                            DEVICE_POLICY_SERVICE);
                    WallpaperManager wallPaperOk = (WallpaperManager) getSystemService(WALLPAPER_SERVICE);
                    WallpaperService wallPaperWrong = (WallpaperService) getSystemService(WALLPAPER_SERVICE);
                }

                public void test2(Context context) {
                    DisplayManager displayServiceOk = (DisplayManager) context
                            .getSystemService(DISPLAY_SERVICE);
                    DisplayManager displayServiceWrong = (DisplayManager) context
                            .getSystemService(DEVICE_POLICY_SERVICE);
                }

                public void clipboard(Context context) {
                  ClipboardManager clipboard = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
                  android.content.ClipboardManager clipboard1 =  (android.content.ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
                  android.text.ClipboardManager clipboard2 =  (android.text.ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
                }
            }
            """
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  // sample code with warnings
  fun testWifiManagerLookup() {
    val expected =
      """
      src/test/pkg/WifiManagerTest.java:14: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing someActivity to someActivity.getApplicationContext() [WifiManagerLeak]
              someActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      src/test/pkg/WifiManagerTest.java:15: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing someService to someService.getApplicationContext() [WifiManagerLeak]
              someService.getSystemService(Context.WIFI_SERVICE);  // ERROR: Service context
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      src/test/pkg/WifiManagerTest.java:16: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing fragment.getActivity() to fragment.getActivity().getApplicationContext() [WifiManagerLeak]
              fragment.getActivity().getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      src/test/pkg/WifiManagerTest.java:17: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing fragment.getContext() to fragment.getContext().getApplicationContext() [WifiManagerLeak]
              fragment.getContext().getSystemService(Context.WIFI_SERVICE); // ERROR: FragmentHost context
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      src/test/pkg/WifiManagerTest.java:29: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing context to context.getApplicationContext() [WifiManagerLeak]
              context.getSystemService(Context.WIFI_SERVICE); // ERROR
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      src/test/pkg/WifiManagerTest.java:34: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing mActivity to mActivity.getApplicationContext() [WifiManagerLeak]
              mActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: activity service
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      src/test/pkg/WifiManagerTest.java:53: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing getSystemService to getApplicationContext().getSystemService [WifiManagerLeak]
                  getSystemService(WIFI_SERVICE); // ERROR: Activity context
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      src/test/pkg/WifiManagerTest.java:54: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing this to this.getApplicationContext() [WifiManagerLeak]
                  this.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      src/test/pkg/WifiManagerTest.java:66: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing getSystemService to getApplicationContext().getSystemService [WifiManagerLeak]
                  getSystemService(WIFI_SERVICE); // ERROR: Service context
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      src/test/pkg/WifiManagerTest.java:76: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing getContext() to getContext().getApplicationContext() [WifiManagerLeak]
                  getContext().getSystemService(Context.WIFI_SERVICE); // ERROR: View context
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      src/test/pkg/WifiManagerTest.java:32: Warning: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing foreignContext to foreignContext.getApplicationContext() [WifiManagerPotentialLeak]
              foreignContext.getSystemService(Context.WIFI_SERVICE); // UNKNOWN
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      src/test/pkg/WifiManagerTest.java:33: Warning: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing mContext to mContext.getApplicationContext() [WifiManagerPotentialLeak]
              mContext.getSystemService(Context.WIFI_SERVICE); // UNKNOWN
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      src/test/pkg/WifiManagerTest.java:41: Warning: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing ctx to ctx.getApplicationContext() [WifiManagerPotentialLeak]
              ctx.getSystemService(Context.WIFI_SERVICE); // UNKNOWN (though likely)
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      10 errors, 3 warnings
      """

    lint()
      .files(
        java(
            """
            package test.pkg;

            import android.app.Activity;
            import android.app.Application;
            import android.app.Fragment;
            import android.app.Service;
            import android.content.Context;
            import android.preference.PreferenceActivity;
            import android.widget.Button;

            @SuppressWarnings("unused")
            public class WifiManagerTest {
                public void testErrors(PreferenceActivity someActivity, Service someService, Fragment fragment) {
                    someActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context
                    someService.getSystemService(Context.WIFI_SERVICE);  // ERROR: Service context
                    fragment.getActivity().getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context
                    fragment.getContext().getSystemService(Context.WIFI_SERVICE); // ERROR: FragmentHost context
                }

                private Context mContext;
                private Application mApplication;
                private Activity mActivity;

                public void testFlow(Activity activity, Context foreignContext) {
                    @SuppressWarnings("UnnecessaryLocalVariable")
                    Context c2;
                    c2 = activity;
                    Context context = c2;
                    context.getSystemService(Context.WIFI_SERVICE); // ERROR

                    // Consider calling foreignContext.getApplicationContext() here
                    foreignContext.getSystemService(Context.WIFI_SERVICE); // UNKNOWN
                    mContext.getSystemService(Context.WIFI_SERVICE); // UNKNOWN
                    mActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: activity service
                    mApplication.getSystemService(Context.WIFI_SERVICE); // OK
                    activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // OK
                }

                public void test(Context ctx) {
                    mContext = ctx.getApplicationContext();
                    ctx.getSystemService(Context.WIFI_SERVICE); // UNKNOWN (though likely)
                }

                public void testOk(Application application) {
                    application.getSystemService(Context.WIFI_SERVICE); // OK

                    Context applicationContext = application.getApplicationContext();
                    applicationContext.getSystemService(Context.WIFI_SERVICE); // OK
                }

                public static class MyActivity extends Activity {
                    public void test() {
                        getSystemService(WIFI_SERVICE); // ERROR: Activity context
                        this.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context
                    }
                }

                public abstract static class MyApplication extends Application {
                    public void test() {
                        getSystemService(WIFI_SERVICE); // OK: Application context
                    }
                }

                public abstract static class MyService extends Service {
                    public void test() {
                        getSystemService(WIFI_SERVICE); // ERROR: Service context
                    }
                }

                public abstract class MyCustomView extends Button {
                    public MyCustomView(Context context) {
                        super(context);
                    }

                    public void test() {
                        getContext().getSystemService(Context.WIFI_SERVICE); // ERROR: View context
                    }
                }
            }
            """
          )
          .indented()
      )
      .run()
      .expect(expected)
      .expectFixDiffs(
        """
        Fix for src/test/pkg/WifiManagerTest.java line 13: Add getApplicationContext():
        @@ -14 +14
        -         someActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context
        +         someActivity.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context
        Fix for src/test/pkg/WifiManagerTest.java line 14: Add getApplicationContext():
        @@ -15 +15
        -         someService.getSystemService(Context.WIFI_SERVICE);  // ERROR: Service context
        +         someService.getApplicationContext().getSystemService(Context.WIFI_SERVICE);  // ERROR: Service context
        Fix for src/test/pkg/WifiManagerTest.java line 15: Add getApplicationContext():
        @@ -16 +16
        -         fragment.getActivity().getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context
        +         fragment.getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context
        Fix for src/test/pkg/WifiManagerTest.java line 16: Add getApplicationContext():
        @@ -17 +17
        -         fragment.getContext().getSystemService(Context.WIFI_SERVICE); // ERROR: FragmentHost context
        +         fragment.getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR: FragmentHost context
        Fix for src/test/pkg/WifiManagerTest.java line 28: Add getApplicationContext():
        @@ -29 +29
        -         context.getSystemService(Context.WIFI_SERVICE); // ERROR
        +         context.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR
        Fix for src/test/pkg/WifiManagerTest.java line 33: Add getApplicationContext():
        @@ -34 +34
        -         mActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: activity service
        +         mActivity.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR: activity service
        Fix for src/test/pkg/WifiManagerTest.java line 52: Add getApplicationContext():
        @@ -53 +53
        -             getSystemService(WIFI_SERVICE); // ERROR: Activity context
        +             getApplicationContext().getSystemService(WIFI_SERVICE); // ERROR: Activity context
        Fix for src/test/pkg/WifiManagerTest.java line 53: Add getApplicationContext():
        @@ -54 +54
        -             this.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context
        +             this.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context
        Fix for src/test/pkg/WifiManagerTest.java line 65: Add getApplicationContext():
        @@ -66 +66
        -             getSystemService(WIFI_SERVICE); // ERROR: Service context
        +             getApplicationContext().getSystemService(WIFI_SERVICE); // ERROR: Service context
        Fix for src/test/pkg/WifiManagerTest.java line 75: Add getApplicationContext():
        @@ -76 +76
        -             getContext().getSystemService(Context.WIFI_SERVICE); // ERROR: View context
        +             getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR: View context
        Fix for src/test/pkg/WifiManagerTest.java line 31: Add getApplicationContext():
        @@ -32 +32
        -         foreignContext.getSystemService(Context.WIFI_SERVICE); // UNKNOWN
        +         foreignContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // UNKNOWN
        Fix for src/test/pkg/WifiManagerTest.java line 32: Add getApplicationContext():
        @@ -33 +33
        -         mContext.getSystemService(Context.WIFI_SERVICE); // UNKNOWN
        +         mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // UNKNOWN
        Fix for src/test/pkg/WifiManagerTest.java line 40: Add getApplicationContext():
        @@ -41 +41
        -         ctx.getSystemService(Context.WIFI_SERVICE); // UNKNOWN (though likely)
        +         ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // UNKNOWN (though likely)

        """
      )
  }

  fun testWifiManagerLookupOnNougat() {
    lint()
      .files(
        java(
            """
            package test.pkg;

            import android.app.Activity;
            import android.content.Context;
            import android.preference.PreferenceActivity;

            public class WifiManagerTest {
                public void testErrors(PreferenceActivity someActivity) {
                    someActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context
                }
            }
            """
          )
          .indented(),
        // Android N:
        manifest().minSdk(24),
      )
      .run()
      .expectClean()
  }

  fun testCrossProfile() {
    // Regression test for b/245337893
    lint()
      .files(
        java(
            """
            package test.pkg;

            import android.app.Activity;
            import android.content.Context;
            import android.preference.PreferenceActivity;

            public class Test {
                public void testErrors(PreferenceActivity someActivity) {
                    (android.content.pm.CrossProfileApps)someActivity.getSystemService(Context.CROSS_PROFILE_APPS_SERVICE); // OK
                    (test.pkg.CrossProfileApps)someActivity.getSystemService(Context.CROSS_PROFILE_APPS_SERVICE); // ERROR
                }
            }
            """
          )
          .indented(),
        java(
            """
            package test.pkg;
            public class CrossProfileApps { }
            """
          )
          .indented(),
        // Android N:
        manifest().minSdk(24),
      )
      .run()
      .expect(
        """
        src/test/pkg/Test.java:10: Error: Suspicious cast to test.pkg.CrossProfileApps for a CROSS_PROFILE_APPS_SERVICE: expected android.content.pm.CrossProfileApps [ServiceCast]
                (test.pkg.CrossProfileApps)someActivity.getSystemService(Context.CROSS_PROFILE_APPS_SERVICE); // ERROR
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
  }

  fun testAndroid14() {
    lint()
      .files(
        kotlin(
            """
            package test.pkg

            import android.content.Context
            import android.health.connect.HealthConnectManager
            import android.os.health.SystemHealthManager

            fun test(context: Context) {
                context.getSystemService(Context.SYSTEM_HEALTH_SERVICE) as HealthConnectManager // ERROR
                context.getSystemService(Context.SYSTEM_HEALTH_SERVICE) as SystemHealthManager // OK
            }
            """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:8: Error: Suspicious cast to HealthConnectManager for a SYSTEM_HEALTH_SERVICE: expected SystemHealthManager [ServiceCast]
            context.getSystemService(Context.SYSTEM_HEALTH_SERVICE) as HealthConnectManager // ERROR
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
  }

  fun testLookup() {
    assertEquals(
      "android.view.accessibility.AccessibilityManager",
      getExpectedType("ACCESSIBILITY_SERVICE"),
    )
    assertEquals("android.accounts.AccountManager", getExpectedType("ACCOUNT_SERVICE"))
    assertEquals("android.app.ActivityManager", getExpectedType("ACTIVITY_SERVICE"))
    assertEquals("android.app.AlarmManager", getExpectedType("ALARM_SERVICE"))
    assertEquals("android.appwidget.AppWidgetManager", getExpectedType("APPWIDGET_SERVICE"))
    assertEquals("android.app.AppOpsManager", getExpectedType("APP_OPS_SERVICE"))
    assertEquals("android.media.AudioManager", getExpectedType("AUDIO_SERVICE"))
    assertEquals("android.os.BatteryManager", getExpectedType("BATTERY_SERVICE"))
    assertEquals("android.bluetooth.BluetoothManager", getExpectedType("BLUETOOTH_SERVICE"))
    assertEquals("android.hardware.camera2.CameraManager", getExpectedType("CAMERA_SERVICE"))
    assertEquals(
      "android.view.accessibility.CaptioningManager",
      getExpectedType("CAPTIONING_SERVICE"),
    )
    assertEquals(
      "android.telephony.CarrierConfigManager",
      getExpectedType("CARRIER_CONFIG_SERVICE"),
    )
    assertEquals("android.text.ClipboardManager", getExpectedType("CLIPBOARD_SERVICE"))
    assertEquals(
      "android.companion.CompanionDeviceManager",
      getExpectedType("COMPANION_DEVICE_SERVICE"),
    )
    assertEquals("android.net.ConnectivityManager", getExpectedType("CONNECTIVITY_SERVICE"))
    assertEquals("android.hardware.ConsumerIrManager", getExpectedType("CONSUMER_IR_SERVICE"))
    assertEquals("android.telephony.euicc.EuiccManager", getExpectedType("EUICC_SERVICE"))
    assertEquals("android.app.admin.DevicePolicyManager", getExpectedType("DEVICE_POLICY_SERVICE"))
    assertEquals("android.hardware.display.DisplayManager", getExpectedType("DISPLAY_SERVICE"))
    assertEquals("android.app.DownloadManager", getExpectedType("DOWNLOAD_SERVICE"))
    assertEquals("android.os.DropBoxManager", getExpectedType("DROPBOX_SERVICE"))
    assertEquals(
      "android.hardware.fingerprint.FingerprintManager",
      getExpectedType("FINGERPRINT_SERVICE"),
    )
    assertEquals(
      "android.os.HardwarePropertiesManager",
      getExpectedType("HARDWARE_PROPERTIES_SERVICE"),
    )
    assertEquals(
      "android.view.inputmethod.InputMethodManager",
      getExpectedType("INPUT_METHOD_SERVICE"),
    )
    assertEquals("android.hardware.input.InputManager", getExpectedType("INPUT_SERVICE"))
    assertEquals("android.net.IpSecManager", getExpectedType("IPSEC_SERVICE"))
    assertEquals("android.app.job.JobScheduler", getExpectedType("JOB_SCHEDULER_SERVICE"))
    assertEquals("android.app.KeyguardManager", getExpectedType("KEYGUARD_SERVICE"))
    assertEquals("android.content.pm.LauncherApps", getExpectedType("LAUNCHER_APPS_SERVICE"))
    assertEquals("android.view.LayoutInflater", getExpectedType("LAYOUT_INFLATER_SERVICE"))
    assertEquals("android.location.LocationManager", getExpectedType("LOCATION_SERVICE"))
    assertEquals(
      "android.media.projection.MediaProjectionManager",
      getExpectedType("MEDIA_PROJECTION_SERVICE"),
    )
    assertEquals("android.media.MediaRouter", getExpectedType("MEDIA_ROUTER_SERVICE"))
    assertEquals(
      "android.media.session.MediaSessionManager",
      getExpectedType("MEDIA_SESSION_SERVICE"),
    )
    assertEquals("android.media.midi.MidiManager", getExpectedType("MIDI_SERVICE"))
    assertEquals("android.app.usage.NetworkStatsManager", getExpectedType("NETWORK_STATS_SERVICE"))
    assertEquals("android.nfc.NfcManager", getExpectedType("NFC_SERVICE"))
    assertEquals("android.app.NotificationManager", getExpectedType("NOTIFICATION_SERVICE"))
    assertEquals("android.net.nsd.NsdManager", getExpectedType("NSD_SERVICE"))
    assertEquals("android.os.PowerManager", getExpectedType("POWER_SERVICE"))
    assertEquals("android.print.PrintManager", getExpectedType("PRINT_SERVICE"))
    assertEquals("android.content.RestrictionsManager", getExpectedType("RESTRICTIONS_SERVICE"))
    assertEquals("android.app.SearchManager", getExpectedType("SEARCH_SERVICE"))
    assertEquals("android.hardware.SensorManager", getExpectedType("SENSOR_SERVICE"))
    assertEquals("android.content.pm.ShortcutManager", getExpectedType("SHORTCUT_SERVICE"))
    assertEquals("android.os.storage.StorageManager", getExpectedType("STORAGE_SERVICE"))
    assertEquals("android.app.usage.StorageStatsManager", getExpectedType("STORAGE_STATS_SERVICE"))
    assertEquals("android.os.health.SystemHealthManager", getExpectedType("SYSTEM_HEALTH_SERVICE"))
    assertEquals("android.telecom.TelecomManager", getExpectedType("TELECOM_SERVICE"))
    assertEquals("android.telephony.TelephonyManager", getExpectedType("TELEPHONY_SERVICE"))
    assertEquals(
      "android.telephony.SubscriptionManager",
      getExpectedType("TELEPHONY_SUBSCRIPTION_SERVICE"),
    )
    assertEquals(
      "android.view.textclassifier.TextClassificationManager",
      getExpectedType("TEXT_CLASSIFICATION_SERVICE"),
    )
    assertEquals(
      "android.view.textservice.TextServicesManager",
      getExpectedType("TEXT_SERVICES_MANAGER_SERVICE"),
    )
    assertEquals("android.media.tv.TvInputManager", getExpectedType("TV_INPUT_SERVICE"))
    assertEquals("android.app.UiModeManager", getExpectedType("UI_MODE_SERVICE"))
    assertEquals("android.app.usage.UsageStatsManager", getExpectedType("USAGE_STATS_SERVICE"))
    assertEquals("android.hardware.usb.UsbManager", getExpectedType("USB_SERVICE"))
    assertEquals("android.os.UserManager", getExpectedType("USER_SERVICE"))
    assertEquals("android.os.Vibrator", getExpectedType("VIBRATOR_SERVICE"))
    assertEquals("android.app.WallpaperManager", getExpectedType("WALLPAPER_SERVICE"))
    assertEquals("android.net.wifi.aware.WifiAwareManager", getExpectedType("WIFI_AWARE_SERVICE"))
    assertEquals("android.net.wifi.p2p.WifiP2pManager", getExpectedType("WIFI_P2P_SERVICE"))
    assertEquals("android.net.wifi.WifiManager", getExpectedType("WIFI_SERVICE"))
    assertEquals("android.view.WindowManager", getExpectedType("WINDOW_SERVICE"))
  }
}
