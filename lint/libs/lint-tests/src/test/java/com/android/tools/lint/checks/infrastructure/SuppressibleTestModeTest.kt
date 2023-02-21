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

package com.android.tools.lint.checks.infrastructure

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import java.io.File
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Suppress("LintDocExample")
class SuppressibleTestModeTest {
  @get:Rule var temporaryFolder = TemporaryFolder()

  private fun TestFile.findIn(rootFolders: List<File>): File {
    for (rootFolder in rootFolders) {
      val target = File(rootFolder, targetPath)
      if (target.exists()) {
        return target
      }
    }
    error("Couldn't find $targetRelativePath in $rootFolders")
  }

  private fun check(output: String, files: List<TestFile>, diffs: String) {
    val root = temporaryFolder.root
    val rootFolders = TestLintTask.lint().files(*files.toTypedArray()).createProjects(root)
    SuppressibleTestMode().rewrite(output.trimIndent(), rootFolders, TestUtils.getSdk().toFile())
    val sb = StringBuilder()
    for (file in files.sortedBy { it.targetRelativePath }) {
      val target = file.findIn(rootFolders)
      val expected = file.contents
      val actual = target.readText()
      sb.append(file.targetRelativePath).append(":\n")
      val diff = TestLintResult.getDiff(expected, actual, windowSize = 1)
      sb.append(diff).append("\n")

      // To debug, uncomment the following to see before/after instead of
      // just inspecting a diff:
      // assertEquals(expected, actual)
    }
    assertEquals(diffs.trimIndent().trim(), sb.toString().trim())
  }

  private fun String.trimTrailingSpaces(): String {
    return lines().joinToString("\n") { it.trimEnd() }
  }

  @Suppress("XmlUnusedNamespaceDeclaration")
  @Test
  fun testXml1() {
    val files =
      listOf(
        xml(
            "res/menu/menu.xml",
            """
                    <menu xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
                        <item
                            android:id="@+id/item1"
                            android:icon="@drawable/icon1"
                            android:title="My title 1">
                        </item>
                        <item
                            android:id="@+id/item2"
                            android:icon="@drawable/icon2"
                            android:showAsAction="ifRoom"
                            android:title="My title 2">
                        </item>
                    </menu>
                    """
          )
          .indented(),
        xml(
            "res/values/duplicate-strings.xml",
            """
                    <resources>
                        <string name="app_name">App Name</string>
                        <string name="hello_world">Hello world!</string>
                        <string name="app_name">App Name 1</string>
                        <string name="app_name2">App Name 2</string>

                    </resources>
                    """
          )
          .indented()
      )
    val output =
      """
            res/values/duplicate-strings.xml:4: Error: app_name has already been defined in this folder [DuplicateDefinition]
                <string name="app_name">App Name 1</string>
                        ~~~~~~~~~~~~~~~
                res/values/duplicate-strings.xml:2: Previously defined here

               Explanation for issues of type "DuplicateDefinition":
               You can define a resource multiple times in different resource folders;
               that's how string translations are done, for example. However, defining the
               same resource more than once in the same resource folder is likely an
               error, for example attempting to add a new resource without realizing that
               the name is already used, and so on.

            res/menu/menu.xml:5: Warning: Hardcoded string "My title 1", should use @string resource [HardcodedText from mylibrary-1.0]
                    android:title="My title 1">
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/menu/menu.xml:11: Warning: Hardcoded string "My title 2", should use @string resource [HardcodedText from mylibrary-1.0]
                    android:title="My title 2">
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~

               Explanation for issues of type "HardcodedText":
               Hardcoding text attributes directly in layout files is bad for several
               reasons:

               * When creating configuration variations (for example for landscape or
               portrait) you have to repeat the actual text (and keep it up to date when
               making changes)

               * The application cannot be translated to other languages by just adding
               new translations for existing string resources.

               There are quickfixes to automatically extract this hardcoded string into a
               resource lookup.

               Vendor: AOSP Unit Tests
               Identifier: mylibrary-1.0
               Contact: lint@example.com
               Feedback: https://example.com/lint/file-new-bug.html

            1 errors, 2 warnings
            """
    check(
      output,
      files,
      """
            res/menu/menu.xml:
            @@ -3 +3
                  <item
            -         android:id="@+id/item1"
            +         tools:ignore="HardcodedText" android:id="@+id/item1"
                      android:icon="@drawable/icon1"
            @@ -8 +8
                  <item
            -         android:id="@+id/item2"
            +         tools:ignore="HardcodedText" android:id="@+id/item2"
                      android:icon="@drawable/icon2"
            res/values/duplicate-strings.xml:
            @@ -1 +1
            - <resources>
            + <resources xmlns:tools="http://schemas.android.com/tools">
                  <string name="app_name">App Name</string>
                  <string name="hello_world">Hello world!</string>
            -     <string name="app_name">App Name 1</string>
            +     <string tools:ignore="DuplicateDefinition" name="app_name">App Name 1</string>
                  <string name="app_name2">App Name 2</string>
            """
        .trimIndent()
    )
  }

  @Test
  fun testXml2() {
    val testFiles =
      listOf(
        xml(
            "res/xml/nfc_tech_list_formatted.xml",
            """
                <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2" >

                    <!-- capture anything using NfcF -->
                    <tech-list>
                        <tech>
                        android.nfc.tech.NfcA
                        </tech>
                    </tech-list>
                </resources>
                """
          )
          .indented()
      )
    val output =
      """
            res/xml/nfc_tech_list_formatted.xml:6: Error: There should not be any whitespace inside <tech> elements [NfcTechWhitespace]
            android.nfc.tech.NfcA
            ~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
        """
        .trimIndent()
    check(
      output,
      testFiles,
      """
            res/xml/nfc_tech_list_formatted.xml:
            @@ -1 +1
            - <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2" >
            + <resources xmlns:tools="http://schemas.android.com/tools" xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2" >

            @@ -5 +5
                  <tech-list>
            -         <tech>
            +         <tech tools:ignore="NfcTechWhitespace">
                      android.nfc.tech.NfcA
            """
    )
  }

  @Suppress("XmlUnusedNamespaceDeclaration")
  @Test
  fun testXmlExistingIgnore() {
    val testFiles =
      listOf(
        xml(
            "res/values-nb/strings.xml",
            """
                <resources
                    xmlns:android="https://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:ignore="ExtraTranslation">
                    <string name="bar">Bar</string>
                </resources>
                """
          )
          .indented()
      )
    val output =
      """
            res/values-nb/strings.xml:1: Error: Suspicious namespace: should start with http:// [NamespaceTypo]
            <resources     xmlns:android="https://schemas.android.com/apk/res/android"
                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/values-nb/strings.xml:2: Error: Suspicious namespace: should start with http:// [NamespaceTypo]
                xmlns:tools="https://schemas.android.com/tools"
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
        """
        .trimIndent()
    check(
      output,
      testFiles,
      """
            res/values-nb/strings.xml:
            @@ -4 +4
                  xmlns:tools="http://schemas.android.com/tools"
            -     tools:ignore="ExtraTranslation">
            +     tools:ignore="NamespaceTypo,ExtraTranslation">
                  <string name="bar">Bar</string>
            """
    )
  }

  @Test
  fun testJava1() {
    val testFiles =
      listOf(
        java(
            "src/test/pkg/AlarmTest.java",
            """
                    package test.pkg;

                    import android.app.AlarmManager;
                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public class AlarmTest {
                        public void test(AlarmManager alarmManager) {
                            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 5000, 60000, null); // OK
                            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 6000, 70000, null); // OK
                            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 50, 10, null); // ERROR
                            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 5000,  // ERROR
                                    OtherClass.MY_INTERVAL, null);                          // ERROR

                            // Check value flow analysis
                            int interval = 10;
                            long interval2 = 2 * interval;
                            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 5000, interval2, null); // ERROR
                        }

                        private static class OtherClass {
                            public static final long MY_INTERVAL = 1000L;
                        }
                    }
                    """
          )
          .indented()
      )
    val output =
      """
            src/test/pkg/AlarmTest.java:9: Warning: Value will be forced up to 5000 as of Android 5.1; don't rely on this to be exact [ShortAlarm]
                    alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 50, 10, null); // ERROR
                                                                             ~~
            src/test/pkg/AlarmTest.java:9: Warning: Value will be forced up to 60000 as of Android 5.1; don't rely on this to be exact [ShortAlarm]
                    alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 50, 10, null); // ERROR
                                                                                 ~~
            src/test/pkg/AlarmTest.java:11: Warning: Value will be forced up to 60000 as of Android 5.1; don't rely on this to be exact [ShortAlarm]
                            OtherClass.MY_INTERVAL, null);                          // ERROR
                            ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AlarmTest.java:16: Warning: Value will be forced up to 60000 as of Android 5.1; don't rely on this to be exact [ShortAlarm]
                    alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 5000, interval2, null); // ERROR
                                                                                   ~~~~~~~~~
            0 errors, 4 warnings
        """
        .trimIndent()
    check(
      output,
      testFiles,
      """
            src/test/pkg/AlarmTest.java:
            @@ -6 +6
              public class AlarmTest {
            -     public void test(AlarmManager alarmManager) {
            +     @SuppressWarnings("ShortAlarm") public void test(AlarmManager alarmManager) {
                      alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 5000, 60000, null); // OK
            """
    )
  }

  @Test
  fun testJavaMultipleIds() {
    val testFiles =
      listOf(
        java(
            """
                package test.pkg;
                import android.content.Context;
                import android.telephony.TelephonyManager;
                import java.lang.reflect.Field;
                public class TestReflection {
                    public void test(Context context, int subId) throws Exception {
                        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                        Field deniedField = TelephonyManager.class.getDeclaredField("NETWORK_TYPES"); // ERROR 1
                        Field maybeField = TelephonyManager.class.getDeclaredField("OTASP_NEEDED"); // ERROR 2
                    }
                }
               """
          )
          .indented()
      )
    val output =
      """
            src/test/pkg/TestReflection.java:8: Error: Reflective access to NETWORK_TYPES is forbidden when targeting API 28 and above [BlockedPrivateApi]
                        Field deniedField = TelephonyManager.class.getDeclaredField("NETWORK_TYPES"); // ERROR 1
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestReflection.java:9: Error: Reflective access to OTASP_NEEDED will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                        Field maybeField = TelephonyManager.class.getDeclaredField("OTASP_NEEDED"); // ERROR 2
                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestReflection.java:9: Error: Reflective access to OTASP_NEEDED will throw an exception when targeting API 28 and above [BlockedPrivateApi]
                        Field maybeField = TelephonyManager.class.getDeclaredField("OTASP_NEEDED"); // ERROR 2
                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
        """
        .trimIndent()
    check(
      output,
      testFiles,
      """
            test/pkg/TestReflection.java:
            @@ -8 +8
                      TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            -         Field deniedField = TelephonyManager.class.getDeclaredField("NETWORK_TYPES"); // ERROR 1
            -         Field maybeField = TelephonyManager.class.getDeclaredField("OTASP_NEEDED"); // ERROR 2
            +         @SuppressWarnings("BlockedPrivateApi") Field deniedField = TelephonyManager.class.getDeclaredField("NETWORK_TYPES"); // ERROR 1
            +         @SuppressWarnings({"BlockedPrivateApi", "SoonBlockedPrivateApi"}) Field maybeField = TelephonyManager.class.getDeclaredField("OTASP_NEEDED"); // ERROR 2
                  }
            """
    )
  }

  @Suppress("RedundantSuppression")
  @Test
  fun testJavaExistingSuppress() {
    val testFiles =
      listOf(
        java(
            """
                package test.pkg;

                import static android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;
                import static android.os.PowerManager.FULL_WAKE_LOCK;
                import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
                import android.content.Context;
                import android.os.PowerManager;

                public class PowerManagerFlagTest {
                    @SuppressWarnings("deprecation")
                    public void test(Context context) {
                        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

                        pm.newWakeLock(PARTIAL_WAKE_LOCK, "Test"); // OK
                        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK|ACQUIRE_CAUSES_WAKEUP, "Test"); // Bad
                        pm.newWakeLock(FULL_WAKE_LOCK|ACQUIRE_CAUSES_WAKEUP, "Test"); // OK
                    }
                }
               """
          )
          .indented()
      )
    val output =
      """
            src/test/pkg/PowerManagerFlagTest.java:15: Warning: Should not set both PARTIAL_WAKE_LOCK and ACQUIRE_CAUSES_WAKEUP. If you do not want the screen to turn on, get rid of ACQUIRE_CAUSES_WAKEUP [Wakelock]
                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK|ACQUIRE_CAUSES_WAKEUP, "Test"); // Bad
                       ~~~~~~~~~~~
            0 errors, 1 warnings
        """
        .trimIndent()
    check(
      output,
      testFiles,
      """
            test/pkg/PowerManagerFlagTest.java:
            @@ -10 +10
              public class PowerManagerFlagTest {
            -     @SuppressWarnings("deprecation")
            +     @SuppressWarnings({"Wakelock", "deprecation"})
                  public void test(Context context) {
            """
    )
  }

  @Test
  fun testKotlin() {
    val testFiles =
      listOf(
        kotlin(
            """
                package test.pkg
                import java.io.File
                class KotlinSuppressTest(val var1: String, file: File) {
                    var property1 = 5
                    var property2: String get() = ""
                        set(value) {}
                }
                fun methodTest(pair: Pair<String,String>) {
                    var test = 5
                    methodTest(pair)
                    val (x,y) = pair
                    val c = object : Runnable {
                        override fun run() {
                            methodTest(pair)
                        }
                    }
                    val a = { i: Int -> i + 1 }
                    "foo".myMethod().myMethod()
                        .myMethod()
                        .myMethod().lowercase()
                }
                fun String.myMethod(): String = this
                """
          )
          .indented(),
      )
    val output =
      """
            src/test/pkg/KotlinSuppressTest.kt:1: Warning: Warning message here [TestId1]
            package test.pkg
                    ~~~~~~~~
            src/test/pkg/KotlinSuppressTest.kt:2: Warning: Warning message here [TestId2]
            import java.io.File
                   ~~~~~~~~~~~~
            src/test/pkg/KotlinSuppressTest.kt:3: Warning: Warning message here [TestId3]
            class KotlinSuppressTest(val var1: String, file: File) {
                  ~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinSuppressTest.kt:3: Warning: Warning message here [TestId4]
            class KotlinSuppressTest(val var1: String, file: File) {
                                         ~~~~
            src/test/pkg/KotlinSuppressTest.kt:3: Warning: Warning message here [TestId5]
            class KotlinSuppressTest(val var1: String, file: File) {
                                                       ~~~~
            src/test/pkg/KotlinSuppressTest.kt:4: Warning: Warning message here [TestId6]
                var property1 = 5
                    ~~~~~~~~~
            src/test/pkg/KotlinSuppressTest.kt:5: Warning: Warning message here [TestId7]
                var property2: String get() = ""
                    ~~~~~~~~~
            src/test/pkg/KotlinSuppressTest.kt:5: Warning: Warning message here [TestId8]
                var property2: String get() = ""
                                      ~~~
            src/test/pkg/KotlinSuppressTest.kt:6: Warning: Warning message here [TestId9]
                    set(value) {}
                    ~~~
            src/test/pkg/KotlinSuppressTest.kt:8: Warning: Warning message here [TestId10]
            fun methodTest(pair: Pair<String,String>) {
                ~~~~~~~~~~
            src/test/pkg/KotlinSuppressTest.kt:8: Warning: Warning message here [TestId11]
            fun methodTest(pair: Pair<String,String>) {
                           ~~~~
            src/test/pkg/KotlinSuppressTest.kt:8: Warning: Warning message here [TestId12]
            fun methodTest(pair: Pair<String,String>) {
                                 ~~~~
            src/test/pkg/KotlinSuppressTest.kt:9: Warning: Warning message here [TestId13]
                var test = 5
                    ~~~~
            src/test/pkg/KotlinSuppressTest.kt:10: Warning: Warning message here [TestId14]
                methodTest(pair)
                ~~~~~~~~~~
            src/test/pkg/KotlinSuppressTest.kt:10: Warning: Warning message here [TestId15]
                methodTest(pair)
                           ~~~~
            src/test/pkg/KotlinSuppressTest.kt:11: Warning: Warning message here [TestId16]
                val (x,y) = pair
                     ~
            src/test/pkg/KotlinSuppressTest.kt:12: Warning: Warning message here [TestId17]
                val c = object : Runnable {
                        ~~~~~~
            src/test/pkg/KotlinSuppressTest.kt:13: Warning: Warning message here [TestId18]
                    override fun run() {
                                 ~~~
            src/test/pkg/KotlinSuppressTest.kt:14: Warning: Warning message here [TestId19]
                        methodTest(pair)
                        ~~~~~~~~~~
            src/test/pkg/KotlinSuppressTest.kt:17: Warning: Warning message here [TestId20]
                val a = { i: Int -> i + 1 }
                          ~~~~~~
            src/test/pkg/KotlinSuppressTest.kt:17: Warning: Warning message here [TestId21]
                val a = { i: Int -> i + 1 }
                                    ~~~~~
            src/test/pkg/KotlinSuppressTest.kt:18: Warning: Warning message here [TestId22]
                "foo".myMethod().myMethod()
                      ~~~~~~~~~~
            src/test/pkg/KotlinSuppressTest.kt:20: Warning: Warning message here [TestId23]
                    .myMethod().lowercase()
                                ~~~~~~~~~
            0 errors, 23 warnings
        """
        .trimIndent()
    check(
      output,
      testFiles,
      """
            test/pkg/KotlinSuppressTest.kt:
            @@ -1 +1
            - package test.pkg
            + @file:Suppress("TestId1") package test.pkg
            + //noinspection TestId2
              import java.io.File
            - class KotlinSuppressTest(val var1: String, file: File) {
            -     var property1 = 5
            -     var property2: String get() = ""
            -         set(value) {}
            + @Suppress("TestId3") class KotlinSuppressTest(@Suppress("TestId4") val var1: String, @Suppress("TestId5") file: File) {
            +     @Suppress("TestId6") var property1 = 5
            +     @Suppress("TestId7") var property2: String @Suppress("TestId8") get() = ""
            +         @Suppress("TestId9") set(value) {}
              }
            - fun methodTest(pair: Pair<String,String>) {
            -     var test = 5
            + @Suppress("TestId23", "TestId22", "TestId15", "TestId14", "TestId10") fun methodTest(@Suppress("TestId12", "TestId11") pair: Pair<String,String>) {
            +     @Suppress("TestId13") var test = 5
                  methodTest(pair)
            -     val (x,y) = pair
            -     val c = object : Runnable {
            -         override fun run() {
            +     val (@Suppress("TestId16") x,y) = pair
            +     val c = @Suppress("TestId17") object : Runnable {
            +         @Suppress("TestId19", "TestId18") override fun run() {
                          methodTest(pair)
            @@ -17 +18
                  }
            -     val a = { i: Int -> i + 1 }
            +     @Suppress("TestId21", "TestId20") val a = { i: Int -> i + 1 }
                  "foo".myMethod().myMethod()
            """
    )
  }

  @Test
  fun testKotlinSuppress() {
    val testFiles =
      listOf(
        kotlin(
            """
                package test.pkg
                //noinspection test1
                import java.io.File
                import java.util.Base64
                class KotlinSuppressTest2() {
                    fun test1() {}
                    fun test1b() {}
                    @Suppress("test1") fun test2() {}
                    @Suppress("test1", "test2") fun test3() {}
                }
                """
          )
          .indented(),
      )
    val output =
      """
            src/test/pkg/KotlinSuppressTest2.kt:3: Warning: Warning message here [TestId]
            import java.io.File
                   ~~~~~~~~~~~~
            src/test/pkg/KotlinSuppressTest2.kt:4: Warning: Warning message here [TestId]
            import java.util.Base64
                   ~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinSuppressTest2.kt:6: Warning: Warning message here [TestId]
                fun test1() {}
                    ~~~~~
            src/test/pkg/KotlinSuppressTest2.kt:7: Warning: Warning message here [TestId]
                fun test1b() {}
                    ~~~~~~
            src/test/pkg/KotlinSuppressTest2.kt:7: Warning: Warning message here [TestId2]
                fun test1b() {}
                    ~~~~~~
            src/test/pkg/KotlinSuppressTest2.kt:8: Warning: Warning message here [TestId]
                @Suppress("test1") fun test2() {}
                                       ~~~~~
            src/test/pkg/KotlinSuppressTest2.kt:9: Warning: Warning message here [TestId]
                @Suppress("test1", "test2") fun test3() {}
                                                ~~~~~
            src/test/pkg/KotlinSuppressTest2.kt:9: Warning: Warning message here [TestId2]
                @Suppress("test1", "test2") fun test3() {}
                                                ~~~~~
            0 errors, 6 warnings
        """
        .trimIndent()
    check(
      output,
      testFiles,
      """
            test/pkg/KotlinSuppressTest2.kt:
            @@ -2 +2
              package test.pkg
            - //noinspection test1
            + //noinspection TestId,test1
              import java.io.File
            + //noinspection TestId
              import java.util.Base64
              class KotlinSuppressTest2() {
            -     fun test1() {}
            -     fun test1b() {}
            -     @Suppress("test1") fun test2() {}
            -     @Suppress("test1", "test2") fun test3() {}
            +     @Suppress("TestId") fun test1() {}
            +     @Suppress("TestId2", "TestId") fun test1b() {}
            +     @Suppress("TestId", "test1") fun test2() {}
            +     @Suppress("TestId2", "TestId", "test1", "test2") fun test3() {}
              }
            """
    )
  }

  @Test
  fun testComments() {
    // Make sure that for comments, we use a //noinspection suppression on the line
    // above instead of going to an outer modifier list owner.
    val testFiles =
      listOf(
        java(
            """
                package test.pkg;
                public class Hidden1 {
                    // STOPSHIP
                    /* We must STOPSHIP! */
                    String x = "STOPSHIP"; // OK
                }
                """
          )
          .indented(),
        kotlin(
            """
                package test.pkg
                class Hidden2 {
                    // STOPSHIP
                    /* We must STOPSHIP! */
                    var x = "STOPSHIP" // OK
                }
                """
          )
          .indented()
      )
    val output =
      """
            src/test/pkg/Hidden1.java:3: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]
                // STOPSHIP
                   ~~~~~~~~
            src/test/pkg/Hidden1.java:4: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]
                /* We must STOPSHIP! */
                           ~~~~~~~~
            src/test/pkg/Hidden2.kt:3: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]
                // STOPSHIP
                   ~~~~~~~~
            src/test/pkg/Hidden2.kt:4: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]
                /* We must STOPSHIP! */
                           ~~~~~~~~
            4 errors, 0 warnings
        """
        .trimIndent()
    check(
      output,
      testFiles,
      """
            test/pkg/Hidden1.java:
            @@ -3 +3
              public class Hidden1 {
            +     //noinspection StopShip
                  // STOPSHIP
            +     //noinspection StopShip
                  /* We must STOPSHIP! */
            test/pkg/Hidden2.kt:
            @@ -3 +3
              class Hidden2 {
            +     //noinspection StopShip
                  // STOPSHIP
            +     //noinspection StopShip
                  /* We must STOPSHIP! */
            """
    )
  }

  @Test
  fun testLambda() {
    // b/258962911
    val testFiles =
      listOf(
        java(
            """
                package test.pkg;
                class Bar {
                    public void test() {
                        Bar.create(param -> null);
                        Bar.create(param -> null);
                    }

                    public static void create(Foo foo) {
                    }

                    @FunctionalInterface
                    interface Foo {
                        public Object run(Object param);
                    }
                }
                """
          )
          .indented(),
        kotlin(
            """
                package test.pkg

                class Bar2 {
                    fun test() {
                        create { param: Any? -> null }
                        create { param: Any? -> null }
                    }

                    internal fun interface Foo {
                        fun run(param: Any?): Any?
                    }

                    companion object {
                        fun create(foo: Foo?) {}
                    }
                }
                """
          )
          .indented()
      )
    val output =
      """
            src/test/pkg/Bar.java:4: Warning: Warning message here [TestId]
                    Bar.create(param -> null);
                               ~~~~~~~~~~~~~
            src/test/pkg/Bar.java:5: Warning: Warning message here [TestId]
                    Bar.create(param -> null);
                               ~~~~~
            src/test/pkg/Bar2.kt:5: Warning: Warning message here [TestId]
                    create { param: Any? -> null }
                             ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/Bar2.kt:6: Warning: Warning message here [TestId]
                    create { param: Any? -> null }
                             ~~~~~
            0 errors, 4 warnings
        """
        .trimIndent()
    check(
      output,
      testFiles,
      """
            test/pkg/Bar.java:
            @@ -3 +3
              class Bar {
            -     public void test() {
            +     @SuppressWarnings("TestId") public void test() {
                      Bar.create(param -> null);
            test/pkg/Bar2.kt:
            @@ -4 +4
              class Bar2 {
            -     fun test() {
            +     @Suppress("TestId") fun test() {
                      create { param: Any? -> null }
            """
    )
  }
}
