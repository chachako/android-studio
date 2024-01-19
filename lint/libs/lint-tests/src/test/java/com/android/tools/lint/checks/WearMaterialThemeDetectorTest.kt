package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class WearMaterialThemeDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return WearMaterialThemeDetector()
  }

  private val materialStub =
    kotlin(
        """
    package androidx.compose.material

    class MaterialTheme

    class Icon
    """
      )
      .indented()

  private val materialWearStub =
    kotlin("""
    package androidx.wear.compose.material

    class MaterialTheme
  """).indented()

  fun testJava() {
    //noinspection all // Sample code
    lint()
      .files(
        materialStub,
        materialWearStub,
        manifest(
            """
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                            <uses-feature android:name="android.hardware.type.watch" />
                            <application
                                android:icon="@mipmap/ic_launcher"
                                android:label="@string/app_name">
                            </application>
                        </manifest>
                        """
          )
          .indented(),
        java(
            """
                package test.pkg;

                import androidx.compose.material.MaterialTheme; // ERROR
                import androidx.wear.compose.material.MaterialTheme; // OK

                public class BadImport {
                }
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test/pkg/BadImport.java:3: Error: Don't use androidx.compose.material.MaterialTheme in a Wear OS project; use androidx.wear.compose.material.MaterialTheme instead [WearMaterialTheme]
            import androidx.compose.material.MaterialTheme; // ERROR
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testKotlin() {
    lint()
      .files(
        materialStub,
        materialWearStub,
        manifest(
            """
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                            <uses-feature android:name="android.hardware.type.watch" />
                            <application
                                android:icon="@mipmap/ic_launcher"
                                android:label="@string/app_name">
                            </application>
                        </manifest>
                        """
          )
          .indented(),
        kotlin(
            """
                import androidx.compose.material.MaterialTheme // ERROR
                import androidx.compose.material.Icon // OK
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test.kt:1: Error: Don't use androidx.compose.material.MaterialTheme in a Wear OS project; use androidx.wear.compose.material.MaterialTheme instead [WearMaterialTheme]
            import androidx.compose.material.MaterialTheme // ERROR
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }
}
