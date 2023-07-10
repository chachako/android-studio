/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.acceptSourceFile
import com.android.tools.lint.detector.api.getMethodName
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Gradle visitor for Kotlin Script files. */
class UastGradleVisitor(override val javaContext: JavaContext) : GradleVisitor() {
  override fun visitBuildScript(context: GradleContext, detectors: List<GradleScanner>) {
    val uastFile = javaContext.uastFile ?: return
    uastFile.acceptSourceFile(
      object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          handleMethodCall(node, detectors, context)
          return super.visitCallExpression(node)
        }

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
          handleBinaryExpression(node, detectors, context)
          return super.visitBinaryExpression(node)
        }
      }
    )
  }

  private fun handleBinaryExpression(
    node: UBinaryExpression,
    detectors: List<GradleScanner>,
    context: GradleContext
  ) {
    if (node.isAssignment()) {
      val hierarchy = getPropertyHierarchy(node.leftOperand)
      val target = hierarchy.firstOrNull() ?: return
      val hierarchyWithParents = hierarchy + getParent(node) + getParentN(node, 2)
      val parentName = hierarchyWithParents[1] ?: ""
      val parentParentName = hierarchyWithParents[2]
      val value = node.rightOperand.getSource()
      for (scanner in detectors) {
        scanner.checkDslPropertyAssignment(
          context,
          target,
          value,
          parentName,
          parentParentName,
          node.leftOperand,
          node.rightOperand,
          node
        )
      }
    } else {
      val left = node.leftOperand
      if (left is UCallExpression) {
        // Constructs like
        //    plugins {
        //        id("com.android.application") version "2.3.3"
        // (corresponds to Gradle: apply plugin: 'com.android.application'
        // and classpath 'com.android.tools.build:gradle:2.3.3')
        //
        // with UAST
        //
        //    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
        //        UIdentifier (Identifier (plugins))
        //        USimpleNameReferenceExpression (identifier = <anonymous class>)
        //        ULambdaExpression
        //            UBlockExpression
        //   ===>         UBinaryExpression (operator = <other>)
        //                    UCallExpression (kind = UastCallKind(name='method_call'), argCount =
        // 1))
        //                        UIdentifier (Identifier (id))
        //                        USimpleNameReferenceExpression (identifier = <anonymous class>)
        //                        ULiteralExpression (value = "com.android.application")
        //                    ULiteralExpression (value = "2.3.3")

        val valueArguments = left.valueArguments
        if (valueArguments.size == 1) {
          val target = getMethodName(left) ?: ""
          val arg = valueArguments[0]
          if (arg !is ULambdaExpression) {
            // Some sort of DSL property?
            // Parent should be block, its parent lambda, its parent a call -
            // the name is the parent
            val parentName = getParent(node) ?: ""
            val parentParentName = getParentN(node, 2)
            val value = arg.getSource()
            for (scanner in detectors) {
              // How do I represent this: we're passing
              // in *two* values:
              //  the plugin id
              // and then the version to use
              // For now just passing the first one, e.g.
              // in the above, plugins.id = "com.android.application"

              scanner.checkDslPropertyAssignment(
                context,
                target,
                value,
                parentName,
                parentParentName,
                left,
                arg,
                node
              )
            }
          }
        }
      }
    }
  }

  private fun handleMethodCall(
    node: UCallExpression,
    detectors: List<GradleScanner>,
    context: GradleContext
  ) {
    val valueArguments = node.valueArguments
    val propertyName = getMethodName(node)
    if (propertyName == null) {
      return
    } else {
      val parentName = getParent(node)
      val parentParentName = getParentN(node, 2)
      val unnamedArguments = valueArguments.map { it.getSource() }
      for (scanner in detectors) {
        scanner.checkMethodCall(
          context,
          propertyName,
          parentName,
          parentParentName,
          mapOf(),
          unnamedArguments,
          node
        )
      }
      if (valueArguments.size == 1 && valueArguments[0] !is ULambdaExpression) {
        // Some sort of DSL property?
        // Parent should be block, its parent lambda, its parent a call -
        // the name is the parent
        val value = unnamedArguments[0]
        for (scanner in detectors) {
          scanner.checkDslPropertyAssignment(
            context,
            propertyName,
            value,
            parentName ?: "",
            parentParentName,
            node.methodIdentifier ?: node,
            valueArguments[0],
            node
          )
        }
      }
    }
  }

  /**
   * Returns the source string for this [UExpression].
   *
   * This is used because [UExpression.asSourceString] doesn't do what it might sound like it does:
   * return the actual source; instead, it runs something like a source printer on the UAST
   * elements; this means for example that the whitespace will be standard instead of what is
   * actually in the source code, and for some constructs, there's a big change (for example,
   * properties will look like Java getters and setters). Instead, we can get the real source code
   * from the [UElement.sourcePsi] property, and from there the true source code via
   * [PsiElement.getText]. We only fall back to [UExpression.asSourceString] for elements missing a
   * source element (e.g. virtual elements).
   */
  private fun UExpression.getSource(): String {
    val sourcePsi = sourcePsi

    if (sourcePsi != null) {
      // Note also that for strings, the GradleVisitor contract expects
      // to get the string literals *with* surrounding quotes; this isn't
      // included in the bounds for string literals in the AST (it *is*
      // from [asSourceString()], so correct for that here.
      if (sourcePsi is KtLiteralStringTemplateEntry) {
        return sourcePsi.parent?.text ?: sourcePsi.text
      }
      return sourcePsi.text
    }

    return asSourceString()
  }

  private fun getPropertyHierarchy(expression: UExpression): List<String> {
    return when (expression) {
      is UQualifiedReferenceExpression -> {
        val result = mutableListOf(expression.selector.getSource())
        var receiver = expression.receiver
        while (receiver is UQualifiedReferenceExpression) {
          result.add(receiver.selector.getSource())
          receiver = receiver.receiver
        }
        result.add(receiver.getSource())
        result
      }
      else -> listOf(expression.getSource())
    }
  }

  private fun getSurroundingNamedBlock(node: UElement): UCallExpression? {
    var parent = node.uastParent
    if (parent is UReturnExpression) {
      // parent may be a UReturnExpression child of UBlockExpression
      parent = parent.uastParent
    }
    if (parent is UBlockExpression) {
      val parentParent = parent.uastParent
      if (parentParent is ULambdaExpression) {
        val parentCall = parentParent.uastParent
        if (parentCall is UCallExpression) {
          return parentCall
        }
      }
    }

    return null
  }

  private fun getParentN(node: UElement, n: Int): String? {
    val parentCall = getSurroundingNamedBlock(node)
    return when {
      parentCall == null -> null
      n == 1 -> getMethodName(parentCall)
      else -> getParentN(parentCall, n - 1)
    }
  }

  private fun getParent(node: UElement) = getParentN(node, 1)

  override fun createLocation(context: GradleContext, cookie: Any): Location {
    return if (cookie is UElement) {
      javaContext.getLocation(cookie)
    } else if (cookie is PsiElement) {
      javaContext.getLocation(cookie)
    } else {
      error("Unexpected location node ${cookie.javaClass}")
    }
  }

  override fun getStartOffset(context: GradleContext, cookie: Any): Int {
    val start = javaContext.getLocation(cookie as UElement).start
    return start?.offset ?: -1
  }
}
