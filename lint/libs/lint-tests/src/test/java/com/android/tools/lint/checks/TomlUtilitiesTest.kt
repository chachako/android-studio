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
package com.android.tools.lint.checks

import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.lint.client.api.LintTomlValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class TomlUtilitiesTest {

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

    libraryName("foo-1_0", "com.google:foo:1.0", includeVersions = true)
    libraryName("google-foo-1_0", "com.google:foo:1.0", "foo-1_0", includeVersions = true)
    libraryName(
      "com-google-foo-1_0",
      "com.google:foo:1.0",
      "foo-1_0",
      "google-foo-1_0",
      includeVersions = true
    )
    libraryName(
      "com-google-foo-1_0-2",
      "com.google:foo:1.0",
      "foo-1_0",
      "google-foo-1_0",
      "com-google-foo-1_0",
      includeVersions = true
    )
    libraryName(
      "com-google-foo-1_0-3",
      "com.google:foo:1.0",
      "foo-1_0",
      "google-foo-1_0",
      "com-google-foo-1_0",
      "com-google-foo-1_0-2",
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
  fun testPickVersionName() {
    versionName("foo", "com.google:foo:1.0")
    versionName("foo-bar", "com.google:foo-bar:1.0", "appcompat")
    versionName("fooVersion", "com.google:foo:1.0", "barVersion")
    versionName("foo-bar", "com.google:foo-bar:1.0")
    versionName("google-foo-bar", "com.google:foo-bar:1.0", "FOO-BAR", "appcompat")
    // If we have camel case in version variables, use that here too
    versionName("fooBar", "com.google:foo-bar:1.0", "appCompat")
    versionName("google-fooBar", "com.google:foo-bar:1.0", "Foo-Bar", "fooBar")
    versionName("fooBarVersion", "com.google:foo-bar:1.0", "Foo-Bar", "fooBar", "appCompatVersion")
    versionName("google-fooBar", "com.google:foo-bar:1.0", "Foo-Bar", "fooBar", "fooBarVersion")
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

  // Test fixtures below

  private fun libraryName(
    expected: String,
    coordinateString: String,
    vararg variableNames: String,
    includeVersions: Boolean = false
  ) {
    check(expected, coordinateString, ::pickLibraryVariableName, includeVersions, *variableNames)
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
      { gc, map, _ -> pickVersionVariableName(gc, map) },
      includeVersions,
      *variableNames
    )
  }

  private fun check(
    expected: String,
    coordinateString: String,
    suggestName:
      (
        gc: GradleCoordinate, libraryMap: Map<String, LintTomlValue>, includeVersionInKey: Boolean
      ) -> String,
    includeVersions: Boolean,
    vararg variableNames: String
  ) {
    val gc = GradleCoordinate.parseCoordinateString(coordinateString)!!
    val map = LinkedHashMap<String, LintTomlValue>()
    val value = Mockito.mock(LintTomlValue::class.java) // map values are unused here
    for (variable in variableNames) {
      map[variable] = value
    }
    val name = suggestName(gc, map, includeVersions)
    assertEquals(expected, name)
    GradleDetector.reservedQuickfixNames = null
  }
}
