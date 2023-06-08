package com.android.build.gradle.internal.privaysandboxsdk

import com.android.utils.XmlUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream

class PrivacySandboxPermissionExtractorTest {

    @Test
    fun `test extract privacy sandbox permissions`() {

        val manifestContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.myrbsdk" >

                <uses-sdk android:minSdkVersion="33" />

                <uses-permission android:name="android.permission.WAKE_LOCK" />

                <application />

            </manifest>
        """.trimIndent()

        val actual =
                extractPrivacySandboxPermissions(ByteArrayInputStream(manifestContent.toByteArray()))

        assertThat(actual.replace(System.lineSeparator(), "\n")).isEqualTo("""
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.myrbsdk"
                xmlns:tools="http://schemas.android.com/tools" >

                <uses-permission
                    android:name="android.permission.WAKE_LOCK"
                    tools:requiredByPrivacySandboxSdk="true" />

            </manifest>
        """.trimIndent())
        assertThat(XmlUtils.parseDocument(actual, true)).isNotNull()
    }

    @Test
    fun `test differently named tools attribute`() {

        val manifestContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android2="http://schemas.android.com/apk/res/android"
                xmlns:tools2="http://schemas.android.com/tools"
                package="com.myrbsdk" >

                <uses-permission android2:name="android.permission.WAKE_LOCK" />

            </manifest>
        """.trimIndent()

        val actual =
                extractPrivacySandboxPermissions(ByteArrayInputStream(manifestContent.toByteArray()))

        assertThat(actual.replace(System.lineSeparator(), "\n")).isEqualTo("""
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android2="http://schemas.android.com/apk/res/android"
            xmlns:tools2="http://schemas.android.com/tools"
            package="com.myrbsdk" >

            <uses-permission
                android2:name="android.permission.WAKE_LOCK"
                tools2:requiredByPrivacySandboxSdk="true" />

        </manifest>
    """.trimIndent())
        assertThat(XmlUtils.parseDocument(actual, true)).isNotNull()
    }
}

