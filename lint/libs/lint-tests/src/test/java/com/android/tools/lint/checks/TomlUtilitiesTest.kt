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

import com.android.ide.common.gradle.Dependency
import com.android.tools.lint.client.api.LintTomlValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

/**
 * Majority of test cases for picking name logic were moved to sdk-common module along with logic
 * itself to be reused in multiple places.
 * [com.android.ide.common.repository.VersionCatalogNamingUtilTest] now covered all test cases for
 * picking library or variable names. Current class, covers mainly lint wrapper/specific logic.
 *
 * See [com.android.ide.common.repository.VersionCatalogNamingUtilTest].
 */
class TomlUtilitiesTest {

  @Test
  fun testPickLibraryName() {
    libraryName("foo", "com.google:foo:1.0")
    libraryName("foo-v10", "com.google:foo:1.0", includeVersions = true)

    libraryName("foo", "com.google:foo:1.0", "bar")
    libraryName("foo-v10", "com.google:foo:1.0", "bar", includeVersions = true)
    libraryName("google-foo", "com.google:foo:1.0", "foo")
    libraryName("foo-v10", "com.google:foo:1.0", "foo", includeVersions = true)

    GradleDetector.reservedQuickfixNames = mutableMapOf("libraries" to mutableSetOf("foo"))
    libraryName("google-foo", "com.google:foo:1.0")
    GradleDetector.reservedQuickfixNames = mutableMapOf("libraries" to mutableSetOf("foo"))
    libraryName("foo-v10", "com.google:foo:1.0", includeVersions = true)
    GradleDetector.reservedQuickfixNames =
      mutableMapOf("libraries" to mutableSetOf("foo", "foo-v10"))
    libraryName("google-foo", "com.google:foo:1.0")
    GradleDetector.reservedQuickfixNames =
      mutableMapOf("libraries" to mutableSetOf("foo", "foo-v10"))
    libraryName("google-foo-v10", "com.google:foo:1.0", includeVersions = true)
    GradleDetector.reservedQuickfixNames =
      mutableMapOf(
        "libraries" to
          mutableSetOf(
            "foo",
            "foo-v10",
            "google-foo",
            "google-foo-v10",
            "com-google-foo",
            "com-google-foo-v10"
          )
      )
    libraryName("com-google-foo2", "com.google:foo:1.0")
    GradleDetector.reservedQuickfixNames =
      mutableMapOf(
        "libraries" to
          mutableSetOf(
            "foo",
            "foo-v10",
            "google-foo",
            "google-foo-v10",
            "com-google-foo",
            "com-google-foo-v10"
          )
      )
    libraryName("com-google-foo-v10-x2", "com.google:foo:1.0", includeVersions = true)
  }

  @Test
  fun testPickVersionName() {
    // the majority of basic tests located on another level at CatalogNamingUtilTest
    versionName("foo", "com.google:foo:1.0")
    versionName("fooBar", "com.google:foo-bar:1.0", "appcompat")
    versionName("foo-bar-version", "com.google:foo-bar:1.0", "FOO-BAR", "appcompat")
    versionName("foo-bar-version", "com.google:foo-bar:1.0", "foo-bar")

    GradleDetector.reservedQuickfixNames = mutableMapOf("versions" to mutableSetOf("foo-bar"))
    versionName("foo-bar-version", "com.google:foo-bar:1.0", "appcompat")

    // If there is a preferred version variable, use it -- unless it exists, or we allow reuse
    versionName("myVariable", "com.google:foo:1.0", versionVariable = "myVariable")
    versionName("foo", "com.google:foo:1.0", "myVariable", versionVariable = "myVariable")
    versionName(
      "myVariable",
      "com.google:foo:1.0",
      "myVariable",
      versionVariable = "myVariable",
      allowExistingVersionVar = true
    )
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
      { dependency, libraries, include, _, _ ->
        pickLibraryVariableName(dependency, libraries, include)
      },
      includeVersions,
      null,
      false,
      *variableNames
    )
  }

  private fun versionName(
    expected: String,
    coordinateString: String,
    vararg variableNames: String,
    includeVersions: Boolean = false,
    versionVariable: String? = null,
    allowExistingVersionVar: Boolean = false
  ) {
    check(
      expected,
      coordinateString,
      { gc, map, _, _, _ ->
        pickVersionVariableName(gc, map, versionVariable, allowExistingVersionVar)
      },
      includeVersions,
      versionVariable,
      allowExistingVersionVar,
      *variableNames
    )
  }

  private fun check(
    expected: String,
    coordinateString: String,
    suggestName:
      (
        dependency: Dependency,
        libraryMap: Map<String, LintTomlValue>,
        includeVersionInKey: Boolean,
        preferred: String?,
        allowExisting: Boolean
      ) -> String,
    includeVersions: Boolean,
    versionVariable: String?,
    allowExisting: Boolean,
    vararg variableNames: String
  ) {
    val dependency = Dependency.parse(coordinateString)
    val map = LinkedHashMap<String, LintTomlValue>()
    val value = Mockito.mock(LintTomlValue::class.java) // map values are unused here
    for (variable in variableNames) {
      map[variable] = value
    }
    val name = suggestName(dependency, map, includeVersions, versionVariable, allowExisting)
    assertEquals(expected, name)
    GradleDetector.reservedQuickfixNames = null
  }
}
