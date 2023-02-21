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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationOrigin
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass

class InconsistentThreadingAnnotationDetector : Detector(), SourceCodeScanner {

  companion object Issues {
    private val threadingAnnotations =
      listOf(
        "com.android.annotations.concurrency.UiThread",
        "com.android.annotations.concurrency.WorkerThread",
        // TODO: add @AnyThread and @Slow annotations
      )

    private val IMPLEMENTATION =
      Implementation(InconsistentThreadingAnnotationDetector::class.java, Scope.JAVA_FILE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
        id = "InconsistentThreadingAnnotation",
        briefDescription = "Add threading annotation to overridden method",
        explanation =
          """
                To be properly instrumented the threading annotations like `@UiThread`, \
                `@WorkerThread` should annotate the overridden method. These should match \
                an annotation on the base class/interface.
            """,
        category = Category.CORRECTNESS,
        severity = Severity.ERROR,
        platforms = STUDIO_PLATFORMS,
        implementation = IMPLEMENTATION
      )
  }

  override fun applicableAnnotations() = threadingAnnotations

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    return type == AnnotationUsageType.METHOD_OVERRIDE
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo
  ) {
    if (annotationInfo.origin == AnnotationOrigin.OUTER_CLASS) {
      // Threading annotations only apply to an immediate class, and so the annotations
      // set on an outer class should be ignored
      return
    }

    // Note that `usageInfo` here refers to the overridden method due to the filtering logic
    // in isApplicableAnnotationUsage() method.
    val superAnnotation = annotationInfo.qualifiedName
    val overriddenMethod = (usageInfo.usage as UMethod)
    val overriddenMethodAnnotation =
      context.evaluator
        .getAllAnnotations(overriddenMethod)
        .map { it.qualifiedName }
        .firstOrNull { threadingAnnotations.contains(it) }

    if (overriddenMethodAnnotation == null) {
      // If overridden method doesn't have a threading annotation check if it's
      // present on a containing class.
      val overriddenMethodClass = usageInfo.usage.getContainingUClass() ?: return
      val overriddenMethodClassAnnotation =
        context.evaluator
          .getAllAnnotations(overriddenMethodClass)
          .map { it.qualifiedName }
          .firstOrNull { threadingAnnotations.contains(it) }
      if (overriddenMethodClassAnnotation == null) {
        context.report(
          ISSUE,
          element,
          context.getLocation(element),
          "Overridden method needs to have a threading annotation matching the super method's annotation `$superAnnotation`"
        )
      } else if (overriddenMethodClassAnnotation != superAnnotation) {
        context.report(
          ISSUE,
          element,
          context.getLocation(element),
          "Method's effective threading annotation `$overriddenMethodClassAnnotation` doesn't match the super method's annotation `$superAnnotation`"
        )
      }
    } else if (overriddenMethodAnnotation != superAnnotation) {
      context.report(
        ISSUE,
        element,
        context.getLocation(element),
        "Method annotation `$overriddenMethodAnnotation` doesn't match the super method's annotation `$superAnnotation`"
      )
    }
  }
}
