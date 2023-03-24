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

package com.android.build.gradle.integration.application

import com.android.SdkConstants
import com.android.SdkConstants.FN_NAVIGATION_JSON
import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.io.path.name

class NavigationIntentFilterTest {
    private val app =
        MinimalSubProject.app("com.example.app")
            .withFile(
                "src/main/AndroidManifest.xml",
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                        <application>
                            <activity android:name="MyActivity">
                                <nav-graph android:value="@navigation/nav_app"/>
                                <nav-graph android:value="@navigation/nav_lib"/>
                            </activity>
                        </application>
                    </manifest>
                """.trimMargin()
            )
            .withFile(
                "src/main/res/navigation/nav_app.xml",
                populateNavAppContent(
                    action = "android.intent.action.APP_ACTION",
                    mimeType ="app/image/jpg"
                )
            )

    private val lib =
        MinimalSubProject.lib("com.example.lib")
            .withFile(
                "src/main/res/navigation/nav_lib.xml",
                populateNavLibContent(
                    action = "android.intent.action.LIB_ACTION",
                    mimeType = "lib/image/jpg"
                )
            )

    private fun populateNavAppContent(action: String?, mimeType: String?): String {
        val actionString = if (action != null) "app:action=\"$action\"" else ""
        val mimeTypeString = if (mimeType != null) "app:mimeType=\"$mimeType\"" else ""
        return """
            <navigation xmlns:app="http://schemas.android.com/apk/res-auto">
                <deepLink
                    $actionString
                    $mimeTypeString
                    app:uri="http://app.example.com"/>
            </navigation>
        """.trimIndent()
    }

    private fun populateNavLibContent(action: String?, mimeType: String?): String {
        val actionString = if (action != null) "app:action=\"$action\"" else ""
        val mimeTypeString = if (mimeType != null) "app:mimeType=\"$mimeType\"" else ""
        return """
            <navigation xmlns:app="http://schemas.android.com/apk/res-auto">
                <deepLink
                    $actionString
                    $mimeTypeString
                    app:uri="http://lib.example.com"/>
            </navigation>
        """.trimIndent()
    }

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":lib", lib)
                    .dependency(app, lib)
                    .dependency(app, "androidx.navigation:navigation-fragment:2.5.2")
                    .dependency(lib, "androidx.navigation:navigation-fragment:2.5.2")
                    .build()
            )
            .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            """
                android.useAndroidX=true
            """.trimIndent()
        )
    }

    @Test
    fun testNavigationIntentActionAndMimeType() {
        project.executor().run(":app:assembleDebug")
        val mergedManifest =
            project.file(
                "app/build/${SdkConstants.FD_INTERMEDIATES}/${SingleArtifact.MERGED_MANIFEST.getFolderName()}/debug/AndroidManifest.xml"
            )
        PathSubject.assertThat(mergedManifest)
            .contentWithUnixLineSeparatorsIsExactly(expectedMergedManifestContent)
    }

    /**
     * Similar to [testNavigationIntentActionAndMimeType], but we first build an AAR from lib.
     */
    @Test
    fun testNavigationIntentActionAndMimeType_withAarDependency() {
        // Add a directory and build.gradle file for the AAR.
        val libAarDir = File(project.projectDir, "lib-aar").also { it.mkdirs() }
        File(libAarDir, "build.gradle").writeText(
            """
                configurations.maybeCreate("default")
                artifacts.add("default", file('lib.aar'))
            """.trimIndent()
        )
        // Build AAR, check that it has expected navigation.json entry, and copy it to libAarDir.
        project.executor().run(":lib:assembleDebug")
        project.getSubproject("lib")
            .getAar("debug") { aar ->
                assertThat(aar.entries.map { it.name }).contains(FN_NAVIGATION_JSON)
                FileUtils.copyFile(aar.file.toFile(), File(libAarDir, "lib.aar"))
            }
        // Update the app's build.gradle and the settings.gradle.
        TestFileUtils.searchAndReplace(
            project.getSubproject("app").buildFile,
            "implementation project(':lib')",
            "implementation project(':lib-aar')",
        )
        TestFileUtils.appendToFile(project.settingsFile, "include ':lib-aar'")

        // Finally, build the APK and check the contents of the merged manifest.
        project.executor().run(":app:assembleDebug")
        val mergedManifest =
            project.file(
                "app/build/${SdkConstants.FD_INTERMEDIATES}/${SingleArtifact.MERGED_MANIFEST.getFolderName()}/debug/AndroidManifest.xml"
            )
        PathSubject.assertThat(mergedManifest)
            .contentWithUnixLineSeparatorsIsExactly(expectedMergedManifestContent)
    }

    @Test
    fun testNavigationWithDefaultAndEmptyAction() {
        val navAppFile =
            project.getSubproject(":app").file("src/main/res/navigation/nav_app.xml")
        val navLibFile =
            project.getSubproject(":lib").file("src/main/res/navigation/nav_lib.xml")
        FileUtils.writeToFile(navAppFile, populateNavAppContent(
            action = "", // should result in no action
            mimeType = "app/image/jpg"
        ))
        FileUtils.writeToFile(navLibFile, populateNavLibContent(
            action = null, // should result in default "VIEW" action
            mimeType = null // should result in no mimeType
        ))
        project.executor().run(":app:assembleDebug")
        val mergedManifest =
            project.file(
                "app/build/${SdkConstants.FD_INTERMEDIATES}/${SingleArtifact.MERGED_MANIFEST.getFolderName()}/debug/AndroidManifest.xml"
            )
        // Validate there is only one occurrence of VIEW action in the merged manifest
        PathSubject.assertThat(mergedManifest).containsExactlyOnce(
            "<action android:name=\"android.intent.action.VIEW\" />")
        // Validate there is only one occurrence of mimeType in the merged manifest
        PathSubject.assertThat(mergedManifest).containsExactlyOnce(
            "<data android:mimeType=\"app/image/jpg\" />")
        // Validate the APP_ACTION does not exist in merged manifest
        PathSubject.assertThat(mergedManifest).doesNotContain("APP_ACTION")
    }

    private val expectedMergedManifestContent: String =
        """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.app"
                android:versionCode="1" >

                <uses-sdk
                    android:minSdkVersion="14"
                    android:targetSdkVersion="14" />

                <application
                    android:appComponentFactory="androidx.core.app.CoreComponentFactory"
                    android:debuggable="true"
                    android:extractNativeLibs="true" >
                    <activity android:name="com.example.app.MyActivity" >
                        <intent-filter>
                            <action android:name="android.intent.action.APP_ACTION" />

                            <category android:name="android.intent.category.DEFAULT" />
                            <category android:name="android.intent.category.BROWSABLE" />

                            <data android:scheme="http" />
                            <data android:host="app.example.com" />
                            <data android:path="/" />
                            <data android:mimeType="app/image/jpg" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="android.intent.action.LIB_ACTION" />

                            <category android:name="android.intent.category.DEFAULT" />
                            <category android:name="android.intent.category.BROWSABLE" />

                            <data android:scheme="http" />
                            <data android:host="lib.example.com" />
                            <data android:path="/" />
                            <data android:mimeType="lib/image/jpg" />
                        </intent-filter>
                    </activity>

                    <uses-library
                        android:name="androidx.window.extensions"
                        android:required="false" />
                    <uses-library
                        android:name="androidx.window.sidecar"
                        android:required="false" />
                </application>

            </manifest>
        """.trimIndent()
}
