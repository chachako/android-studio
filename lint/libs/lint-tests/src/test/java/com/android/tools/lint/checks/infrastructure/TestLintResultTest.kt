/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestLintResult.Companion.getDiff
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test

class TestLintResultTest {
  @Test
  fun testDiff() {
    assertEquals("", getDiff("", ""))
    assertEquals("", getDiff("aaa", "aaa"))
    assertEquals(
      """
            @@ -1 +1
            - aaa
            + bbb
            """
        .trimIndent(),
      getDiff("aaa", "bbb")
    )
    assertEquals(
      """
            @@ -1 +1
            - this
            @@ -4 +3
            + new
            """
        .trimIndent(),
      getDiff(
        """
                this
                is
                a
                test
                """
          .trimIndent(),
        """
                is
                a
                new
                test
                """
          .trimIndent()
      )
    )
    assertEquals(
      """
            @@ -4 +4
            - line4
            - line5
            @@ -8 +6
            - line8
            + line7.5
            """
        .trimIndent(),
      getDiff(
        """
                line1
                line2
                line3
                line4
                line5
                line6
                line7
                line8
                line9
                """
          .trimIndent(),
        """
                line1
                line2
                line3
                line6
                line7
                line7.5
                line9
                """
          .trimIndent()
      )
    )

    assertEquals(
      """
            @@ -4 +4
              line1
              line2
              line3
            - line4
            - line5
              line6
              line7
            - line8
            + line7.5
              line9
            """
        .trimIndent(),
      getDiff(
        """
                line1
                line2
                line3
                line4
                line5
                line6
                line7
                line8
                line9
                """
          .trimIndent(),
        """
                line1
                line2
                line3
                line6
                line7
                line7.5
                line9
                """
          .trimIndent(),
        3
      )
    )
    assertEquals(
      """
            @@ -8 +8
            -         android:id="@+id/textView1"
            +         android:id="@+id/output"
            @@ -19 +19
            -         android:layout_alignLeft="@+id/textView1"
            -         android:layout_below="@+id/textView1"
            +         android:layout_alignLeft="@+id/output"
            +         android:layout_below="@+id/output"
            """
        .trimIndent(),
      getDiff(
        """
                <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    tools:context=".MainActivity" >

                    <TextView
                        android:id="@+id/textView1"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_centerVertical="true"
                        android:layout_toRightOf="@+id/button2"
                        android:text="@string/hello_world" />

                    <Button
                        android:id="@+id/button1"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignLeft="@+id/textView1"
                        android:layout_below="@+id/textView1"
                        android:layout_marginLeft="22dp"
                        android:layout_marginTop="24dp"
                        android:text="Button" />

                    <Button
                        android:id="@+id/button2"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignParentLeft="true"
                        android:layout_alignParentTop="true"
                        android:text="Button" />

                </RelativeLayout>
                """
          .trimIndent(),
        """
                <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    tools:context=".MainActivity" >

                    <TextView
                        android:id="@+id/output"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_centerVertical="true"
                        android:layout_toRightOf="@+id/button2"
                        android:text="@string/hello_world" />

                    <Button
                        android:id="@+id/button1"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignLeft="@+id/output"
                        android:layout_below="@+id/output"
                        android:layout_marginLeft="22dp"
                        android:layout_marginTop="24dp"
                        android:text="Button" />

                    <Button
                        android:id="@+id/button2"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignParentLeft="true"
                        android:layout_alignParentTop="true"
                        android:text="Button" />

                </RelativeLayout>
                """
          .trimIndent()
      )
    )
  }

  @Test
  fun testMyDiff() {
    val a =
      """
            <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                tools:context=".MainActivity" >

                <TextView
                    android:id="@+id/textView1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_centerVertical="true"
                    android:layout_toRightOf="@+id/button2"
                    android:text="@string/hello_world" />

                <Button
                    android:id="@+id/button1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignLeft="@+id/textView1"
                    android:layout_below="@+id/textView1"
                    android:layout_marginLeft="22dp"
                    android:layout_marginTop="24dp"
                    android:text="Button" />

                <Button
                    android:id="@+id/button2"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignParentLeft="true"
                    android:layout_alignParentTop="true"
                    android:text="Button" />

            </RelativeLayout>
            """
        .trimIndent()
    val b =
      """
            <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                tools:context=".MainActivity" >

                <TextView
                    android:id="@+id/textView1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_height2="wrap_content"
                    android:layout_height3="wrap_content"
                    android:layout_centerVertical="true"
                    android:layout_toRightOf="@+id/button2"
                    android:text="@string/hello_world" />

                <Button
                    android:id="@+id/button1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignLeft="@+id/textView1"
                    android:layout_below="@+id/textView1"
                    android:layout_marginLeft="22dp"
                    android:layout_marginTop="24dp"
                    android:text="Button" />

                <Button
                    android:id="@+id/button2"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignParentLeft="true"
                    android:layout_alignParentTop="true"
                    android:text="Button" />

            </RelativeLayout>
            """
        .trimIndent()
    assertEquals(
      """
            @@ -11 +11
                      android:id="@+id/textView1"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
            +         android:layout_height2="wrap_content"
            +         android:layout_height3="wrap_content"
                      android:layout_centerVertical="true"
                      android:layout_toRightOf="@+id/button2"
                      android:text="@string/hello_world" />
            """
        .trimIndent(),
      getDiff(a, b, 3)
    )
  }

  @Test
  fun testNoTrailingSpaces() {
    val a = "" + "\n" + "foo\n" + "\n"
    val b = "" + "\n" + "bar\n" + "\n"
    assertThat(getDiff(a, b, 3)).isEqualTo("" + "@@ -2 +2\n" + "\n" + "- foo\n" + "+ bar")
  }

  @Test
  fun testDiffAtEnd() {
    val a = "" + "[versions]\n" + "\n" + "[libraries]"
    val b =
      "" +
        "[versions]\n" +
        "appcompat = \"1.5.1\"\n" +
        "\n" +
        "[libraries]\n" +
        "androidx-appcompat = { module = \"androidx.appcompat:appcompat\", version.ref = \"appcompat\" }"
    assertThat(getDiff(a, b, 1))
      .isEqualTo(
        "" +
          "@@ -2 +2\n" +
          "  [versions]\n" +
          "+ appcompat = \"1.5.1\"\n" +
          "\n" +
          "  [libraries]\n" +
          "+ androidx-appcompat = { module = \"androidx.appcompat:appcompat\", version.ref = \"appcompat\" }"
      )
    assertThat(getDiff(a, b, 2))
      .isEqualTo(
        "" +
          "@@ -2 +2\n" +
          "  [versions]\n" +
          "+ appcompat = \"1.5.1\"\n" +
          "\n" +
          "  [libraries]\n" +
          "+ androidx-appcompat = { module = \"androidx.appcompat:appcompat\", version.ref = \"appcompat\" }"
      )
    assertThat(getDiff(a, b, 3))
      .isEqualTo(
        "" +
          "@@ -2 +2\n" +
          "  [versions]\n" +
          "+ appcompat = \"1.5.1\"\n" +
          "\n" +
          "  [libraries]\n" +
          "+ androidx-appcompat = { module = \"androidx.appcompat:appcompat\", version.ref = \"appcompat\" }"
      )
  }

  @Test
  fun testOverlaps() {
    val a =
      """
      [versions]
      version=1
      [libraries]
      different1
      [bundles]
      """
        .trimIndent()
    val b =
      """
      [versions]
      appcompat = "1.5.1"
      version=1
      [libraries]
      different2
      [bundles]
      androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
      """
        .trimIndent()
    assertThat(getDiff(a, b, 1))
      .isEqualTo(
        """
        @@ -2 +2
          [versions]
        + appcompat = "1.5.1"
          version=1
          [libraries]
        - different1
        + different2
          [bundles]
        + androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
        """
          .trimIndent()
      )
    assertThat(getDiff(a, b, 2))
      .isEqualTo(
        "" +
          "@@ -2 +2\n" +
          "  [versions]\n" +
          "+ appcompat = \"1.5.1\"\n" +
          "  version=1\n" +
          "  [libraries]\n" +
          "- different1\n" +
          "+ different2\n" +
          "  [bundles]\n" +
          "+ androidx-appcompat = { module = \"androidx.appcompat:appcompat\", version.ref = \"appcompat\" }"
      )
    assertThat(getDiff(a, b, 3))
      .isEqualTo(
        "" +
          "@@ -2 +2\n" +
          "  [versions]\n" +
          "+ appcompat = \"1.5.1\"\n" +
          "  version=1\n" +
          "  [libraries]\n" +
          "- different1\n" +
          "+ different2\n" +
          "  [bundles]\n" +
          "+ androidx-appcompat = { module = \"androidx.appcompat:appcompat\", version.ref = \"appcompat\" }"
      )
  }

  @Test
  fun testOverlap2() {
    val before =
      """
            import android.graphics.drawable.VectorDrawable

            class VectorDrawableProvider {
                fun getVectorDrawable(): VectorDrawable {
                    with(this) {
                        return VectorDrawable()
                    }
                }
            }
        """
        .trimIndent()

    val after =
      """
            import android.graphics.drawable.VectorDrawable
            import android.os.Build
            import android.support.annotation.RequiresApi

            class VectorDrawableProvider {
                @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
                fun getVectorDrawable(): VectorDrawable {
                    with(this) {
                        return VectorDrawable()
                    }
                }
            }
        """
        .trimIndent()

    assertThat(getDiff(before, after, 1))
      .isEqualTo(
        """
            @@ -2 +2
              import android.graphics.drawable.VectorDrawable
            + import android.os.Build
            + import android.support.annotation.RequiresApi

              class VectorDrawableProvider {
            +     @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
                  fun getVectorDrawable(): VectorDrawable {
            """
          .trimIndent()
      )
  }

  @Test
  fun testDoNotMergeNearby1() {
    // Based on GridLayoutDetectorTest#testGridLayout2 where I incorrectly
    // treat a fix diff as if the deleted and inserted lines are adjacent;
    // there is a line in the middle the diff was omitting.
    val before =
      """
        1
        2
        3
        4
        """
        .trimIndent()
    val after =
      """
        1
        new
        2
        4
        """
        .trimIndent()

    assertEquals(
      """
      @@ -2 +2
      + new
      @@ -3 +4
      - 3
      """
        .trimIndent(),
      getDiff(before, after, 0)
    )
    assertEquals(
      """
      @@ -2 +2
      + new
      - 3
      """
        .trimIndent(),
      getDiff(before, after, 0, diffCompatMode2 = true)
    )
  }

  @Test
  fun testDoNotMergeNearby2() {
    val before =
      """
        1
        2
        3
        4
        5
        """
        .trimIndent()
    val after =
      """
        1
        new
        2
        4
        5
        """
        .trimIndent()
    assertEquals(
      """
      @@ -2 +2
      + new
      @@ -3 +4
      - 3
      """
        .trimIndent(),
      getDiff(before, after, 0)
    )
  }

  @Test
  fun testDoNotMergeNearby3() {
    val before =
      """
      1
      2
      3
      4a
      5
      """
        .trimIndent()
    val after =
      """
      1
      new1
      2
      3
      4b
      5
      new2
      """
        .trimIndent()
    assertThat(getDiff(before, after, 1))
      .isEqualTo(
        """
        @@ -2 +2
          1
        + new1
          2
          3
        - 4a
        + 4b
          5
        + new2
        """
          .trimIndent()
      )
  }
}
