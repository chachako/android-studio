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

import com.android.SdkConstants.CLASS_CONTEXT
import com.android.SdkConstants.CLASS_INTENT
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.TYPE_CLASS
import com.android.tools.lint.client.api.TYPE_STRING
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findLastAssignment
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.util.containers.headTail
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryOperator.Companion.ASSIGN
import org.jetbrains.uast.UastCallKind.Companion.CONSTRUCTOR_CALL
import org.jetbrains.uast.UastCallKind.Companion.NESTED_ARRAY_INITIALIZER
import org.jetbrains.uast.UastCallKind.Companion.NEW_ARRAY_WITH_INITIALIZER
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedChain
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.util.isNewArrayWithDimensions
import org.jetbrains.uast.util.isNewArrayWithInitializer

/**
 * Looks for creations of mutable PendingIntents with implicit Intents. This type of object will
 * throw an exception in apps targeting Android 14 and above.
 */
class PendingIntentMutableImplicitDetector : Detector(), SourceCodeScanner {
  override fun getApplicableMethodNames() = PendingIntentUtils.GET_METHOD_NAMES

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, PendingIntentUtils.CLASS)) return
    val flagsArgument =
      node.getArgumentForParameter(PendingIntentUtils.GET_ARGUMENT_POSITION_FLAG) ?: return
    val flags = ConstantEvaluator.evaluate(context, flagsArgument) as? Int ?: return
    if (!isNewMutableNonExemptedPendingIntent(flags)) return
    val intentArgument =
      node.getArgumentForParameter(PendingIntentUtils.GET_ARGUMENT_POSITION_INTENT) ?: return
    if (
      isIntentExpressionImplicit(
        intentArgument,
        IntentExpressionInfo(context.evaluator, node, intentArgument)
      )
    ) {
      val incident =
        Incident(
          issue = ISSUE,
          scope = node,
          location = context.getLocation(node),
          message = "Mutable implicit `PendingIntent` will throw an exception",
          // TODO(b/272018609): Create 2 LintFixes: change to immutable or flag no create
        )
      context.report(incident, map())
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    if (context.mainProject.targetSdk < 23) return false
    if (context.mainProject.targetSdk < 34) {
      incident.overrideSeverity(Severity.WARNING)
      incident.message += " once this app starts targeting Android 14 or above"
    }
    incident.message +=
      ", follow either of these recommendations: for an existing `PendingIntent` use" +
        " `FLAG_NO_CREATE` and for a new `PendingIntent` either make it immutable or make the" +
        " `Intent` within explicit"
    return true
  }

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "MutableImplicitPendingIntent",
        briefDescription = "Mutable Implicit PendingIntent is disallowed",
        explanation =
          """
          Apps targeting Android 14 and above are not allowed to create `PendingIntents` with \
          `FLAG_MUTABLE` and an implicit intent within for security reasons.

          To retrieve an existing PendingIntent, use `FLAG_NO_CREATE`. To create a new \
          `PendingIntent`, either make the intent explicit, or make it immutable with \
          `FLAG_IMMUTABLE`.
          """,
        category = Category.SECURITY,
        priority = 5,
        /**
         * The severity of this issue is reported conditionally. See the overridden `filterIncident`
         * method.
         * - targetSdk >= 34: ERROR
         * - 23 <= targetSdk < 34: WARNING
         * - targetSdk < 23: not reported
         */
        severity = Severity.ERROR,
        implementation =
          Implementation(PendingIntentMutableImplicitDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )

    private const val CLASS_URI = "android.net.Uri"
    private val INTENT_EXPLICIT_SET_METHOD_NAMES =
      listOf("setComponent", "setClass", "setPackage", "setClassName")
    // Used for the vararg argumentTypes in JavaEvaluator#methodMatches
    private val INTENT_EXPLICIT_CONSTRUCTOR_ARGS =
      listOf(
        arrayOf(CLASS_CONTEXT, TYPE_CLASS),
        arrayOf(TYPE_STRING, CLASS_URI, CLASS_CONTEXT, TYPE_CLASS),
      )

    private data class IntentExpressionInfo(
      val javaEvaluator: JavaEvaluator,
      val call: UCallExpression,
      val intentArgument: UExpression
    )

    private fun isNewMutableNonExemptedPendingIntent(flags: Int): Boolean {
      val isFlagNoCreateSet = (flags and PendingIntentUtils.FLAG_NO_CREATE) != 0
      val isFlagMutableSet = (flags and PendingIntentUtils.FLAG_MUTABLE) != 0
      val isFlagAllowUnsafeImplicitIntentSet =
        (flags and PendingIntentUtils.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT) != 0
      return isFlagMutableSet && !isFlagNoCreateSet && !isFlagAllowUnsafeImplicitIntentSet
    }

    /**
     * Checks if an UExpression is an implicit intent by pattern matching the type of the passed
     * UExpression and calls the corresponding isIntent_TYPE_Implicit method
     */
    private fun isIntentExpressionImplicit(
      intentExp: UExpression,
      intentInfo: IntentExpressionInfo
    ): Boolean =
      when (val intent = intentExp.skipParenthesizedExprDown()) {
        is UCallExpression -> isIntentCallImplicit(intent, intentInfo)
        is UQualifiedReferenceExpression -> isIntentQualifiedImplicit(intent, intentInfo)
        is USimpleNameReferenceExpression -> isIntentSimpleNameImplicit(intent, intentInfo)
        else -> false
      }

    /**
     * Checks if an UCallExpression is an implicit intent by pattern matching its UastCallKind as
     * follows:
     * - Constructor: an intent constructor is implicit if it doesn't match any of the explicit
     *   intent constructors defined by [INTENT_EXPLICIT_CONSTRUCTOR_ARGS]
     * - ArrayInitializers: an intent array is implicit if any of expressions inside the array
     *   initializer is an implicit intent, e.g.: `new Intent[] { new Intent(), new Intent(context,
     *   SomeClass.class) }` is implicit because the first element is implicit
     */
    private fun isIntentCallImplicit(
      intent: UCallExpression,
      intentInfo: IntentExpressionInfo
    ): Boolean {
      return when (intent.kind) {
        CONSTRUCTOR_CALL -> {
          INTENT_EXPLICIT_CONSTRUCTOR_ARGS.none { constructorArgs ->
            intentInfo.javaEvaluator.methodMatches(
              intent.resolve() ?: return false,
              CLASS_INTENT,
              allowInherit = false,
              *constructorArgs
            )
          }
        }
        NESTED_ARRAY_INITIALIZER,
        NEW_ARRAY_WITH_INITIALIZER -> {
          intent.valueArguments.any { isIntentExpressionImplicit(it, intentInfo) }
        }
        // TODO(b/272014836): Handle listOf(), arrayOf() for PendingIntent.getActivities()
        else -> false
      }
    }

    /**
     * Checks if an UQualifiedReferenceExpression is an implicit intent as follows:
     *
     * An UQualifiedReferenceExpression is implicit if its receiver, i.e. the intent object, is
     * implicit and all its selectors, i.e. method calls, don't match any of the explicit set
     * methods defined by [isExplicitIntentSetMethod] and are not Intent methods, i.e. escape calls.
     *
     * See examples below:
     * - `Intent().setPackage("android.package")`
     *     - Here, the intent object `Intent()` is implicit, but the call
     *       `setPackage("android.package")` is an explicit set method, so overall the expression is
     *       not an implicit intent.
     * - `intent.setAction("TEST");` where `intent` is implicit
     *     - Given that `intent` is implicit and `setAction("TEST")` is not an explicit method, the
     *       expression is an implicit intent.
     * - `intent.foo("TEST");` where `intent` is implicit
     *     - Given that `intent` is implicit and `foo("TEST")` is not an Intent method, i.e. an
     *       escape call (we don't know what `foo()` does), so the expression is not an implicit
     *       intent.
     */
    private fun isIntentQualifiedImplicit(
      intent: UQualifiedReferenceExpression,
      intentInfo: IntentExpressionInfo
    ): Boolean {
      var qualifiedChain = intent.getQualifiedChain()
      if (qualifiedChain.isEmpty()) return false
      if (
        isKotlin(intent.lang) &&
          qualifiedChain.size >= 3 &&
          qualifiedChain[0].asSourceString() == "android" &&
          qualifiedChain[1].asSourceString() == "content"
      ) {
        // Ignore the first 2 expressions for the fully qualified case of the
        // android.content.Intent() constructor
        qualifiedChain = qualifiedChain.drop(2)
      }
      val (intentObject, calls) = qualifiedChain.headTail()
      val isIntentObjectImplicit = isIntentExpressionImplicit(intentObject, intentInfo)
      // TODO(b/274913202): Support Intent().apply { this.setPackage("test.pkg") } - calls inside
      // apply are part of a lambda
      val areCallsImplicitAndNotEscaped =
        calls.none {
          val call = it as? UCallExpression
          isExplicitIntentSetMethod(call) || isEscapeCall(call, intentInfo)
        }
      return isIntentObjectImplicit && areCallsImplicitAndNotEscaped
    }

    /**
     * Checks if an USimpleNameReferenceExpression (e.g. a variable) is an implicit intent as
     * follows:
     *
     * An USimpleNameReferenceExpression is implicit if all the following is true:
     * 1. Its last assignment is within the method where the PendingIntent is created
     * 2. Its last assignment isn't already an explicit intent
     * 3. There are no calls to explicit set methods defined by [isExplicitIntentSetMethod] and
     *    escape methods between the last assignment and the expression's use in the PendingIntent
     *    method
     * 4. If it's an array, then at least one of its elements is implicit
     *
     * Conditions 3 and 4 are checked by an [IntentSimpleNameAnalyzer].
     *
     * Below we consider 4 example scenarios where the above conditions are violated. Assume this
     * class:
     * ```
     * public class TestClass {
     *   Intent mIntent = new Intent();
     *   public void foo(Intent intentArg) {
     *     Intent intentOne = new Intent(); // implicit
     *     Intent intentTwo = new Intent(context, SomeClass.class); // explicit
     *     Intent[] intentArray = new Intent[2];
     *     intentArray[0] = intentTwo;
     *     intentArray[1] = intentTwo;
     *   }
     * }
     * ```
     * 1. If we use `mIntent` or `intentArg` inside `foo()` then condition 1 for `mIntent` and
     *    `intentArg` is not satisfied because these variables are defined outside of `foo()`
     * 2. If we assign `intentOne` to `intentTwo`, then `intentOne` is no longer implicit, so
     *    condition 2 for `intentOne` is not satisfied
     * 3. If we call `intentOne.setPackage("android.package");` before using it in the
     *    PendingIntent, then condition 3 for `intentOne` is not satisfied because
     *    `setPackage("android.package")` is an explicit set method
     * 4. If we use `intentArray`, then condition 4 is not satisfied because all its elements are
     *    explicit
     *
     * TODO(b/286042003): support cases when explicit intents are changed to implicit
     */
    private fun isIntentSimpleNameImplicit(
      intent: USimpleNameReferenceExpression,
      intentInfo: IntentExpressionInfo
    ): Boolean {
      val (assign, assignParentUMethod) =
        findLastAssignmentAndItsParentUMethod(intent, intentInfo.call) ?: return false
      val intentParentUMethod = intent.getParentOfType(UMethod::class.java) ?: return false
      if (assignParentUMethod != intentParentUMethod) return false // only consider local assigns
      var intentArraySize = 0
      if (assign is UCallExpression) {
        if (assign.isNewArrayWithDimensions()) {
          if (assign.valueArguments.size > 1) return false // only consider 1d arrays
          intentArraySize = assign.valueArguments[0].evaluate() as? Int ?: 0
        } else if (!isIntentCallImplicit(assign, intentInfo)) {
          return false
        }
      } else if (!isIntentExpressionImplicit(assign, intentInfo)) {
        return false
      }
      val analyzer = IntentSimpleNameAnalyzer(assign, intent, intentInfo, intentArraySize)
      intentParentUMethod.accept(analyzer)
      return analyzer.isIntentImplicitAfterGivenAssignment()
    }

    private fun isExplicitIntentSetMethod(call: UCallExpression?): Boolean =
      INTENT_EXPLICIT_SET_METHOD_NAMES.contains(call?.methodName)

    private fun findLastAssignmentAndItsParentUMethod(
      intent: USimpleNameReferenceExpression,
      call: UCallExpression
    ): Pair<UExpression, UMethod?>? {
      val assign =
        findLastAssignment(intent.resolve() as? PsiVariable ?: return null, call)
          ?.skipParenthesizedExprDown()
          ?: return null
      return Pair(assign, assign.getParentOfType(UMethod::class.java))
    }

    private fun isEscapeCall(call: UCallExpression?, intentInfo: IntentExpressionInfo): Boolean =
      !intentInfo.javaEvaluator.isMemberInClass(call?.resolve(), CLASS_INTENT)

    /**
     * Helper class for analyzing the data flow of an intent variable to determine if it's implicit.
     *
     * Conditions for an intent variable to be implicit:
     * - If an intent variable is a single Intent, then it's implicit if after the last assignment
     *   but before its use, none of the explicit set methods nor escape calls were called on the
     *   variable.
     * - If an intent variable is an array of Intents of a given [intentArraySize], then it's
     *   implicit if any of its elements are implicit. The default value for unassigned indices is
     *   false, meaning the array won't be flagged if some values aren't assigned.
     *
     * How to use the analyzer:
     * 1. Start the analysis by calling the analyzer on a method where the intent variable is
     *    assigned and used.
     * 2. Retrieve the final result by calling the [isIntentImplicitAfterGivenAssignment] method.
     *
     * How the analyzer works:
     * - First, it needs to reach the last assignment of the intent variable and set
     *   `isAssignReached` to true.
     * - After the last assignment is reached, the analyzer checks that the intent is not made
     *   explicit via explicit set methods. If it is, then `isIntentImplicit` is set to true.
     * - If the intent reference calls an external method or is used by an external method, then at
     *   that point the analyzer can't consider the intent implicit, so `isEscaped` is set to true.
     * - The analysis is finished when it reaches `intentFinalReference`.
     */
    private data class IntentSimpleNameAnalyzer(
      val lastAssignment: UExpression,
      val intentFinalReference: USimpleNameReferenceExpression,
      val intentInfo: IntentExpressionInfo,
      val intentArraySize: Int
    ) : DataFlowAnalyzer(setOf(intentFinalReference)) {
      private val intentArrayValuesIsImplicit: BooleanArray = BooleanArray(intentArraySize)
      private var isIntentImplicit: Boolean = true
      private var isIntentLocationReached: Boolean = false
      private var isAssignReached: Boolean = false
      private var isEscaped: Boolean = false

      override fun afterVisitExpression(node: UExpression) {
        if (node == lastAssignment) isAssignReached = true
        if (node == intentInfo.intentArgument) isIntentLocationReached = true
        if (!isIntentLocationReached) super.afterVisitExpression(node)
      }

      /**
       * - Marks if the analyzer reached the location where the intent was used originally, i.e.
       *   PendingIntent method
       * - If the intent location wasn't reached yet, then adds the expression to the tracking list
       */
      override fun visitSimpleNameReferenceExpression(
        node: USimpleNameReferenceExpression
      ): Boolean {
        if (isIntentAnalyzable() && node.identifier == intentFinalReference.identifier) track(node)
        return super.visitSimpleNameReferenceExpression(node)
      }

      /**
       * Considers assignments of array values and stores their isImplicit value inside
       * [intentArrayValuesIsImplicit]
       */
      override fun visitArrayAccessExpression(node: UArrayAccessExpression): Boolean {
        if (isIntentAnalyzable() && isMemberOfIntentArray(node)) {
          val parent = node.uastParent
          if (parent is UBinaryExpression && parent.operator == ASSIGN) {
            val isRightOperandImplicit = isIntentExpressionImplicit(parent.rightOperand, intentInfo)
            val index = node.indices.firstOrNull()?.evaluate() as? Int ?: 0
            if (index in 0 until intentArraySize) {
              intentArrayValuesIsImplicit[index] = isRightOperandImplicit
            }
          }
        }
        return super.visitArrayAccessExpression(node)
      }

      override fun receiver(call: UCallExpression) {
        if (!isIntentAnalyzable()) return
        if (isExplicitIntentSetMethod(call)) {
          isIntentImplicit = false
        } else if (isEscapeCall(call, intentInfo)) {
          isEscaped = true
        }
      }

      override fun argument(call: UCallExpression, reference: UElement) {
        if (
          call == intentInfo.intentArgument.skipParenthesizedExprDown() ||
            reference == intentInfo.intentArgument ||
            call.isNewArrayWithInitializer() ||
            isPendingIntentGetMethod(call)
        )
          return
        isEscaped = true
      }

      fun isIntentImplicitAfterGivenAssignment(): Boolean {
        if (isEscaped) return false
        if (intentArraySize != 0) return intentArrayValuesIsImplicit.any { it }
        return isIntentImplicit
      }

      private fun isPendingIntentGetMethod(call: UCallExpression): Boolean {
        return intentInfo.javaEvaluator.isMemberInClass(call.resolve(), PendingIntentUtils.CLASS) &&
          PendingIntentUtils.GET_METHOD_NAMES.contains(call.methodName)
      }
      private fun isIntentAnalyzable(): Boolean =
        isAssignReached && !isEscaped && !isIntentLocationReached

      private fun isMemberOfIntentArray(node: UArrayAccessExpression): Boolean =
        (node.receiver as? USimpleNameReferenceExpression)?.identifier ==
          intentFinalReference.identifier
    }
  }
}
