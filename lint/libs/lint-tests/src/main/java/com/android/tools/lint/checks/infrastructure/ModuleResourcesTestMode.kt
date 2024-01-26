/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_XML
import com.android.tools.lint.LintResourceRepository

/**
 * Test mode which looks for checks that are accessing module repositories, moves resources into a
 * separate library and makes sure that the associated detector doesn't attempt to access module
 * resources. Usually, the test output is the same, but not always so the output check here only
 * looks to see if lint errors are raised (such as the one flagged by [LintResourceRepository] when
 * you look up a module library resource during the analysis phase).
 */
internal class ModuleResourcesTestMode :
  SourceTransformationTestMode(
    description = "Resources In Separate Module Test Mode",
    testMode = "TestMode.MODULE_RESOURCES",
    folder = "module-resources",
  ) {

  override fun usePartialAnalysis(): Boolean = true

  override fun applies(context: TestModeContext): Boolean {
    // This mode looks specifically for lint checks that are using resource repositories
    // *and* has either multiple resource files or code and resources
    if (
      context.task.requestedResourceRepository &&
        context.task.incrementalFileName == null &&
        context.projects.size == 1
    ) {
      var hasCode = false
      var resourceCount = 0
      var hasManifest = false
      for (file in context.projects.first().files) {
        if (file.isXmlResource()) {
          resourceCount++
        } else if (file.isManifest() && file !is TestFile.ManifestTestFile) {
          // Don't count files with the manifest DSL; they typically just
          // set the minSdk. We're looking for full manifest examples
          hasManifest = true
        } else {
          val path = file.targetRelativePath ?: continue
          if (path.endsWith(DOT_KT) || path.endsWith(DOT_JAVA)) {
            hasCode = true
          }
        }
        if (resourceCount == 2 || resourceCount == 1 && (hasManifest || hasCode)) {
          return true
        }
      }
    }
    return false
  }

  override fun configureProjects(projects: List<ProjectDescription>): List<ProjectDescription> {
    val project = projects.single()

    // Split up the resource files into separate projects:
    //   * value resources (strings dimensions)
    //   * non-value resources (layouts, menus, etc.) depending on values
    //   * code depending on non-value resources
    // This catches the most common type of resource lookup -- code looking up
    // layouts and strings etc., or resources themselves looking up values
    // (for example, PxUsageDetector checking a @dimen reference from a layout).
    // It's technically possible for a lint check looking at a value resource
    // to want to look up a non-value resource, but this is rare. We have to
    // pick dependencies in one direction -- otherwise the resource lookup
    // won't return any items. And we won't catch resource lookups to other
    // resources of the same time; that can't be helped (it would be nice to
    // place every resource in its own module, but we again don't know the
    // access order between them.)

    val values =
      ProjectDescription(*project.files.filter { it.isValueXmlResource() }.toTypedArray())
        .name("values")

    val files =
      ProjectDescription(
          *project.files.filter { it.isXmlResource() && !it.isValueXmlResource() }.toTypedArray()
        )
        .name("resources")
        .dependsOn(values)

    val code =
      ProjectDescription(*project.files.filter { !it.isXmlResource() }.toTypedArray())
        .name(project.name)
        .dependsOn(files)

    return listOf(files, values, code)
  }

  override fun sameOutput(expected: String, actual: String, type: OutputKind): Boolean {
    // Looking specifically for reported errors
    return !actual.contains("[LintError]") || super.sameOutput(expected, actual, type)
  }

  override fun transformMessage(message: String): String {
    return message.replace(" lib/", " ").replace("../lib/", "")
  }

  override val diffExplanation: String =
    """
    The lint detector is triggering
    a lint error when resources are moved into their own module and
    the detector is attempting to access resources from the resource
    repository in other modules than the current one.
    """
      .trimIndent()
}

private fun TestFile.isXmlResource(): Boolean =
  targetRelativePath.endsWith(DOT_XML) && !isManifest()

private fun TestFile.isValueXmlResource(): Boolean =
  isXmlResource() && targetRelativePath.contains("res/values")

private fun TestFile.isManifest(): Boolean = targetRelativePath.endsWith(ANDROID_MANIFEST_XML)
