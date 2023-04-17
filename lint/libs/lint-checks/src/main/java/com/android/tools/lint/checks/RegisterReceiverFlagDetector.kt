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
package com.android.tools.lint.checks

import com.android.tools.lint.checks.BroadcastReceiverUtils.BROADCAST_RECEIVER_METHOD_NAMES
import com.android.tools.lint.checks.BroadcastReceiverUtils.checkIsProtectedReceiverAndReturnUnprotectedActions
import com.android.tools.lint.client.api.TYPE_INT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findArgument
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.isNullLiteral

/**
 * Detector that identifies `registerReceiver()` calls which are missing the `RECEIVER_EXPORTED` or
 * `RECEIVER_NOT_EXPORTED` flags.
 */
class RegisterReceiverFlagDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames() = BROADCAST_RECEIVER_METHOD_NAMES

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.content.Context")) return

    // The parameter positions vary across the various registerReceiver*() methods, so rather
    // than hardcode them we simply look them up based on the parameter type.
    val receiverArg = findArgument(node, "android.content.BroadcastReceiver") ?: return
    if (receiverArg.isNullLiteral()) return
    val filterArg = findArgument(node, "android.content.IntentFilter") ?: return

    val flagsArg = findArgument(node, TYPE_INT)

    val evaluator = ConstantEvaluator().allowFieldInitializers()

    val (isProtected, unprotectedActionsList) =
      checkIsProtectedReceiverAndReturnUnprotectedActions(
        filterArg,
        node,
        context.evaluator,
      )

    if (!isProtected) {
      val flags = evaluator.evaluate(flagsArg) as? Int
      val actionsList = unprotectedActionsList.joinToString(", ", "", "", -1, "")
      val registeredFor = actionsList.ifEmpty { "an IntentFilter that cannot be inspected by lint" }
      val message =
        """`${receiverArg.sourcePsi?.text ?: receiverArg.asSourceString()}` \
                |is missing `RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED` flag for unprotected \
                |broadcasts registered for $registeredFor"""
          .trimMargin()

      val lintMap = map().put(HAS_UNPROTECTED_KEY, unprotectedActionsList.isNotEmpty())
      if (flagsArg == null) {
        context.report(
          Incident(
            RECEIVER_EXPORTED_FLAG,
            node,
            context.getLocation(node),
            message,
            buildAlternativesFix(context, filterArg, false)
          ),
          lintMap
        )
      } else if (flags != null && (flags and RECEIVER_EXPORTED_FLAG_PRESENT_MASK) == 0) {
        context.report(
          Incident(
            RECEIVER_EXPORTED_FLAG,
            node,
            context.getLocation(flagsArg),
            message,
            buildAlternativesFix(context, flagsArg, true)
          ),
          lintMap
        )
      }
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    // We are only sure an app will crash if:
    // 1. the app is targeting U+ AND
    // 2. we found unprotected actions
    if (
      context.mainProject.targetSdk < VERSION_CODE_U || map.getBoolean(HAS_UNPROTECTED_KEY) != true
    ) {
      incident.overrideSeverity(Severity.WARNING)
    }
    return true
  }

  companion object {
    private const val RECEIVER_EXPORTED = 0x2
    private const val RECEIVER_NOT_EXPORTED = 0x4
    private const val RECEIVER_EXPORTED_FLAG_PRESENT_MASK =
      RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED

    private const val FLAG_EXPORTED_STR = "android.content.Context.RECEIVER_EXPORTED"
    private const val FLAG_NOT_EXPORTED_STR = "android.content.Context.RECEIVER_NOT_EXPORTED"

    // TODO(mattgilbride@) - use VersionCodes.U once added and SdkVersionInfo.HIGHEST_SUPPORTED_API
    // is updated
    private const val VERSION_CODE_U = 34

    private const val HAS_UNPROTECTED_KEY = "hasUnprotected"

    private const val EXPLANATION =
      """
      In Android U, all receivers registering for non-system broadcasts are required \
      to include a flag indicating the receiver's exported state. Apps registering for non-system \
      broadcasts should use the `ContextCompat#registerReceiver` APIs with flags set to either \
      `RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED`.

      If you are not expecting broadcasts from other apps on the device, \
      register your receiver with `RECEIVER_NOT_EXPORTED` to protect your receiver on all platform releases.
      """

    @JvmField
    val RECEIVER_EXPORTED_FLAG: Issue =
      Issue.create(
        id = "UnspecifiedRegisterReceiverFlag",
        briefDescription = "Missing `registerReceiver()` exported flag",
        explanation = EXPLANATION,
        moreInfo =
          "https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int)",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation =
          Implementation(
            RegisterReceiverFlagDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
          )
      )

    private fun buildAlternativesFix(
      context: JavaContext,
      arg: UExpression,
      isFlagsArg: Boolean,
    ): LintFix =
      LintFix.create()
        .alternatives(
          buildFlagFix(context, arg, isFlagsArg),
          buildFlagFix(context, arg, isFlagsArg, true)
        )

    private fun buildFlagFix(
      context: JavaContext,
      arg: UExpression,
      isFlagsArg: Boolean,
      exported: Boolean = false
    ): LintFix {

      val addFlagText = if (exported) FLAG_EXPORTED_STR else FLAG_NOT_EXPORTED_STR
      val name = if (exported) "Add RECEIVER_EXPORTED" else "Add RECEIVER_NOT_EXPORTED (preferred)"
      val isKotlin = context.uastFile?.lang == KotlinLanguage.INSTANCE
      val originalArgString = arg.asSourceString()

      val fixText =
        if (!isFlagsArg) ", $addFlagText"
        else if (originalArgString == "0") addFlagText
        else if (isKotlin) "$originalArgString or $addFlagText"
        else "$originalArgString | $addFlagText"

      val fix =
        LintFix.create()
          .name(name)
          .replace()
          .reformat(true)
          .shortenNames()
          .range(context.getLocation(arg))
          .with(fixText)

      if (!isFlagsArg) fix.end()

      return fix.build()
    }
  }
}
