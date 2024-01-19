/*
 * Copyright (C) 2021 The Android Open Source Project
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

class TileProviderDetectorTest : AbstractCheckTest() {
  override fun getDetector() = TileProviderDetector()

  fun testDocumentationExample() {
    // Missing permission
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <uses-feature android:name="android.hardware.type.watch" />
                        <application
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name" >
                        </application>
                        <service
                            android:name=".MyTileProvider">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                        </service>
                    </manifest>""",
          )
          .indented()
      )
      .run()
      .expect(
        """
                    AndroidManifest.xml:8: Warning: TileProvider does not specify BIND_TILE_PROVIDER permission [TileProviderPermissions]
                        <service
                         ~~~~~~~
                    AndroidManifest.xml:8: Warning: Tiles need preview assets [SquareAndRoundTilePreviews]
                        <service
                        ^
                    0 errors, 2 warnings
                    """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                    Fix for AndroidManifest.xml line 8: Add BIND_TILE_PROVIDER permission:
                    @@ -12 +12

                    -     <service android:name=".MyTileProvider" >
                    +     <service
                    +         android:name=".MyTileProvider"
                    +         android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER" >
                              <intent-filter>"""
      )
  }

  fun testWrongPermission() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <application
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name" >
                        </application>
                        <uses-feature android:name="android.hardware.type.watch" />
                        <service
                            android:name=".MyTileProvider"
                            android:permission="not.the.right.PERMISSION">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                        </service>
                    </manifest>
                    """,
          )
          .indented()
      )
      .run()
      .expect(
        """
                AndroidManifest.xml:8: Warning: TileProvider does not specify BIND_TILE_PROVIDER permission [TileProviderPermissions]
                    <service
                     ~~~~~~~
                AndroidManifest.xml:8: Warning: Tiles need preview assets [SquareAndRoundTilePreviews]
                    <service
                    ^
                0 errors, 2 warnings
                """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                    Fix for AndroidManifest.xml line 8: Change permission to BIND_TILE_PROVIDER:
                    @@ -14 +14
                              android:name=".MyTileProvider"
                    -         android:permission="not.the.right.PERMISSION" >
                    +         android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER" >
                              <intent-filter>"""
      )
  }

  fun testCorrectPermission() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <uses-feature android:name="android.hardware.type.watch" />
                        <application
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name" >
                        </application>
                        <service
                            android:name=".MyTileProvider"
                            android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                        </service>
                    </manifest>""",
          )
          .indented()
      )
      .run()
      .expect(
        """
            AndroidManifest.xml:8: Warning: Tiles need preview assets [SquareAndRoundTilePreviews]
                <service
                ^
            0 errors, 1 warnings
            """
      )
  }

  fun testRoundAndSquareClean() {
    lint()
      .files(
        image("res/drawable-ldpi/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        image("res/drawable-round/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <uses-feature android:name="android.hardware.type.watch" />
                        <application android:icon="@drawable/ic_launcher"
                                     android:label="@string/app_name" >
                        </application>
                        <service android:name=".MyTileProvider"
                                 android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                            <meta-data android:name="androidx.wear.tiles.PREVIEW"
                                       android:resource="@drawable/ic_walk" />
                        </service>
                    </manifest>"""
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testAspectRatioAndSize() {
    lint()
      .files(
        image("res/drawable-ldpi/ic_walk.png", 350, 400).fill(10, 10, 20, 20, -0xff0001),
        image("res/drawable-round/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <uses-feature android:name="android.hardware.type.watch" />
                        <application android:icon="@drawable/ic_launcher"
                                     android:label="@string/app_name" >
                        </application>
                        <service android:name=".MyTileProvider"
                                 android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                            <meta-data android:name="androidx.wear.tiles.PREVIEW"
                                       android:resource="@drawable/ic_walk" />
                        </service>
                    </manifest>"""
          )
          .indented(),
      )
      .run()
      .expect(
        """
          AndroidManifest.xml:13: Error: Tile previews should be at least 384px by 384px [TilePreviewImageFormat]
                             android:resource="@drawable/ic_walk" />
                                               ~~~~~~~~~~~~~~~~~
          AndroidManifest.xml:13: Error: Tile previews should have 1:1 aspect ratio [TilePreviewImageFormat]
                             android:resource="@drawable/ic_walk" />
                                               ~~~~~~~~~~~~~~~~~
          2 errors, 0 warnings
      """
      )
  }

  fun testAspectRatioAndSizeNonWear() {
    lint()
      .files(
        image("res/drawable-ldpi/ic_walk.png", 350, 400).fill(10, 10, 20, 20, -0xff0001),
        image("res/drawable-round/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <application android:icon="@drawable/ic_launcher"
                                     android:label="@string/app_name" >
                        </application>
                        <service android:name=".MyTileProvider"
                                 android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                            <meta-data android:name="androidx.wear.tiles.PREVIEW"
                                       android:resource="@drawable/ic_walk" />
                        </service>
                    </manifest>"""
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testAspectRatioSquare() {
    lint()
      .files(
        image("res/drawable-ldpi/ic_walk.png", 450, 400).fill(10, 10, 20, 20, -0xff0001),
        image("res/drawable-round/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <uses-feature android:name="android.hardware.type.watch" />
                        <application android:icon="@drawable/ic_launcher"
                                     android:label="@string/app_name" >
                        </application>
                        <service android:name=".MyTileProvider"
                                 android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                            <meta-data android:name="androidx.wear.tiles.PREVIEW"
                                       android:resource="@drawable/ic_walk" />
                        </service>
                    </manifest>"""
          )
          .indented(),
      )
      .run()
      .expect(
        """
          AndroidManifest.xml:13: Error: Tile previews should have 1:1 aspect ratio [TilePreviewImageFormat]
                             android:resource="@drawable/ic_walk" />
                                               ~~~~~~~~~~~~~~~~~
          1 errors, 0 warnings
      """
      )
  }

  fun testAspectRatioSquareNonWear() {
    lint()
      .files(
        image("res/drawable-ldpi/ic_walk.png", 450, 400).fill(10, 10, 20, 20, -0xff0001),
        image("res/drawable-round/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <application android:icon="@drawable/ic_launcher"
                                     android:label="@string/app_name" >
                        </application>
                        <service android:name=".MyTileProvider"
                                 android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                            <meta-data android:name="androidx.wear.tiles.PREVIEW"
                                       android:resource="@drawable/ic_walk" />
                        </service>
                    </manifest>"""
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testImageSizeRound() {
    lint()
      .files(
        image("res/drawable-ldpi/ic_walk.png", 300, 300).fill(10, 10, 20, 20, -0xff0001),
        image("res/drawable-round/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <uses-feature android:name="android.hardware.type.watch" />
                        <application android:icon="@drawable/ic_launcher"
                                     android:label="@string/app_name" >
                        </application>
                        <service android:name=".MyTileProvider"
                                 android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                            <meta-data android:name="androidx.wear.tiles.PREVIEW"
                                       android:resource="@drawable/ic_walk" />
                        </service>
                    </manifest>"""
          )
          .indented(),
      )
      .run()
      .expect(
        """
        AndroidManifest.xml:13: Error: Tile previews should be at least 384px by 384px [TilePreviewImageFormat]
                           android:resource="@drawable/ic_walk" />
                                             ~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
      """
      )
  }

  fun testImageSizeRoundNonWear() {
    lint()
      .files(
        image("res/drawable-ldpi/ic_walk.png", 300, 300).fill(10, 10, 20, 20, -0xff0001),
        image("res/drawable-round/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <application android:icon="@drawable/ic_launcher"
                                     android:label="@string/app_name" >
                        </application>
                        <service android:name=".MyTileProvider"
                                 android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                            <meta-data android:name="androidx.wear.tiles.PREVIEW"
                                       android:resource="@drawable/ic_walk" />
                        </service>
                    </manifest>"""
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testImageSizeSquare() {
    lint()
      .files(
        image("res/drawable-ldpi/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        image("res/drawable-round/ic_walk.png", 300, 300).fill(10, 10, 20, 20, -0xff0001),
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <uses-feature android:name="android.hardware.type.watch" />
                        <application android:icon="@drawable/ic_launcher"
                                     android:label="@string/app_name" >
                        </application>
                        <service android:name=".MyTileProvider"
                                 android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                            <meta-data android:name="androidx.wear.tiles.PREVIEW"
                                       android:resource="@drawable/ic_walk" />
                        </service>
                    </manifest>"""
          )
          .indented(),
      )
      .run()
      .expect(
        """
        AndroidManifest.xml:13: Error: Tile previews should be at least 384px by 384px [TilePreviewImageFormat]
                           android:resource="@drawable/ic_walk" />
                                             ~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
      """
      )
  }

  fun testImageSizeSquareNonWear() {
    lint()
      .files(
        image("res/drawable-ldpi/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        image("res/drawable-round/ic_walk.png", 300, 300).fill(10, 10, 20, 20, -0xff0001),
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <application android:icon="@drawable/ic_launcher"
                                     android:label="@string/app_name" >
                        </application>
                        <service android:name=".MyTileProvider"
                                 android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                            <meta-data android:name="androidx.wear.tiles.PREVIEW"
                                       android:resource="@drawable/ic_walk" />
                        </service>
                    </manifest>"""
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testAspectRatioRound() {
    lint()
      .files(
        image("res/drawable-ldpi/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        image("res/drawable-round/ic_walk.png", 250, 200).fill(10, 10, 20, 20, -0xff0001),
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <uses-feature android:name="android.hardware.type.watch" />
                        <application android:icon="@drawable/ic_launcher"
                                     android:label="@string/app_name" >
                        </application>
                        <service android:name=".MyTileProvider"
                                 android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                            <meta-data android:name="androidx.wear.tiles.PREVIEW"
                                       android:resource="@drawable/ic_walk" />
                        </service>
                    </manifest>"""
          )
          .indented(),
      )
      .run()
      .expect(
        """
        AndroidManifest.xml:13: Error: Tile previews should be at least 384px by 384px [TilePreviewImageFormat]
                           android:resource="@drawable/ic_walk" />
                                             ~~~~~~~~~~~~~~~~~
        AndroidManifest.xml:13: Error: Tile previews should have 1:1 aspect ratio [TilePreviewImageFormat]
                           android:resource="@drawable/ic_walk" />
                                             ~~~~~~~~~~~~~~~~~
        2 errors, 0 warnings
      """
      )
  }

  fun testMissingMetaData() {
    lint()
      .files(
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <uses-feature android:name="android.hardware.type.watch" />
                        <application android:icon="@drawable/ic_launcher"
                                     android:label="@string/app_name" >
                        </application>
                        <service android:name=".MyTileProvider"
                                 android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                        </service>
                    </manifest>"""
          )
          .indented()
      )
      .run()
      .expect(
        """
            AndroidManifest.xml:7: Warning: Tiles need preview assets [SquareAndRoundTilePreviews]
                <service android:name=".MyTileProvider"
                ^
            0 errors, 1 warnings
        """
      )
  }

  fun testOnlySquareIcons() {
    lint()
      .files(
        image("res/drawable-ldpi/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        xml(
          "res/drawable-xhdpi/ic_walk.xml",
          """<selector xmlns:android="http://schemas.android.com/apk/res/android">
                <item  android:color="#ff000000"/>
            </selector>
            """,
        ),
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <uses-feature android:name="android.hardware.type.watch" />
                        <application android:icon="@drawable/ic_launcher"
                                     android:label="@string/app_name" >
                        </application>
                        <service android:name=".MyTileProvider"
                                 android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                            <meta-data android:name="androidx.wear.tiles.PREVIEW"
                                       android:resource="@drawable/ic_walk" />
                        </service>
                    </manifest>"""
          )
          .indented(),
      )
      .run()
      .expect(
        """
            AndroidManifest.xml:13: Warning: Tiles need a preview asset in both drawable-round and drawable [SquareAndRoundTilePreviews]
                               android:resource="@drawable/ic_walk" />
                                                 ~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
        """
      )
  }

  fun testOnlyRoundIconsWithLocale() {
    lint()
      .files(
        image("res/drawable-round/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        image("res/drawable-it-round/ic_walk.png", 400, 400).fill(10, 10, 20, 20, -0xff0001),
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <uses-feature android:name="android.hardware.type.watch" />
                        <application android:icon="@drawable/ic_launcher"
                                     android:label="@string/app_name" >
                        </application>
                        <service android:name=".MyTileProvider"
                                 android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                            <meta-data android:name="androidx.wear.tiles.PREVIEW"
                                       android:resource="@drawable/ic_walk" />
                        </service>
                    </manifest>"""
          )
          .indented(),
      )
      .run()
      .expect(
        """
            AndroidManifest.xml:13: Warning: Tiles need a preview asset in both drawable-round and drawable [SquareAndRoundTilePreviews]
                               android:resource="@drawable/ic_walk" />
                                                 ~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
        """
      )
  }
}
