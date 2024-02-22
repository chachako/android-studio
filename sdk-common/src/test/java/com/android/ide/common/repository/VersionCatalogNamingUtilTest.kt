/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.ide.common.repository

import com.android.ide.common.gradle.Dependency
import org.junit.Test
import org.junit.Assert.assertEquals

class VersionCatalogNamingUtilTest {

    @Test
    fun testPickLibraryName() {
        libraryName("foo", "com.google:foo:1.0")
        libraryName("foo", "com.google:foo:1.0", "bar")
        libraryName("google-foo", "com.google:foo:1.0", "foo")
        libraryName("google-foo", "com.google:foo:1.0", "Foo")
        libraryName("com-google-foo", "com.google:foo:1.0", "Foo", "Google-foo")
        libraryName("com-google-foo2", "com.google:foo:1.0", "Foo", "Google-foo", "com-google-foo")
        libraryName(
            "com-google-foo3",
            "com.google:foo:1.0",
            "Foo",
            "Google-foo",
            "com-google-foo",
            "com-google-foo2"
        )

        libraryName("foo-v10", "com.google:foo:1.0", includeVersions = true)
        libraryName("google-foo-v10", "com.google:foo:1.0", "foo-v10", includeVersions = true)
        libraryName(
            "com-google-foo-v10",
            "com.google:foo:1.0",
            "foo-v10",
            "google-foo-v10",
            includeVersions = true
        )
        libraryName(
            "com-google-foo-v10-x2",
            "com.google:foo:1.0",
            "foo-v10",
            "google-foo-v10",
            "com-google-foo-v10",
            includeVersions = true
        )
        libraryName(
            "com-google-foo-v10-x3",
            "com.google:foo:1.0",
            "foo-v10",
            "google-foo-v10",
            "com-google-foo-v10",
            "com-google-foo-v10-x2",
            includeVersions = true
        )

        libraryName("kotlin-reflect", "org.jetbrains.kotlin:kotlin-reflect:1.0")
        libraryName(
            "jetbrains-kotlin-reflect",
            "org.jetbrains.kotlin:kotlin-reflect:1.0",
            "kotlin-reflect"
        )

        // Special handling for androidx: if any libraries use androidx- as a prefix, use that too
        libraryName("test-foo", "androidx.test:foo:1.0", "foo")
        libraryName("androidx-foo", "androidx.test:foo:1.0", "foo", "androidx-recyclerview")
    }

    @Test
    fun testPickAndroidxLibraryNameKebabCase() {
        libraryName("lifecycle-runtime-ktx", "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1", "foo")

        libraryName("androidx-lifecycle-runtime-ktx", "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1", "lifecycle-runtime-ktx")

        libraryName(
            "androidx-lifecycle-lifecycle-runtime-ktx",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1",
            "lifecycle-runtime-ktx",
            "lifecycle-lifecycle-runtime-ktx",
            "androidx-lifecycle-runtime-ktx"
        )
        libraryName(
            "androidx-lifecycle-lifecycle-runtime-ktx2",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1",
            "lifecycle-runtime-ktx",
            "lifecycle-lifecycle-runtime-ktx",
            "androidx-lifecycle-runtime-ktx",
            "androidx-lifecycle-lifecycle-runtime-ktx",
        )
        libraryName(
            "androidx-lifecycle-lifecycle-runtime-ktx3",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1",
            "lifecycle-runtime-ktx",
            "lifecycle-lifecycle-runtime-ktx",
            "androidx-lifecycle-runtime-ktx",
            "androidx-lifecycle-lifecycle-runtime-ktx",
            "androidx-lifecycle-lifecycle-runtime-ktx2"
        )
    }

    @Test
    fun testPickLibraryWithCamelCaseFallback() {
        libraryName("foo-bar", "com.google:foo-bar:1.0")
        libraryName("fooBar", "com.google:foo-bar:1.0", "googleFoo")
        libraryName("google-foo", "com.google:foo:1.0", "foo")
        libraryName("googleFoo", "com.google:foo:1.0", "aFoo","Foo")
        libraryName("comGoogleFoo", "com.google:foo:1.0", "Foo", "googleFoo")
        libraryName("comGoogleFoo2", "com.google:foo:1.0", "Foo", "GoogleFoo", "comGoogleFoo")
        libraryName(
            "comGoogleFoo3",
            "com.google:foo:1.0",
            "Foo",
            "GoogleFoo",
            "comGoogleFoo",
            "comGoogleFoo2"
        )

        libraryName("fooV10", "com.google:foo:1.0", "aFoo", includeVersions = true)
        libraryName("googleFooV10", "com.google:foo:1.0", "fooV10", includeVersions = true)
        libraryName(
            "comGoogleFooV10",
            "com.google:foo:1.0",
            "fooV10",
            "googleFooV10",
            includeVersions = true
        )
        libraryName(
            "comGoogleFooV10X2",
            "com.google:foo:1.0",
            "fooV10",
            "googleFooV10",
            "comGoogleFooV10",
            includeVersions = true
        )
        libraryName(
            "comGoogleFooV10X3",
            "com.google:foo:1.0",
            "fooV10",
            "googleFooV10",
            "comGoogleFooV10",
            "comGoogleFooV10X2",
            includeVersions = true
        )

        libraryName("kotlinReflect", "org.jetbrains.kotlin:kotlin-reflect:1.0", "aFoo")
        libraryName(
            "jetbrainsKotlinReflect",
            "org.jetbrains.kotlin:kotlin-reflect:1.0",
            "kotlinReflect"
        )

        // Special handling for androidx: if any libraries use androidx- as a prefix, use that too
        libraryName("testFoo", "androidx.test:foo:1.0", "foo", "aFoo")
        libraryName("androidxFoo", "androidx.test:foo:1.0", "foo", "testFoo")
    }

    @Test
    fun testPickPluginName() {
        pluginName("android-application", "org.android.application")
        pluginName("baselineprofile", "androidx.baselineprofile")
        pluginName("plugin-id", "plugin-id", "foo")
        pluginName("plugin-id", "plugin_id", "foo")
        pluginName("pluginid", "pluginId", "foo") // camelCase input is downcased for safety
        pluginName(
            "org-android-application",
            "org.android.application",
            "Foo",
            "android-application"
        )
        pluginName(
            "org-android-applicationX2",
            "org.android.application",
            "Foo",
            "android-application",
            "org-android-application"
        )
        pluginName(
            "org-android-applicationX3",
            "org.android.application",
            "Foo",
            "android-application",
            "org-android-application",
            "org-android-applicationX2"
        )
    }


    @Test
    fun testPickPluginNameWithCamelCaseFallback() {
        pluginName("androidApplication", "org.android.application", "aFoo")
        pluginName("baselineprofile", "androidx.baselineprofile")
        pluginName("pluginId", "plugin-id", "aFoo")
        pluginName("pluginid", "pluginId", "aFoo") // camelCase input is downcased for safety
        pluginName(
            "orgAndroidApplication",
            "org.android.application",
            "aFoo",
            "androidApplication"
        )
        pluginName(
            "orgAndroidApplicationX2",
            "org.android.application",
            "aFoo",
            "androidApplication",
            "orgAndroidApplication"
        )
        pluginName(
            "orgAndroidApplicationX3",
            "org.android.application",
            "aFoo",
            "androidApplication",
            "orgAndroidApplication",
            "orgAndroidApplicationX2"
        )
    }

    @Test
    fun testPickVersionName() {
        versionName("foo", "com.google:foo:1.0")
        versionName("fooBar", "com.google:foo-bar:1.0", "appcompat")
        versionName("fooVersion", "com.google:foo:1.0", "barVersion")
        versionName("fooBar", "com.google:foo-bar:1.0")
        versionName("foo-bar-version", "com.google:foo-bar:1.0", "FOO-BAR", "appcompat")
        // If we have camel case in version variables, use that here too
        versionName("fooBar", "com.google:foo-bar:1.0", "appCompat")
        versionName("fooBarVersion", "com.google:foo-bar:1.0", "Foo-Bar", "fooBar")
        versionName(
            "fooBarVersion",
            "com.google:foo-bar:1.0",
            "Foo-Bar",
            "fooBar",
            "appCompatVersion"
        )
        versionName(
            "googleFooBar",
            "com.google:foo-bar:1.0",
            "Foo-Bar",
            "fooBar",
            "fooBarVersion"
        )
        versionName(
            "googleFooBarVersion",
            "com.google:foo-bar:1.0",
            "Foo-Bar",
            "fooBar",
            "fooBarVersion",
            "googleFooBar"
        )
        versionName(
            "comGoogleFooBar",
            "com.google:foo-bar:1.0",
            "Foo-Bar",
            "fooBar",
            "fooBarVersion",
            "googleFooBar",
            "googleFooBarVersion"
        )
        versionName(
            "comGoogleFooBar2",
            "com.google:foo-bar:1.0",
            "Foo-Bar",
            "fooBar",
            "fooBarVersion",
            "googleFooBar",
            "googleFooBarVersion",
            "comGoogleFooBar",
        )
        versionName(
            "comGoogleFooBar3",
            "com.google:foo-bar:1.0",
            "Foo-Bar",
            "fooBar",
            "fooBarVersion",
            "googleFooBar",
            "googleFooBarVersion",
            "comGoogleFooBar",
            "comGoogleFooBar2"
        )

        versionName("foo-bar-version", "com.google:foo-bar:1.0", "foo-bar")
        versionName("google-foo-bar", "com.google:foo-bar:1.0", "foo-bar", "foo-bar-version")
        versionName(
            "google-foo-bar-version",
            "com.google:foo-bar:1.0",
            "foo-bar",
            "foo-bar-version",
            "google-foo-bar"
        )
        versionName(
            "com-google-foo-bar",
            "com.google:foo-bar:1.0",
            "foo-bar",
            "foo-bar-version",
            "google-foo-bar",
            "google-foo-bar-version"
        )
        versionName(
            "com-google-foo-bar2",
            "com.google:foo-bar:1.0",
            "foo-bar",
            "foo-bar-version",
            "google-foo-bar",
            "google-foo-bar-version",
            "com-google-foo-bar"
        )
        versionName(
            "com-google-foo-bar3",
            "com.google:foo-bar:1.0",
            "foo-bar",
            "foo-bar-version",
            "google-foo-bar",
            "google-foo-bar-version",
            "com-google-foo-bar",
            "com-google-foo-bar2"
        )
    }
    @Test
    fun testPickAndroidXKebabCaseVersionName() {
        versionName(
            "lifecycle-runtime-ktx",
                    "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1",
                    "foo-bar"
        )
        versionName(
            "lifecycle-runtime-ktx-version",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1",
            "lifecycle-runtime-ktx"
        )
        versionName(
            "androidx-lifecycle-runtime-ktx",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1",
            "lifecycle-runtime-ktx",
            "lifecycle-runtime-ktx-version"
        )
        versionName(
            "androidx-lifecycle-runtime-ktx-version",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1",
            "lifecycle-runtime-ktx",
            "lifecycle-runtime-ktx-version",
            "androidx-lifecycle-runtime-ktx"
        )
        versionName(
            "androidx-lifecycle-lifecycle-runtime-ktx",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1",
            "lifecycle-runtime-ktx",
            "lifecycle-runtime-ktx-version",
            "androidx-lifecycle-runtime-ktx",
            "androidx-lifecycle-runtime-ktx-version"
        )
        versionName(
            "androidx-lifecycle-lifecycle-runtime-ktx2",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1",
            "lifecycle-runtime-ktx",
            "lifecycle-runtime-ktx-version",
            "androidx-lifecycle-runtime-ktx",
            "androidx-lifecycle-runtime-ktx-version",
            "androidx-lifecycle-lifecycle-runtime-ktx"
        )
        versionName(
            "androidx-lifecycle-lifecycle-runtime-ktx3",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1",
            "lifecycle-runtime-ktx",
            "lifecycle-runtime-ktx-version",
            "androidx-lifecycle-runtime-ktx",
            "androidx-lifecycle-runtime-ktx-version",
            "androidx-lifecycle-lifecycle-runtime-ktx",
            "androidx-lifecycle-lifecycle-runtime-ktx2"
        )

    }

    @Test
    fun testPickPluginVersionName() {
        pluginVersionName("androidApplication", "org.android.application", "application")
        pluginVersionName("fooVersion", "foo", "barVersion")
        pluginVersionName("foo-bar", "foo_bar")
        pluginVersionName("foo-bar", "foo-bar")
        pluginVersionName("google-foo-bar", "com.google.foo-bar", "FOO-BAR", "appcompat")
        pluginVersionName("googleFooBar", "com.google.foo-bar", "appCompat")
        pluginVersionName("googleFooBar", "com.google.foo-bar", "Foo-Bar", "fooBar")

        pluginVersionName(
            "google-foo-bar-version",
            "com.google.foo-bar",
            "foo-bar",
            "google-foo-bar",
        )

        pluginVersionName(
            "com-google-foo-bar2",
            "com.google.foo-bar",
            "foo-bar",
            "google-foo-bar",
            "google-foo-bar-version",
            "com-google-foo-bar"
        )

        pluginVersionName(
            "com-google-foo-bar3",
            "com.google.foo-bar",
            "foo-bar",
            "google-foo-bar",
            "google-foo-bar-version",
            "com-google-foo-bar",
            "com-google-foo-bar2"
        )
    }

    @Test
    fun testToSafeKey() {
        assertEquals("empty", "".toSafeKey())
        assertEquals("ax", "a".toSafeKey())
        assertEquals("ax", "A".toSafeKey())
        assertEquals("xx", "1".toSafeKey())
        assertEquals("xx", ".".toSafeKey())
        assertEquals("xx", "-".toSafeKey())
        assertEquals("xx", "_".toSafeKey())
        assertEquals("xx", "%".toSafeKey())
        assertEquals("a-z", "a-".toSafeKey())
        assertEquals("b-z", "B-".toSafeKey())
        assertEquals("x-z", "--".toSafeKey())
        assertEquals("a-z", "a.".toSafeKey())
        assertEquals("b-z", "b.".toSafeKey())
        assertEquals("x-z", "1.".toSafeKey())
        assertEquals("x_z", "1%%%%".toSafeKey())
        assertEquals("b_z", "b%%%%".toSafeKey())
        assertEquals("a_z", "A%%%%".toSafeKey())
        assertEquals("gradle", "gradle".toSafeKey())
        assertEquals("gradle", "Gradle".toSafeKey())
        assertEquals("xradle", "1radle".toSafeKey())
        assertEquals("gradle-z", "gradle-".toSafeKey())
        assertEquals("gradle-z", "Gradle-".toSafeKey())
        assertEquals("xradle-z", "1radle-".toSafeKey())
        assertEquals("gradle_z", "gradle+".toSafeKey())
        assertEquals("gradle_z", "Gradle+".toSafeKey())
        assertEquals("xradle_z", "1radle+".toSafeKey())
        assertEquals("gradle-z", "gradle.".toSafeKey())
        assertEquals("gradle-z", "Gradle.".toSafeKey())
        assertEquals("xradle-z", "1radle.".toSafeKey())
        assertEquals("org-company_all", "org-company_all".toSafeKey())
        assertEquals("org-company_all", "org.company+all".toSafeKey())
        assertEquals("mpandroidchart", "MPAndroidChart".toSafeKey())
    }

    // Test fixtures below
    private fun libraryName(
        expected: String,
        coordinateString: String,
        vararg variableNames: String,
        includeVersions: Boolean = false
    ) {
        check(
            expected,
            coordinateString,
            { dependency, libraries, include ->
                pickLibraryVariableName(dependency, include, libraries) },
            includeVersions,
            *variableNames
        )
    }

    private fun pluginName(
        expected: String,
        pluginId: String,
        vararg variableNames: String,
    ) {
        checkPlugin(
            expected,
            pluginId,
            { pluginId, plugins ->
                pickPluginVariableName(pluginId, plugins) },
            *variableNames
        )
    }

    private fun versionName(
        expected: String,
        coordinateString: String,
        vararg variableNames: String,
        includeVersions: Boolean = false
    ) {
        check(
            expected,
            coordinateString,
            { dependency, set, _ ->
                pickVersionVariableName(dependency, set)
            },
            includeVersions,
            *variableNames
        )
    }

    private fun pluginVersionName(
        expected: String,
        pluginId: String,
        vararg variableNames: String,
    ) {
        checkPlugin(
            expected,
            pluginId,
            { pluginId, set ->
                pickPluginVersionVariableName(pluginId, set)
            },
            *variableNames
        )
    }

    private fun check(
        expected: String,
        coordinateString: String,
        suggestName:
            (
            dependency: Dependency,
            reserved: Set<String>,
            allowExisting: Boolean
        ) -> String,
        includeVersions: Boolean,
        vararg variableNames: String
    ) {
        val dependency = Dependency.parse(coordinateString)
        val reserved = setOf(*variableNames)
        val name = suggestName(dependency, reserved, includeVersions)
        assertEquals(expected, name)
    }

    private fun checkPlugin(
        expected: String,
        pluginId: String,
        suggestName: (String, Set<String>) -> String,
        vararg variableNames: String
    ) {
        val reserved = setOf(*variableNames)
        val name = suggestName(pluginId, reserved)
        assertEquals(expected, name)
    }
}
