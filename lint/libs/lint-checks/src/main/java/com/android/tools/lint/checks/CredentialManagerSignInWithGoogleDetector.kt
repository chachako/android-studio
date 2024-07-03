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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import java.util.EnumSet
import org.jetbrains.uast.UReferenceExpression

/**
 * Reports all references to `GetGoogleIdOption` and `GetSignInWithGoogleOption`, unless we see a
 * reference to `GoogleIdTokenCredential`.
 */
class CredentialManagerSignInWithGoogleDetector : Detector(), SourceCodeScanner {

  override fun getApplicableReferenceNames() =
    listOf("GetGoogleIdOption", "GetSignInWithGoogleOption", "GoogleIdTokenCredential")

  override fun visitReference(
    context: JavaContext,
    reference: UReferenceExpression,
    referenced: PsiElement,
  ) {
    val qualifiedName = (referenced as? PsiClass)?.qualifiedName ?: return
    if (!qualifiedName.startsWith(GOOGLE_ID_PACKAGE_DOT)) return
    val className = qualifiedName.removePrefix(GOOGLE_ID_PACKAGE_DOT)
    // Avoid something like: "${GOOGLE_ID_PACKAGE_DOT}aaaaaaaaa.GetGoogleIdOption"
    if (className !in getApplicableReferenceNames()) return

    val partialResults = context.getPartialResults(ISSUE).map()
    when (className) {
      "GetGoogleIdOption",
      "GetSignInWithGoogleOption" -> {
        // Skip if suppressed.
        if (context.driver.isSuppressed(context, ISSUE, reference)) return
        // Otherwise, we store the location.
        partialResults
          .getOrPutLintMap(KEY_OPTION_REFS)
          .appendLocation(context.getLocation(reference))
      }
      "GoogleIdTokenCredential" -> {
        partialResults.put(KEY_SAW_TOKEN_REF, true)
      }
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    if (context.project.isLibrary) return

    // If we saw a reference to the token class in any project, then we are done.
    if (partialResults.maps().any { it.getBoolean(KEY_SAW_TOKEN_REF) == true }) {
      return
    }

    // Otherwise: report all references to Google ID classes.
    val locations =
      partialResults
        .maps()
        // Each module's LintMap _might_ contain a LintMap of locations.
        .mapNotNull { it.getMap(KEY_OPTION_REFS) }
        // We can flatten these into a single list of locations.
        .flatMap { it.asLocationSequence() }

    for (location in locations) {
      context.report(
        ISSUE,
        location,
        "Use of `:googleid` classes without use of `GoogleIdTokenCredential`",
      )
    }
  }

  override fun afterCheckRootProject(context: Context) {
    if (context.isGlobalAnalysis()) {
      checkPartialResults(context, context.getPartialResults(ISSUE))
    }
  }

  companion object {

    private const val KEY_OPTION_REFS = "OPTION_REFS"
    private const val KEY_SAW_TOKEN_REF = "SAW_TOKEN_REF"
    private const val GOOGLE_ID_PACKAGE_DOT = "com.google.android.libraries.identity.googleid."

    private val IMPLEMENTATION =
      Implementation(
        CredentialManagerSignInWithGoogleDetector::class.java,
        EnumSet.of(Scope.ALL_JAVA_FILES),
      )

    @JvmField
    val ISSUE =
      Issue.create(
        id = "CredentialManagerSignInWithGoogle",
        briefDescription = "Misuse of Sign in with Google API",
        explanation =
          """
          When using `:googleid` classes like `GetGoogleIdOption` and `GetSignInWithGoogleOption`, \
          you typically must handle the response using \
          `GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL` or \
          `GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL`.

          This check reports all uses of these `:googleid` classes if there are no \
          references to `GoogleIdTokenCredential`.
          """,
        moreInfo =
          "https://developer.android.com/identity/sign-in/credential-manager-siwg#create-sign",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )
  }
}
