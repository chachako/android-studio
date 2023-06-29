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
    fun testPickPluginName() {
        pluginName("androidApplication", "org.android.application")
        pluginName("pluginId", "plugin-id", "foo")
        pluginName("pluginId", "plugin_id", "foo")
        pluginName("pluginId", "pluginId", "foo")
        pluginName(
            "orgAndroidApplication",
            "org.android.application",
            "Foo",
            "androidApplication"
        )
        pluginName(
            "orgAndroidApplicationX2",
            "org.android.application",
            "Foo",
            "androidApplication",
            "orgAndroidApplication"
        )
        pluginName(
            "orgAndroidApplicationX3",
            "org.android.application",
            "Foo",
            "androidApplication",
            "orgAndroidApplication",
            "orgAndroidApplicationX2"
        )
    }

    @Test
    fun testPickVersionName() {
        versionName("foo", "com.google:foo:1.0")
        versionName("foo-bar", "com.google:foo-bar:1.0", "appcompat")
        versionName("fooVersion", "com.google:foo:1.0", "barVersion")
        versionName("foo-bar", "com.google:foo-bar:1.0")
        versionName("google-foo-bar", "com.google:foo-bar:1.0", "FOO-BAR", "appcompat")
        // If we have camel case in version variables, use that here too
        versionName("fooBar", "com.google:foo-bar:1.0", "appCompat")
        versionName("google-fooBar", "com.google:foo-bar:1.0", "Foo-Bar", "fooBar")
        versionName(
            "fooBarVersion",
            "com.google:foo-bar:1.0",
            "Foo-Bar",
            "fooBar",
            "appCompatVersion"
        )
        versionName(
            "google-fooBar",
            "com.google:foo-bar:1.0",
            "Foo-Bar",
            "fooBar",
            "fooBarVersion"
        )
        versionName(
            "com-google-fooBar",
            "com.google:foo-bar:1.0",
            "Foo-Bar",
            "fooBar",
            "fooBarVersion",
            "google-fooBar"
        )
        versionName(
            "com-google-fooBar",
            "com.google:foo-bar:1.0",
            "Foo-Bar",
            "fooBar",
            "fooBarVersion",
            "google-fooBar",
            "google-fooBarVersion"
        )
        versionName(
            "com-google-fooBar2",
            "com.google:foo-bar:1.0",
            "Foo-Bar",
            "fooBar",
            "fooBarVersion",
            "google-fooBar",
            "google-fooBarVersion",
            "com-google-fooBar"
        )
        versionName(
            "com-google-fooBar3",
            "com.google:foo-bar:1.0",
            "Foo-Bar",
            "fooBar",
            "fooBarVersion",
            "google-fooBar",
            "google-fooBarVersion",
            "com-google-fooBar",
            "com-google-fooBar2"
        )

        versionName("google-foo-bar", "com.google:foo-bar:1.0", "foo-bar")
        versionName("com-google-foo-bar", "com.google:foo-bar:1.0", "foo-bar", "google-foo-bar")
        versionName(
            "com-google-foo-bar2",
            "com.google:foo-bar:1.0",
            "foo-bar",
            "google-foo-bar",
            "com-google-foo-bar"
        )
        versionName(
            "com-google-foo-bar3",
            "com.google:foo-bar:1.0",
            "foo-bar",
            "google-foo-bar",
            "com-google-foo-bar",
            "com-google-foo-bar2"
        )
    }

    @Test
    fun testPickPluginVersionName() {
        pluginVersionName("android-application", "org.android.application", "application")
        pluginVersionName("fooVersion", "foo", "barVersion")
        pluginVersionName("foo-bar", "foo_bar")
        pluginVersionName("foo-bar", "foo-bar")
        pluginVersionName("google-foo-bar", "com.google.foo-bar", "FOO-BAR", "appcompat")
        pluginVersionName("googleFooBar", "com.google.foo-bar", "appCompat")
        pluginVersionName("googleFooBar", "com.google.foo-bar", "Foo-Bar", "fooBar")

        pluginVersionName(
            "com-google-foo-bar2",
            "com.google.foo-bar",
            "foo-bar",
            "google-foo-bar",
            "com-google-foo-bar"
        )

        pluginVersionName(
            "com-google-foo-bar3",
            "com.google.foo-bar",
            "foo-bar",
            "google-foo-bar",
            "com-google-foo-bar",
            "com-google-foo-bar2"
        )
    }


    @Test
    fun testToSafeKey() {
        assertEquals("org-company_all","org.company+all".toSafeKey())
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
            { dependency, set ->
                pickVersionVariableName(dependency, set)
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
