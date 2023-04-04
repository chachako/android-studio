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

package com.android.build.gradle.internal.privaysandboxsdk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

import java.io.ByteArrayInputStream

class AsarUtilsTest {

    @Test
    fun tagAllElementsAsRequiredByPrivacySandboxSdk() {

        val manifestContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.myrbsdk" >

                <uses-sdk android:minSdkVersion="33" />

                <uses-permission android:name="android.permission.WAKE_LOCK" />

                <application />

            </manifest>
        """.trimIndent()


        val actual = tagAllElementsAsRequiredByPrivacySandboxSdk(ByteArrayInputStream(manifestContent.toByteArray()))

        assertThat(actual.replace(System.lineSeparator(), "\n")).isEqualTo("""
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.myrbsdk" >

                <uses-permission
                    android:name="android.permission.WAKE_LOCK"
                    tools:requiredByPrivacySandboxSdk="true" />

            </manifest>
        """.trimIndent())
    }
}
