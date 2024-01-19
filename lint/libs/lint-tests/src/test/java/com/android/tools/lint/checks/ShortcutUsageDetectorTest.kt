/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.detector.api.Detector

class ShortcutUsageDetectorTest : AbstractCheckTest() {

  override fun getDetector(): Detector {
    return ShortcutUsageDetector()
  }

  fun testDocumentationExample() {
    val expected =
      """
            src/test/pkg/TestDocumentationExample.java:16: Information: Calling this method indicates use of dynamic shortcuts, but there are no calls to methods that track shortcut usage, such as pushDynamicShortcut or reportShortcutUsed. Calling these methods is recommended, as they track shortcut usage and allow launchers to adjust which shortcuts appear based on activation history. Please see https://developer.android.com/develop/ui/views/launch/shortcuts/managing-shortcuts#track-usage [ReportShortcutUsage]
                    ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 0 warnings
            """
    lint()
      .files(
        java(
            "src/test/pkg/TestDocumentationExample.java",
            """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import androidx.core.content.pm.ShortcutManagerCompat;
                import androidx.core.content.pm.ShortcutInfoCompat;
                import java.util.ArrayList;

                @SuppressWarnings("unused")
                public class TestDocumentationExample extends Activity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        ShortcutInfoCompat shortuctInfoCompat = new ShortcutInfoCompat.Builder(context, "id").build();
                        ArrayList<ShortcutInfoCompat> shortcuts = new ArrayList<ShortcutInfoCompat>();
                        shortcuts.add(shortcutInfoCompat);
                        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts);
                    }
                }
                """,
          )
          .indented(),
        *stubs,
      )
      .run()
      .expect(expected)
      .expectFixDiffs(
        """
            Show URL for src/test/pkg/TestDocumentationExample.java line 16: https://developer.android.com/develop/ui/views/launch/shortcuts/managing-shortcuts#track-usage
            """
      )
  }

  fun testAddDynamicShortcutsExample() {
    val expected =
      """
            src/test/pkg/TestAddDynamicShortcutsExample.java:16: Information: Calling this method indicates use of dynamic shortcuts, but there are no calls to methods that track shortcut usage, such as pushDynamicShortcut or reportShortcutUsed. Calling these methods is recommended, as they track shortcut usage and allow launchers to adjust which shortcuts appear based on activation history. Please see https://developer.android.com/develop/ui/views/launch/shortcuts/managing-shortcuts#track-usage [ReportShortcutUsage]
                    ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 0 warnings
            """
    lint()
      .files(
        java(
            "src/test/pkg/TestAddDynamicShortcutsExample.java",
            """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import androidx.core.content.pm.ShortcutManagerCompat;
                import androidx.core.content.pm.ShortcutInfoCompat;
                import java.util.ArrayList;

                @SuppressWarnings("unused")
                public class TestAddDynamicShortcutsExample extends Activity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        ShortcutInfoCompat shortuctInfoCompat = new ShortcutInfoCompat.Builder(context, "id").build();
                        ArrayList<ShortcutInfoCompat> shortcuts = new ArrayList<ShortcutInfoCompat>();
                        shortcuts.add(shortcutInfoCompat);
                        ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts);
                    }
                }
                """,
          )
          .indented(),
        *stubs,
      )
      .run()
      .expect(expected)
      .expectFixDiffs(
        """
            Show URL for src/test/pkg/TestAddDynamicShortcutsExample.java line 16: https://developer.android.com/develop/ui/views/launch/shortcuts/managing-shortcuts#track-usage
            """
      )
  }

  fun testReportUsageViaPushDynamicShorcut() {
    lint()
      .files(
        java(
            "src/test/pkg/testReportUsageViaPushDynamicShorcut.java",
            """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import androidx.core.content.pm.ShortcutManagerCompat;
                import androidx.core.content.pm.ShortcutInfoCompat;

                @SuppressWarnings("unused")
                public class ShortcutUsageReportedTest extends Activity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        ShortuctInfoCompat shortuctInfoCompat = new ShortcutInfoCompat.Builder(context, "id").build();
                        ShortcutManagerCompat.pushDynamicShortcut(shortuctInfoCompat);
                    }
                }
                """,
          )
          .indented(),
        *stubs,
      )
      .run()
      .expectClean()
  }

  fun testReportShortcutUsed() {
    lint()
      .files(
        java(
            "src/test/pkg/testReportShortcutUsed.java",
            """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import android.view.KeyEvent;
                import androidx.core.content.pm.ShortcutManagerCompat;
                import androidx.core.content.pm.ShortcutInfoCompat;
                import java.util.ArrayList;

                @SuppressWarnings("unused")
                public class ShortcutUsageReportedTest extends Activity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        ShortuctInfoCompat shortuctInfoCompat = new ShortcutInfoCompat.Builder(context, "id").build();
                        ArrayList<ShortcutInfoCompat> shortcuts = new ArrayList<ShortcutInfoCompat>();
                        shortcuts.add(shortcutInfoCompat);
                        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts);
                        ShortcutManagerCompat.reportShortcutUsed(context, shortuctInfoCompat.getId());
                    }
                }
                """,
          )
          .indented(),
        *stubs,
      )
      .run()
      .expectClean()
  }

  // Stubs
  private val ShortcutCompatManager: TestFile =
    java(
        """
            package androidx.core.content.pm;

            import android.content.Context;

            @SuppressWarnings("all") // stubs
            public class ShortcutManagerCompat {
                public static boolean addDynamicShortcuts(Context ctx, List<ShortcutInfoCompat> shortcuts) {
                    return false;
                }
                public static boolean setDynamicShortcuts(Context ctx, List<ShortcutInfoCompat> shortcuts) {
                    return false;
                }
                public static void pushDynamicShortcut(ShortcutInfoCompat shortcutInfoCompat){
                }
                public static void reportShortcutUsed(Context context, String shortcutId){
                }
            }

        """
      )
      .indented()

  private val ShortcutInfoCompat: TestFile =
    java(
        """
            package androidx.core.content.pm;

            @SuppressWarnings("all") // stubs
            public class ShortcutInfoCompat {
                private final ShortcutInfoCompat mInfo;
                Context mContext;
                String mId;
                public String getId() {
                    return mId;
                }
                public static class Builder {
                    public Builder(Context context,  String id) {
                        mInfo = new ShortcutInfoCompat();
                        mInfo.mContext = context;
                        mInfo.mId = id;
                    }
                    public ShortcutInfoCompat build() {
                        return mInfo;
                    }
                }
            }
        """
      )
      .indented()

  private val stubs = arrayOf(ShortcutCompatManager, ShortcutInfoCompat)
}
