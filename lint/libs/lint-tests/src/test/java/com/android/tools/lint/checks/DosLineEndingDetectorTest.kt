/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Detector

class DosLineEndingDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return DosLineEndingDetector()
    }

    fun test() {
        //noinspection all // Sample code
        lint().files(
            base64gzip(
                "res/layout/crcrlf.xml",
                "" +
                    "H4sIAAAAAAAA/42Ry2rDMBBF9wb/g9EHWHtjB7LLItmEkK0Z5KEaKktGGtdx" +
                    "vz7yA5qE1o12uvdc5lWe0QDTFx5hdD1nt9bYUIBtvKOmEpq5K6QMSmMLIV/1" +
                    "XLlWQvcpPQa5aiJNsviWPDtnwmZ6JtbMqhdmbqEeqGFdiRZY6boDj5Z/BzXS" +
                    "h+YXMtulSbrg5QVvfCUclu/fpQYPXa2c5Z9SG+Xeo1V00R+cp++IgqkE+x7/" +
                    "46/omdQGzXGkShzQGPfgztss5p4mOz8B2b2KZyUeRSanlSSlfD51FO+BbabY" +
                    "/QEAAA=="
            )
        ).run().expect(
            """
            res/layout/crcrlf.xml:4: Error: Incorrect line ending: found carriage return (\r) without corresponding newline (\n) [MangledCRLF]
                android:layout_height="match_parent" >
            ^
            1 errors, 0 warnings
            """
        )
    }

    fun testIgnore() {
        lint().files(
            base64gzip(
                "res/layout/crcrlf_ignore.xml",
                "" +
                    "H4sIAAAAAAAA/42SwWrEIBCG7wt5h+ADxHuIpaVQeti9LGWvYTBDHGpUdHaz" +
                    "26evmwTaQprWg+Dv9+vvOM0RLTBdcA83f+byOliXanBd9NQpYZhDLWXSBgdI" +
                    "1aJX2g8SwruMmOSiiWJX5jH72XubNt0TsXgWvbZThHakjo0SA7A2bYCIjtdB" +
                    "g9QbXiWn42vqnY+oxAFcb7F7Pu5fRPlQ7IoZat7wyifCcV7+HmWMEFrtHX9d" +
                    "sBHnf7TOuxhffaSPjIJVguMZ/+JPGJn0Bs35SUo8Jo7k+vxHoXUwfAfnykzx" +
                    "7mR1AHJPOncA8U2U8l6diV2bG/mzWzL8CZ0ik0FAAgAA"
            )
        ).run().expectClean()
    }

    fun testNegative() {
        // Make sure we don't get warnings for a correct file
        lint().files(
            xml(
                "res/layout/layout1.xml",
                """

                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical" >

                    <include
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        layout="@layout/layout2" />

                    <Button
                        android:id="@+id/button1"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button" />

                    <Button
                        android:id="@+id/button2"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button" />

                </LinearLayout>
                """
            ).indented()
        ).run().expectClean()
    }
}
