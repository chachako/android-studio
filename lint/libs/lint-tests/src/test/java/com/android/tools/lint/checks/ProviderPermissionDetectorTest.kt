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
import com.android.tools.lint.detector.api.Detector

class ProviderPermissionDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector = ProviderPermissionDetector()

  fun testDocumentationExample() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              package="test.pkg">
              <application>
                <provider android:name="test.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                <provider android:name="test.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
              </application>
            </manifest>
            """
          )
          .indented(),
        javaSingleApi,
        kotlinSingleApi,
      )
      .run()
      .expect(
        """
          AndroidManifest.xml:5: Warning: test.pkg.JavaTestContentProvider implements {insert} write APIs but does not protect them with a permission. Update the <provider> tag to use android:permission or android:writePermission [ProviderReadPermissionOnly]
              <provider android:name="test.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                                                                        ~~~~~~~~~~~~~~~~~~~~~~
          AndroidManifest.xml:6: Warning: test.pkg.KotlinTestContentProvider implements {insert} write APIs but does not protect them with a permission. Update the <provider> tag to use android:permission or android:writePermission [ProviderReadPermissionOnly]
              <provider android:name="test.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                                                                          ~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 2 warnings
        """
      )
      .expectFixDiffs(
        """
          Fix for AndroidManifest.xml line 5: Replace with permission:
          @@ -5 +5
          -     <provider android:name="test.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
          +     <provider android:name="test.pkg.JavaTestContentProvider" android:permission="android.permission.READ_DATA"/>
          Fix for AndroidManifest.xml line 6: Replace with permission:
          @@ -6 +6
          -     <provider android:name="test.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
          +     <provider android:name="test.pkg.KotlinTestContentProvider" android:permission="android.permission.READ_DATA"/>
        """
      )
  }

  fun testReadPermissionOnly_singleApiImplemented_shortProviderNamesWithDot_throwsWarnings() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              package="test.pkg">
              <application>
                <provider android:name=".JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                <provider android:name=".KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
              </application>
            </manifest>
            """
          )
          .indented(),
        javaSingleApi,
        kotlinSingleApi,
      )
      .run()
      .expect(
        """
          AndroidManifest.xml:5: Warning: test.pkg.JavaTestContentProvider implements {insert} write APIs but does not protect them with a permission. Update the <provider> tag to use android:permission or android:writePermission [ProviderReadPermissionOnly]
              <provider android:name=".JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                                                                ~~~~~~~~~~~~~~~~~~~~~~
          AndroidManifest.xml:6: Warning: test.pkg.KotlinTestContentProvider implements {insert} write APIs but does not protect them with a permission. Update the <provider> tag to use android:permission or android:writePermission [ProviderReadPermissionOnly]
              <provider android:name=".KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                                                                  ~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 2 warnings
        """
      )
      .expectFixDiffs(
        """
          Fix for AndroidManifest.xml line 5: Replace with permission:
          @@ -5 +5
          -     <provider android:name=".JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
          +     <provider android:name=".JavaTestContentProvider" android:permission="android.permission.READ_DATA"/>
          Fix for AndroidManifest.xml line 6: Replace with permission:
          @@ -6 +6
          -     <provider android:name=".KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
          +     <provider android:name=".KotlinTestContentProvider" android:permission="android.permission.READ_DATA"/>
        """
      )
  }

  fun testReadPermissionOnly_someWriteApisImplemented_throwsWarnings() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              package="test.pkg">
              <application>
                <provider android:name="test.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                <provider android:name="test.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
              </application>
            </manifest>
            """
          )
          .indented(),
        javaSomeApis,
        kotlinSomeApis,
      )
      .run()
      .expect(
        """
          AndroidManifest.xml:5: Warning: test.pkg.JavaTestContentProvider implements {insert, delete} write APIs but does not protect them with a permission. Update the <provider> tag to use android:permission or android:writePermission [ProviderReadPermissionOnly]
              <provider android:name="test.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                                                                        ~~~~~~~~~~~~~~~~~~~~~~
          AndroidManifest.xml:6: Warning: test.pkg.KotlinTestContentProvider implements {insert, delete} write APIs but does not protect them with a permission. Update the <provider> tag to use android:permission or android:writePermission [ProviderReadPermissionOnly]
              <provider android:name="test.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                                                                          ~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 2 warnings
        """
      )
      .expectFixDiffs(
        """
          Fix for AndroidManifest.xml line 5: Replace with permission:
          @@ -5 +5
          -     <provider android:name="test.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
          +     <provider android:name="test.pkg.JavaTestContentProvider" android:permission="android.permission.READ_DATA"/>
          Fix for AndroidManifest.xml line 6: Replace with permission:
          @@ -6 +6
          -     <provider android:name="test.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
          +     <provider android:name="test.pkg.KotlinTestContentProvider" android:permission="android.permission.READ_DATA"/>
        """
      )
  }

  fun testReadPermissionOnly_noWriteApisImplemented_isClean() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              package="test.pkg">
              <application>
                <provider android:name="test.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                <provider android:name="test.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                <provider android:name="test.pkg.KotlinWithThrowsTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
              </application>
            </manifest>
            """
          )
          .indented(),
        javaNoApis,
        kotlinNoApis,
        kotlinNoApisWithThrows,
      )
      .run()
      .expectClean()
  }

  fun testPermissionExists_someWriteApisImplemented_isClean() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              package="test.pkg">
              <application>
                <provider android:name="test.pkg.JavaTestContentProvider" android:permission="android.permission.READ_DATA"/>
                <provider android:name="test.pkg.KotlinTestContentProvider" android:permission="android.permission.READ_DATA"/>
              </application>
            </manifest>
            """
          )
          .indented(),
        javaSomeApis,
        kotlinSomeApis,
      )
      .run()
      .expectClean()
  }

  fun testWritePermissionExists_someWriteApisImplemented_isClean() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              package="test.pkg">
              <application>
                <provider android:name="test.pkg.JavaTestContentProvider" android:writePermission="android.permission.READ_DATA"/>
                <provider android:name="test.pkg.KotlinTestContentProvider" android:writePermission="android.permission.READ_DATA"/>
              </application>
            </manifest>
            """
          )
          .indented(),
        javaSomeApis,
        kotlinSomeApis,
      )
      .run()
      .expectClean()
  }

  fun testIsolatedClass_ReadPermissionOnly_singleWriteApiImplemented_throwsWarningInClass() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              package="test.pkg">
              <application>
                <provider android:name=".JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                <provider android:name=".KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
              </application>
            </manifest>
            """
          )
          .indented(),
        javaSingleApi,
      )
      .isolated(javaSingleApiPath)
      .run()
      .expect(
        """
          src/test/pkg/JavaTestContentProvider.java:7: Warning: test.pkg.JavaTestContentProvider implements {insert} write APIs but does not protect them with a permission. Update the <provider> tag to use android:permission or android:writePermission [ProviderReadPermissionOnly]
          public class JavaTestContentProvider extends ContentProvider {
                       ~~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 1 warnings
        """
      )
      .expectFixDiffs(
        """
          Fix for src/test/pkg/JavaTestContentProvider.java line 7: Replace with permission:
          AndroidManifest.xml:
          @@ -5 +5
          -     <provider android:name=".JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
          +     <provider android:name=".JavaTestContentProvider" android:permission="android.permission.READ_DATA"/>
        """
      )
  }

  fun testIsolatedLibraryClass_ReadPermissionOnly_singleWriteApiImplemented_throwsWarningInLibraryClass() {
    val project1 =
      project()
        .files(
          javaSingleApiJvModule,
          manifest(
              """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              package="test.jv.pkg">
              <application>
                <provider android:name=".JavaTestContentProvider" android:readPermission="android.permission.READ_DATA" />
              </application>
            </manifest>
            """
            )
            .indented(),
        )

    val project2 =
      project()
        .files(
          manifest(
              """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              package="test.pkg">
              <application>
              </application>
            </manifest>
            """
            )
            .indented()
        )
        .dependsOn(project1)

    lint()
      .projects(project1, project2)
      .isolated("../lib/src/test/jv/pkg/JavaTestContentProvider.java")
      .run()
      .expect(
        """
          ../lib/src/test/jv/pkg/JavaTestContentProvider.java:7: Warning: test.jv.pkg.JavaTestContentProvider implements {insert} write APIs but does not protect them with a permission. Update the <provider> tag to use android:permission or android:writePermission [ProviderReadPermissionOnly]
          public class JavaTestContentProvider extends ContentProvider {
                       ~~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 1 warnings
        """
      )
      .expectFixDiffs(
        """
          Fix for ../lib/src/test/jv/pkg/JavaTestContentProvider.java line 7: Replace with permission:
          AndroidManifest.xml:
          @@ -5 +5
          -     <provider android:name=".JavaTestContentProvider" android:readPermission="android.permission.READ_DATA" />
          +     <provider android:name=".JavaTestContentProvider" android:permission="android.permission.READ_DATA" />
        """
      )
  }

  fun testIsolatedManifest_ReadPermissionOnly_singleWriteApiImplemented_isClean() {
    lint()
      .files(
        manifest(
            "AndroidManifest.xml",
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              package="test.pkg">
              <application>
                <provider android:name=".JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                <provider android:name=".KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
              </application>
            </manifest>
            """,
          )
          .indented(),
        javaSingleApi,
      )
      .isolated("AndroidManifest.xml")
      .run()
      .expectClean()
  }

  fun testSuppressed_ReadPermissionOnly_someWriteApisImplemented_isClean() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              package="test.pkg">
              <application>
                <provider android:name="test.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA" tools:ignore="ProviderReadPermissionOnly"/>
                <provider android:name="test.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA" tools:ignore="ProviderReadPermissionOnly"/>
              </application>
            </manifest>
            """
          )
          .indented(),
        javaSomeApis,
        kotlinSomeApis,
      )
      .run()
      .expectClean()
  }

  fun testClassesInDifferentModules_ReadPermissionOnly_singleApiImplemented_throwsWarnings() {
    val project1 = project().files(javaSingleApiJvModule)

    val project2 = project().files(kotlinSingleApiKtModule)

    val project3 =
      project()
        .files(
          manifest(
              """
              <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                package="test.pkg">
                <application>
                  <provider android:name="test.jv.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                  <provider android:name="test.kt.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                </application>
              </manifest>
              """
            )
            .indented()
        )
        .dependsOn(project1)
        .dependsOn(project2)

    lint()
      .projects(project1, project2, project3)
      .run()
      .expect(
        """
          AndroidManifest.xml:5: Warning: test.jv.pkg.JavaTestContentProvider implements {insert} write APIs but does not protect them with a permission. Update the <provider> tag to use android:permission or android:writePermission [ProviderReadPermissionOnly]
              <provider android:name="test.jv.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                                                                           ~~~~~~~~~~~~~~~~~~~~~~
          AndroidManifest.xml:6: Warning: test.kt.pkg.KotlinTestContentProvider implements {insert} write APIs but does not protect them with a permission. Update the <provider> tag to use android:permission or android:writePermission [ProviderReadPermissionOnly]
              <provider android:name="test.kt.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                                                                             ~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 2 warnings
        """
      )
      .expectFixDiffs(
        """
          Fix for AndroidManifest.xml line 5: Replace with permission:
          @@ -5 +5
          -     <provider android:name="test.jv.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
          +     <provider android:name="test.jv.pkg.JavaTestContentProvider" android:permission="android.permission.READ_DATA"/>
          Fix for AndroidManifest.xml line 6: Replace with permission:
          @@ -6 +6
          -     <provider android:name="test.kt.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
          +     <provider android:name="test.kt.pkg.KotlinTestContentProvider" android:permission="android.permission.READ_DATA"/>
        """
      )
  }

  fun testClassesInDifferentModules_Suppressed_ReadPermissionOnly_singleApiImplemented_isClean() {
    val project1 = project().files(javaSingleApiJvModule)
    val project2 = project().files(kotlinSingleApiKtModule)
    val project3 =
      project()
        .files(
          manifest(
              """
              <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                package="test.pkg">
                <application>
                  <provider android:name="test.jv.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA" tools:ignore="ProviderReadPermissionOnly"/>
                  <provider android:name="test.kt.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA" tools:ignore="ProviderReadPermissionOnly"/>
                </application>
              </manifest>
              """
            )
            .indented()
        )
        .dependsOn(project1)
        .dependsOn(project2)

    lint().projects(project1, project2, project3).run().expectClean()
  }

  fun testHalfClassesInDifferentModulesReadPermissionOnly_singleApiImplemented_throwsWarnings() {
    val project1 = project().files(javaSingleApiJvModule)

    val project2 =
      project()
        .files(
          manifest(
              """
              <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                package="test.pkg">
                <application>
                  <provider android:name="test.jv.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                  <provider android:name=".KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                </application>
              </manifest>
              """
            )
            .indented(),
          kotlinSingleApi,
        )
        .dependsOn(project1)

    lint()
      .projects(project1, project2)
      .run()
      .expect(
        """
          AndroidManifest.xml:5: Warning: test.jv.pkg.JavaTestContentProvider implements {insert} write APIs but does not protect them with a permission. Update the <provider> tag to use android:permission or android:writePermission [ProviderReadPermissionOnly]
              <provider android:name="test.jv.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                                                                           ~~~~~~~~~~~~~~~~~~~~~~
          AndroidManifest.xml:6: Warning: test.pkg.KotlinTestContentProvider implements {insert} write APIs but does not protect them with a permission. Update the <provider> tag to use android:permission or android:writePermission [ProviderReadPermissionOnly]
              <provider android:name=".KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                                                                  ~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 2 warnings
        """
      )
      .expectFixDiffs(
        """
          Fix for AndroidManifest.xml line 5: Replace with permission:
          @@ -5 +5
          -     <provider android:name="test.jv.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
          +     <provider android:name="test.jv.pkg.JavaTestContentProvider" android:permission="android.permission.READ_DATA"/>
          Fix for AndroidManifest.xml line 6: Replace with permission:
          @@ -6 +6
          -     <provider android:name=".KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
          +     <provider android:name=".KotlinTestContentProvider" android:permission="android.permission.READ_DATA"/>
        """
      )
  }

  fun testMainManifestOverridesDefinition_ReadPermissionOnly_mainDependentNoApisImplemented_librarySingleApiImplemented_isClean() {
    val project1 =
      project()
        .files(
          manifest(
              """
              <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                package="test.jv.pkg">
                <application>
                  <provider android:name="test.kt.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                </application>
              </manifest>
              """
            )
            .indented(),
          javaNoApisJvModule,
        )
    val project2 = project().files(kotlinSingleApiKtModule)
    val project3 =
      project()
        .files(
          manifest(
              """
              <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                package="test.pkg">
                <application>
                  <provider android:name="test.jv.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                  <provider android:name="test.kt.pkg.KotlinTestContentProvider" android:permission="android.permission.READ_DATA"/>
              </application>
              </manifest>
              """
            )
            .indented()
        )
        .dependsOn(project1)
        .dependsOn(project2)

    lint().projects(project1, project2, project3).run().expectClean()
  }

  fun testDifferentModulesManifestsImpactMain_ReadPermissionOnly_mainDependentNoApisImplemented_librarySingleApiImplemented_throwsWarnings() {
    val project1 =
      project()
        .files(
          manifest(
              """
              <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                package="test.jv.pkg">
                <application>
                  <provider android:name="test.kt.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                </application>
              </manifest>
              """
            )
            .indented(),
          javaNoApisJvModule,
        )
    val project2 = project().files(kotlinSingleApiKtModule)
    val project3 =
      project()
        .files(
          manifest(
              """
              <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                package="test.pkg">
                <application>
                  <provider android:name="test.jv.pkg.JavaTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
              </application>
              </manifest>
              """
            )
            .indented()
        )
        .dependsOn(project1)
        .dependsOn(project2)

    lint()
      .projects(project1, project2, project3)
      .run()
      .expect(
        """
          ../lib/AndroidManifest.xml:5: Warning: test.kt.pkg.KotlinTestContentProvider implements {insert} write APIs but does not protect them with a permission. Update the <provider> tag to use android:permission or android:writePermission [ProviderReadPermissionOnly]
              <provider android:name="test.kt.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
                                                                             ~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 1 warnings
        """
      )
      .expectFixDiffs(
        """
          Fix for lib/AndroidManifest.xml line 5: Replace with permission:
          @@ -5 +5
          -     <provider android:name="test.kt.pkg.KotlinTestContentProvider" android:readPermission="android.permission.READ_DATA"/>
          +     <provider android:name="test.kt.pkg.KotlinTestContentProvider" android:permission="android.permission.READ_DATA"/>
        """
      )
  }

  private val javaSingleApiPath = "src/test/pkg/JavaTestContentProvider.java"
  private val javaSingleApi: TestFile =
    java(
        javaSingleApiPath,
        """
        package test.pkg;

        import android.content.ContentProvider;
        import android.net.Uri;
        import android.os.Bundle;

        public class JavaTestContentProvider extends ContentProvider {
          @Override
          public Uri insert(Uri uri, ContentValues values) {
            return insert(uri, values, null);
          }

          @Override
          public int delete(Uri uri, String selection, String[] selectionArgs) {
            throw new UnsupportedOperationException("");
          }

          @Override
          public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
          }

          private Uri insert(Uri uri, ContentValues values, Bundle extras) {
            System.out.println(uri);
          }
        }
        """,
      )
      .indented()
  private val kotlinSingleApi: TestFile =
    kotlin(
        """
        package test.pkg

        import android.content.ContentProvider
        import android.net.Uri

        class KotlinTestContentProvider : ContentProvider() {
          override fun insert(uri: Uri, values: ContentValues?): Uri? = insert(uri, values, null) ?: null

          override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
            throw UnsupportedOperationException()
          }

          override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<String>?
          ): Int = 0

          private fun insert(uri: Uri, values: ContentValues?, extras: Bundle?): Int? {
            println(uri)
            return null
          }
        }
        """
      )
      .indented()
  private val javaSomeApis: TestFile =
    java(
        """
        package test.pkg;

        import android.content.ContentProvider;
        import android.net.Uri;
        import android.os.Bundle;

        public class JavaTestContentProvider extends ContentProvider {
          @Override
          public Uri insert(Uri uri, ContentValues values) {
            return insert(uri, values, null);
          }

          @Override
          public int delete(Uri uri, String selection, String[] selectionArgs) {
            foo();
            return 0;
          }

          @Override
          public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
          }

          private Uri insert(Uri uri, ContentValues values, Bundle extras) {
            System.out.println(uri);
          }

          private void foo() {
            int a = 10;
          }
        }
        """
      )
      .indented()
  private val kotlinSomeApis: TestFile =
    kotlin(
        """
        package test.pkg

        import android.content.ContentProvider
        import android.net.Uri

        class KotlinTestContentProvider : ContentProvider() {
          override fun insert(uri: Uri, values: ContentValues?): Uri? = insert(uri, values, null) ?: null

          override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
            foo()
            return 0
          }

          override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<String>?
          ): Int = 0

          private fun insert(uri: Uri, values: ContentValues?, extras: Bundle?): Int? {
            println(uri)
            return null
          }

          private fun foo() {
            val a = 10
          }
        }
        """
      )
      .indented()
  private val javaNoApis: TestFile =
    java(
        """
        package test.pkg;

        import android.content.ContentProvider;
        import android.net.Uri;
        import android.os.Bundle;

        public class JavaTestContentProvider extends ContentProvider {
          @Override
          public Uri insert(Uri uri, ContentValues values) {
            throw new UnsupportedOperationException();
          }

          @Override
          public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
          }

          @Override
          public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
          }
        }
        """
      )
      .indented()
  private val kotlinNoApis =
    kotlin(
        """
        package test.pkg

        import android.content.ContentProvider
        import android.net.Uri

        class KotlinTestContentProvider : ContentProvider() {
          override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

          override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
            return 0
          }

          override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<String>?
          ): Int = 0
        }
        """
      )
      .indented()
  private val kotlinNoApisWithThrows =
    kotlin(
        """
        package test.pkg

        import android.content.ContentProvider
        import android.net.Uri

        class KotlinWithThrowsTestContentProvider : ContentProvider() {
          override fun insert(uri: Uri, values: ContentValues?): Uri? = throw NotImplementedError()

          override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
            error("Not supported")
          }

          override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<String>?
          ): Int = TODO()
        }
        """
      )
      .indented()
  private val javaSingleApiJvModule: TestFile =
    java(
        """
        package test.jv.pkg;

        import android.content.ContentProvider;
        import android.net.Uri;
        import android.os.Bundle;

        public class JavaTestContentProvider extends ContentProvider {
          @Override
          public Uri insert(Uri uri, ContentValues values) {
            return insert(uri, values, null);
          }

          @Override
          public int delete(Uri uri, String selection, String[] selectionArgs) {
            throw new UnsupportedOperationException("");
          }

          @Override
          public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
          }

          private Uri insert(Uri uri, ContentValues values, Bundle extras) {
            System.out.println(uri);
          }
        }
        """
      )
      .indented()
  private val kotlinSingleApiKtModule: TestFile =
    kotlin(
        """
        package test.kt.pkg

        import android.content.ContentProvider
        import android.net.Uri

        class KotlinTestContentProvider : ContentProvider() {
          override fun insert(uri: Uri, values: ContentValues?): Uri? = insert(uri, values, null)

          override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
            throw UnsupportedOperationException()
          }

          override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<String>?
          ): Int = 0

          private fun insert(uri: Uri, values: ContentValues?, extras: Bundle?) {
            println(uri)
          }
        }
        """
      )
      .indented()
  private val javaNoApisJvModule: TestFile =
    java(
        """
        package test.jv.pkg;

        import android.content.ContentProvider;
        import android.net.Uri;
        import android.os.Bundle;

        public class JavaTestContentProvider extends ContentProvider {
          @Override
          public Uri insert(Uri uri, ContentValues values) {
            throw new UnsupportedOperationException();
          }

          @Override
          public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
          }

          @Override
          public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
          }
        }
        """
      )
      .indented()
}
