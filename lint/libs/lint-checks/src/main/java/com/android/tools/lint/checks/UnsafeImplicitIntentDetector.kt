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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_EXPORTED
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.CLASS_CONTEXT
import com.android.SdkConstants.CLASS_INTENT
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_RECEIVER
import com.android.SdkConstants.VALUE_FALSE
import com.android.SdkConstants.VALUE_TRUE
import com.android.tools.lint.client.api.TYPE_CLASS
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
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.iterator
import com.android.utils.subtag
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getParentOfType
import org.w3c.dom.Element

class UnsafeImplicitIntentDetector : Detector(), SourceCodeScanner, XmlScanner {

  override fun getApplicableConstructorTypes() = listOf(CLASS_INTENT)

  override fun getApplicableElements() =
    listOf(
      TAG_ACTIVITY,
      TAG_RECEIVER,
    )

  override fun visitElement(context: XmlContext, element: Element) {
    if (context.isGlobalAnalysis()) return
    val exported = element.getAttributeNodeNS(ANDROID_URI, ATTR_EXPORTED)
    if (exported == null || VALUE_FALSE == exported.value) {
      val packageName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
      val intentFilter = element.subtag(TAG_INTENT_FILTER) ?: return
      val action = intentFilter.subtag(TAG_ACTION)?.getAttributeNS(ANDROID_URI, ATTR_NAME)
      if (action != null) {
        val lintMap = context.getPartialResults(ISSUE).map()
        val actions = lintMap.getMap(ACTIONS_KEY) ?: LintMap()
        actions.put(action, action)
        lintMap.put(ACTIONS_KEY, actions)
        val packages = lintMap.getMap(PACKAGES_KEY) ?: LintMap()
        packages.put(action, packageName)
        lintMap.put(PACKAGES_KEY, packages)
      }
    }
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod
  ) {
    if (context.evaluator.parametersMatch(constructor, CLASS_CONTEXT, TYPE_CLASS)) return
    val action: String? =
      node.valueArguments.firstOrNull()?.let {
        ConstantEvaluator.evaluateString(context, it, false)
      }
    if (context.isGlobalAnalysis()) {
      val mainProject = context.mainProject
      val mergedManifest = mainProject.mergedManifest
      if (mergedManifest == null || mergedManifest.documentElement == null) return
      val actions = getElementsAndGetActions(mergedManifest.documentElement)
      val (isIntentImplicit, actionFromSetAction, location) = isIntentImplicit(node, context)
      val actualAction: String? = actionFromSetAction ?: action
      if (isIntentImplicit) {
        if (!actions.containsKey(actualAction)) return
        val message =
          """
                This intent matches an internal non-exported component.
                If you are trying to invoke the component matching the action `$actualAction`,
                then you should use an explicit intent.
                """
            .trimIndent()
        val actualLocation = location ?: context.getLocation(node)
        context.report(
          Incident(
            ISSUE,
            actualLocation,
            message,
            buildQuickFix(context, actualLocation, actions[actualAction]!!)
          )
        )
      }
    } else {
      val (isIntentImplicit, actionFromSetAction, location) = isIntentImplicit(node, context)
      val actualAction: String = actionFromSetAction ?: action ?: return
      if (isIntentImplicit) {
        val lintMap = context.getPartialResults(ISSUE).map()
        val intents = lintMap.getMap(INTENTS_KEY) ?: LintMap()
        intents.put(actualAction, actualAction)
        lintMap.put(INTENTS_KEY, intents)
        if (location != null) {
          val locations = lintMap.getMap(LOCATIONS_KEY) ?: LintMap()
          locations.put(actualAction, location)
          lintMap.put(LOCATIONS_KEY, locations)
        } else {
          val locations = lintMap.getMap(LOCATIONS_KEY) ?: LintMap()
          locations.put(actualAction, location ?: context.getLocation(node))
          lintMap.put(LOCATIONS_KEY, locations)
        }
      }
    }
  }

  fun getElementsAndGetActions(root: Element): Map<String, String> {
    val application = root.subtag(TAG_APPLICATION) ?: return emptyMap()
    val actions = HashMap<String, String>()
    for (component in application) {
      when (component.tagName) {
        TAG_ACTIVITY,
        TAG_RECEIVER -> {
          addActions(component, actions)
        }
      }
    }
    return actions
  }

  private fun addActions(element: Element, actions: MutableMap<String, String>) {
    val exported = element.getAttributeNodeNS(ANDROID_URI, ATTR_EXPORTED)
    val componentName = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME).value
    if (exported?.value != VALUE_TRUE) {
      val intentFilter = element.subtag(TAG_INTENT_FILTER) ?: return
      val action = intentFilter.subtag(TAG_ACTION)?.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
      actions.put(action, componentName)
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    val actions = partialResults.map().getMap(ACTIONS_KEY) ?: return
    val intents = partialResults.map().getMap(INTENTS_KEY) ?: return
    val locations = partialResults.map().getMap(LOCATIONS_KEY) ?: return
    val packages = partialResults.map().getMap(PACKAGES_KEY) ?: return
    for (intent in intents) {
      val intentAction = intents.get(intent)
      if (intentAction == null || !actions.containsKey(intentAction)) continue
      val message =
        """
            This intent matches an internal non-exported component.
            If you are trying to invoke the component matching the action `$intentAction`,
            then you should use an explicit intent.
            """
          .trimIndent()
      val location = locations.getLocation(intent)
      context.report(
        Incident(
          ISSUE,
          location!!,
          message,
          buildQuickFix(context, location, packages[intentAction]!!)
        )
      )
    }
  }

  private fun isIntentImplicit(
    node: UCallExpression,
    context: JavaContext
  ): Triple<Boolean, String?, Location?> {
    var isIntentImplicit = true
    var action: String? = null
    var location: Location? = null
    val visitor =
      object : DataFlowAnalyzer(setOf(node)) {
        override fun receiver(call: UCallExpression) {
          if (
            call.methodName == "setComponent" ||
              call.methodName == "setClass" ||
              call.methodName == "setPackage" ||
              call.methodName == "setClassName"
          ) {
            isIntentImplicit = false
          }
          if (call.methodName == "setAction") {
            action = call.valueArguments[0].evaluateString()
            location = context.getLocation(call)
          }
        }
      }
    val parent = node.getParentOfType(UMethod::class.java)
    parent?.accept(visitor)
    return Triple(isIntentImplicit, action, location)
  }

  private fun buildQuickFix(context: Context, location: Location, longClassName: String): LintFix {
    val (packageName, className) = getPackageNameAndClassName(context, longClassName)
    val explicitifyWithClassName = ".setClassName(\"$packageName\", \"$packageName.$className\")"
    val explicitifyWithPackage = ".setPackage(\"$packageName\")"

    val setPackage =
      LintFix.create()
        .name("Set package name")
        .replace()
        .reformat(true)
        .range(location)
        .end()
        .with(explicitifyWithPackage)
        .build()
    val setClassName =
      LintFix.create()
        .name("Set class name")
        .replace()
        .reformat(true)
        .range(location)
        .end()
        .with(explicitifyWithClassName)
        .build()
    return fix().alternatives(setPackage, setClassName)
  }

  private fun getPackageNameAndClassName(
    context: Context,
    longClassName: String
  ): Pair<String, String> {
    val lastDot = longClassName.lastIndexOf('.')
    val subpackage = longClassName.substring(0, lastDot)
    val className = longClassName.substring(lastDot + 1, longClassName.length)
    var packageName = ""
    packageName =
      if (lastDot <= 0) {
        context.project.`package` + ""
      } else {
        subpackage
      }
    return Pair(packageName, className)
  }

  companion object {
    private const val LOCATIONS_KEY = "locations"
    private const val INTENTS_KEY = "intents"
    private const val ACTIONS_KEY = "actions"
    private const val PACKAGES_KEY = "packages"

    private val IMPLEMENTATION =
      Implementation(
        UnsafeImplicitIntentDetector::class.java,
        EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
        Scope.JAVA_FILE_SCOPE
      )

    /** Issue describing the problem and pointing to the detector implementation. */
    @JvmField
    val ISSUE: Issue =
      Issue.create(
        id = "UnsafeImplicitIntentLaunch",
        briefDescription = "Implicit intent matches an internal non-exported component",
        explanation =
          """
                    This intent matches a non-exported component within the same app. \
                    In many cases, the app developer could instead use an explicit Intent \
                    to send messages to their internal components, ensuring that the messages \
                    are safely delivered without exposure to malicious apps on the device. \
                    Using such implicit intents will result in a crash in an upcoming version of Android.
                    """,
        category = Category.SECURITY,
        priority = 9,
        severity = Severity.ERROR,
        androidSpecific = true,
        enabledByDefault = false,
        implementation = IMPLEMENTATION
      )
  }
}
