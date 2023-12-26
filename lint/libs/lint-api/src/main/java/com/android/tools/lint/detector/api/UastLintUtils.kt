/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.lint.detector.api

import com.android.SdkConstants.ATTR_VALUE
import com.android.tools.lint.client.api.AndroidPlatformAnnotations
import com.android.tools.lint.client.api.AndroidPlatformAnnotations.Companion.fromPlatformAnnotation
import com.android.tools.lint.client.api.ResourceReference
import com.android.tools.lint.detector.api.ConstantEvaluatorImpl.LastAssignmentFinder.LastAssignmentValueUnknown
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.asJava.elements.FakeFileForLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMember
import org.jetbrains.kotlin.asJava.elements.KtLightParameter
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.getParameterForArgument
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedName
import org.jetbrains.uast.getUCallExpression
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.kotlin.kinds.KotlinSpecialExpressionKinds
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.UastVisitor

class UastLintUtils {
  companion object {
    @JvmStatic
    fun UElement.tryResolveUDeclaration(): UDeclaration? {
      return (this as? UResolvable)?.resolve().toUElementOfType()
    }

    /** Returns the containing file for the given element. */
    @JvmStatic
    fun getContainingFile(context: JavaContext, element: PsiElement?): PsiFile? {
      if (element == null) {
        return null
      }

      val containingFile = element.containingFile
      return if (containingFile != context.psiFile) {
        getContainingFile(element)
      } else containingFile
    }

    /** Returns the containing file for the given element. */
    @JvmStatic
    fun getPsiFile(file: UFile?): PsiFile? {
      return file?.let { getContainingFile(it.psi) }
    }

    /** Returns the containing file for the given element. */
    @JvmStatic
    fun getContainingFile(element: PsiElement?): PsiFile? {
      if (element == null) {
        return null
      }

      val containingFile = element as? PsiFile ?: element.containingFile ?: return null

      // In Kotlin files identifiers are sometimes using LightElements that are hosted in
      // a placeholder file, these do not have the right PsiFile as containing elements
      if (containingFile is FakeFileForLightClass) {
        return containingFile.ktFile
      }
      return containingFile
    }

    @JvmStatic
    fun getQualifiedName(element: PsiElement): String? =
      when (element) {
        is PsiClass -> element.qualifiedName
        is PsiMethod ->
          element.containingClass?.let { getQualifiedName(it) }?.let { "$it.${element.name}" }
        is PsiField ->
          element.containingClass?.let { getQualifiedName(it) }?.let { "$it.${element.name}" }
        else -> null
      }

    @JvmStatic
    fun getClassName(type: PsiClassType): String {
      val psiClass = type.resolve()
      return psiClass?.let { getClassName(it) } ?: type.className
    }

    @JvmStatic
    fun getClassName(psiClass: PsiClass): String {
      val stringBuilder = StringBuilder()
      stringBuilder.append(psiClass.name)

      var currPsiClass = psiClass.containingClass
      while (currPsiClass != null) {
        stringBuilder.insert(0, currPsiClass.name + ".")
        currPsiClass = currPsiClass.containingClass
      }
      return stringBuilder.toString()
    }

    @JvmStatic
    fun findLastAssignment(variable: PsiVariable, endAt: UElement): UExpression? {
      var currVariable = variable
      var lastAssignment: UElement? = null

      if (currVariable is UVariable) {
        currVariable = currVariable.psi
      }

      if (
        !currVariable.hasModifierProperty(PsiModifier.FINAL) &&
          (currVariable is PsiLocalVariable || currVariable is PsiParameter)
      ) {
        val containingFunction = endAt.getContainingUMethod()
        if (containingFunction != null) {
          val finder = ConstantEvaluatorImpl.LastAssignmentFinder(currVariable, endAt, null, -1)
          containingFunction.accept(finder)
          lastAssignment = finder.lastAssignment
        }
      } else {
        lastAssignment = UastFacade.getInitializerBody(currVariable)
      }

      return if (lastAssignment is UExpression) lastAssignment else null
    }

    /**
     * Finds the first argument of a method that matches the given parameter type.
     *
     * @param node the call expression.
     * @param method the method this call expression resolves to. It is expected the call expression
     *   and the method match. Otherwise, the result will be wrong.
     * @param type: the type of the parameter to be found.
     * @return The FIRST expression representing the argument used in the call expression for the
     *   specific parameter.
     */
    @JvmStatic
    fun findArgument(
      node: UCallExpression,
      method: PsiMethod,
      type: String,
    ): UExpression? {
      val psiParameter =
        method.parameterList.parameters.firstOrNull { it.type.canonicalText == type } ?: return null
      val argument = node.getArgumentForParameter(psiParameter.parameterIndex())
      return argument?.skipParenthesizedExprDown()
    }

    @JvmStatic
    fun findArgument(node: UCallExpression, type: String): UExpression? {
      return findArgument(node, node.resolve() ?: return null, type)
    }

    /**
     * Finds the initialization method (factory method or constructor) of a variable whose type is a
     * specific class.
     *
     * @param fullQualifiedClassName fully qualified class name to be searched for.
     * @param origExpression the variable or the initialization method itself.
     * @param endAt where the search ends
     * @param includeSubClass if true, include constructor of a subclass of the specified class.
     */
    fun findConstruction(
      fullQualifiedClassName: String,
      origExpression: UExpression,
      endAt: UElement,
      includeSubClass: Boolean = false
    ): UCallExpression? {
      val expression = origExpression.skipParenthesizedExprDown()

      val call = expression.getUCallExpression(1)
      if (call != null) {
        // handle the case of a default constructor, in which case the call cannot be resolved if
        // not defined.
        val classRef = call.classReference
        if (
          classRef != null &&
            (call.classReference?.getQualifiedName() == fullQualifiedClassName ||
              includeSubClass &&
                InheritanceUtil.isInheritor(
                  classRef.resolve() as? PsiClass,
                  true,
                  fullQualifiedClassName
                ))
        ) {
          return call
        } else if (expression is UQualifiedReferenceExpression) {
          return if (isReturningContext(call)) {
            // eg. filter.apply { addAction("abc") } --> use filter variable.
            findConstruction(fullQualifiedClassName, expression.receiver, endAt, includeSubClass)
          } else { // eg. IntentFilter.create("abc") --> use create("abc") UCallExpression.
            findConstruction(fullQualifiedClassName, call, endAt, includeSubClass)
          }
        }
      }

      return when (val resolved = expression.tryResolve()) {
        is PsiVariable -> {
          val assignment = findLastAssignment(resolved, endAt) ?: return null
          findConstruction(fullQualifiedClassName, assignment, endAt, includeSubClass)
        }
        is PsiMethod -> {
          if (isFactoryMethodForClass(fullQualifiedClassName, resolved, includeSubClass)) {
            expression as? UCallExpression
          } else {
            null
          }
        }
        else -> null
      }
    }

    private fun isFactoryMethodForClass(
      fullQualifiedClassName: String,
      method: PsiMethod,
      includeSubClass: Boolean = false
    ): Boolean {
      return (method.returnType?.canonicalText == fullQualifiedClassName || method.isConstructor) &&
        (isMemberInClass(method, fullQualifiedClassName) ||
          includeSubClass && isMemberInSubClassOf(method, fullQualifiedClassName))
    }

    fun isMemberInSubClassOf(
      member: PsiMember,
      className: String,
      strict: Boolean = false
    ): Boolean {
      val containingClass = member.containingClass
      return containingClass != null &&
        InheritanceUtil.isInheritor(containingClass, strict, className)
    }

    fun isMemberInClass(member: PsiMember?, className: String): Boolean {
      if (member == null) {
        return false
      }
      val containingClass = member.containingClass?.qualifiedName
      return className == containingClass
    }

    @JvmStatic
    fun getReferenceName(expression: UReferenceExpression): String? {
      if (expression is USimpleNameReferenceExpression) {
        return expression.identifier
      } else if (expression is UQualifiedReferenceExpression) {
        val selector = expression.selector
        if (selector is USimpleNameReferenceExpression) {
          return selector.identifier
        }
      }

      return null
    }

    @JvmStatic
    fun findLastValue(variable: PsiVariable, call: UElement, evaluator: ConstantEvaluator): Any? {
      var value: Any? = null

      if (
        !variable.hasModifierProperty(PsiModifier.FINAL) &&
          (variable is PsiLocalVariable || variable is PsiParameter)
      ) {
        val containingFunction = call.getContainingUMethod()
        if (containingFunction != null) {
          val body = containingFunction.uastBody
          if (body != null) {
            val finder = ConstantEvaluatorImpl.LastAssignmentFinder(variable, call, evaluator, 1)
            body.accept(finder)
            value = finder.currentValue

            if (value == null && finder.lastAssignment != null) {
              // Special return value: variable was assigned, but we don't know
              // the value
              return LastAssignmentValueUnknown
            }
          }
        }
      } else {
        val initializer = UastFacade.getInitializerBody(variable)
        if (initializer != null) {
          value = initializer.evaluate()
        }
      }

      return value
    }

    @JvmStatic
    fun toAndroidReferenceViaResolve(element: UElement): ResourceReference? {
      return ResourceReference.get(element)
    }

    @JvmStatic
    fun areIdentifiersEqual(first: UExpression, second: UExpression): Boolean {
      val firstIdentifier = getIdentifier(first)
      val secondIdentifier = getIdentifier(second)
      return (firstIdentifier != null &&
        secondIdentifier != null &&
        firstIdentifier == secondIdentifier)
    }

    @JvmStatic
    fun getIdentifier(expression: UExpression): String? =
      when (expression) {
        is ULiteralExpression -> expression.asRenderString()
        is UQualifiedReferenceExpression -> {
          val receiverIdentifier = getIdentifier(expression.receiver)
          val selectorIdentifier = getIdentifier(expression.selector)
          if (receiverIdentifier == null || selectorIdentifier == null) {
            null
          } else "$receiverIdentifier.$selectorIdentifier"
        }
        else -> null
      }

    @JvmStatic
    fun isNumber(argument: UElement): Boolean =
      when (argument) {
        is ULiteralExpression -> argument.value is Number
        is UPrefixExpression -> isNumber(argument.operand)
        else -> false
      }

    @JvmStatic
    fun isZero(argument: UElement): Boolean {
      if (argument is ULiteralExpression) {
        val value = argument.value
        return value is Number && value.toInt() == 0
      }
      return false
    }

    @JvmStatic
    fun isMinusOne(argument: UElement): Boolean {
      return if (argument is UUnaryExpression) {
        val operand = argument.operand
        if (operand is ULiteralExpression && argument.operator === UastPrefixOperator.UNARY_MINUS) {
          val value = operand.value
          value is Number && value.toInt() == 1
        } else {
          false
        }
      } else {
        false
      }
    }

    @JvmStatic
    fun getAnnotationValue(annotation: UAnnotation): UExpression? {
      return annotation.findDeclaredAttributeValue(ATTR_VALUE)
    }

    @JvmStatic
    fun getLongAttribute(
      context: JavaContext,
      annotation: UAnnotation,
      name: String,
      defaultValue: Long
    ): Long {
      return getAnnotationLongValue(annotation, name, defaultValue)
    }

    @JvmStatic
    fun getDoubleAttribute(
      context: JavaContext,
      annotation: UAnnotation,
      name: String,
      defaultValue: Double
    ): Double {
      return getAnnotationDoubleValue(annotation, name, defaultValue)
    }

    @JvmStatic
    fun getBoolean(
      context: JavaContext,
      annotation: UAnnotation,
      name: String,
      defaultValue: Boolean
    ): Boolean {
      return getAnnotationBooleanValue(annotation, name, defaultValue)
    }

    @JvmStatic
    fun getAnnotationBooleanValue(annotation: UAnnotation?, name: String): Boolean? {
      return AnnotationValuesExtractor.getAnnotationValuesExtractor(annotation)
        .getAnnotationBooleanValue(annotation, name)
    }

    @JvmStatic
    fun getAnnotationBooleanValue(
      annotation: UAnnotation?,
      name: String,
      defaultValue: Boolean
    ): Boolean {
      val value = getAnnotationBooleanValue(annotation, name)
      return value ?: defaultValue
    }

    @JvmStatic
    fun getAnnotationLongValue(annotation: UAnnotation?, name: String): Long? {
      return AnnotationValuesExtractor.getAnnotationValuesExtractor(annotation)
        .getAnnotationLongValue(annotation, name)
    }

    @JvmStatic
    fun getAnnotationLongValue(annotation: UAnnotation?, name: String, defaultValue: Long): Long {
      val value = getAnnotationLongValue(annotation, name)
      return value ?: defaultValue
    }

    @JvmStatic
    fun getAnnotationDoubleValue(annotation: UAnnotation?, name: String): Double? {
      return AnnotationValuesExtractor.getAnnotationValuesExtractor(annotation)
        .getAnnotationDoubleValue(annotation, name)
    }

    @JvmStatic
    fun getAnnotationDoubleValue(
      annotation: UAnnotation?,
      name: String,
      defaultValue: Double
    ): Double {
      val value = getAnnotationDoubleValue(annotation, name)
      return value ?: defaultValue
    }

    @JvmStatic
    fun getAnnotationStringValue(annotation: UAnnotation?, name: String): String? {
      return AnnotationValuesExtractor.getAnnotationValuesExtractor(annotation)
        .getAnnotationStringValue(annotation, name)
    }

    @JvmStatic
    fun getAnnotationStringValues(annotation: UAnnotation?, name: String): Array<String>? {
      return AnnotationValuesExtractor.getAnnotationValuesExtractor(annotation)
        .getAnnotationStringValues(annotation, name)
    }

    @JvmStatic
    fun containsAnnotation(list: List<UAnnotation>, annotation: UAnnotation): Boolean =
      list.stream().anyMatch { e -> e === annotation }

    @JvmStatic
    fun containsAnnotation(list: List<UAnnotation>, qualifiedName: String): Boolean =
      list.stream().anyMatch { e -> e.qualifiedName == qualifiedName }

    /**
     * Returns any default-use site annotations for this owner, **even though** in Kotlin these
     * annotations may not belong on this element. This is done because Kotlin's annotations
     * semantics means that if you don't specify a use site on a property for example, the
     * annotation *only* applies to the private backing field, not the get method and not the set
     * method! However, if you added `@Suppress` on a property you probably expected it to apply to
     * the getter/setter as well.
     */
    @JvmStatic
    fun getDefaultUseSiteAnnotations(owner: PsiModifierListOwner): List<UAnnotation>? {
      if (owner is KtLightMember<*>) {
        val origin = owner.unwrapped
        if (origin is KtProperty) {
          return getDefaultUseSiteAnnotations(origin.annotationEntries)
        }
      } else if (owner is KtLightParameter) {
        val origin = owner.method.unwrapped as? KtDeclaration
        if (origin is KtProperty || origin is KtParameter) {
          return getDefaultUseSiteAnnotations(origin.annotationEntries)
        }
      }

      return null
    }

    @JvmStatic
    fun getDefaultUseSiteAnnotations(annotated: UAnnotated): List<UAnnotation>? {
      val entries =
        when (val origin = annotated.sourcePsi) {
          is KtParameter -> origin.annotationEntries
          is KtProperty -> origin.annotationEntries
          else -> return null
        }
      return getDefaultUseSiteAnnotations(entries)
    }

    private fun getDefaultUseSiteAnnotations(entries: List<KtAnnotationEntry>): List<UAnnotation>? {
      var annotations: MutableList<UAnnotation>? = null
      for (ktAnnotation in entries) {
        val site = ktAnnotation.useSiteTarget?.getAnnotationUseSiteTarget()
        if (site == null || site == AnnotationUseSiteTarget.PROPERTY) {
          val annotation =
            (UastFacade.convertElement(ktAnnotation, null) as? UAnnotation ?: continue).let {
              val signature = it.qualifiedName ?: ""
              if (AndroidPlatformAnnotations.isPlatformAnnotation(signature)) {
                it.fromPlatformAnnotation(signature)
              } else {
                it
              }
            }
          val list = annotations ?: mutableListOf<UAnnotation>().also { annotations = it }
          list.add(annotation)
        }
      }

      return annotations
    }
  }
}

/**
 * Returns true if the given call represents a Kotlin scope function where the object reference is
 * this. See https://kotlinlang.org/docs/scope-functions.html#function-selection
 */
fun isScopingThis(node: UCallExpression): Boolean {
  val name = getMethodName(node)
  if (name == "run" || name == "with" || name == "apply") {
    return isScopingFunction(node)
  }
  return false
}

/**
 * Returns true if the given call represents a Kotlin scope function where the object reference is
 * the lambda variable `it`; see https://kotlinlang.org/docs/scope-functions.html#function-selection
 */
fun isScopingIt(node: UCallExpression): Boolean {
  val name = getMethodName(node)
  if (name == "let" || name == "also" || name == "takeIf" || name == "takeUnless") {
    return isScopingFunction(node)
  }
  return false
}

/**
 * Returns true if the given call represents a Kotlin scope function where the return value is the
 * context object; see https://kotlinlang.org/docs/scope-functions.html#function-selection
 */
fun isReturningContext(node: UCallExpression): Boolean {
  val name = getMethodName(node)
  if (name == "apply" || name == "also" || name == "takeIf" || name == "takeUnless") {
    return isScopingFunction(node)
  }
  return false
}

/**
 * Returns true if the given node appears to be one of the scope functions. Only checks parent
 * class; caller should intend that it's actually one of let, with, apply, etc.
 */
private fun isScopingFunctionName(name: String?): Boolean {
  return when (name) {
    "let",
    "run",
    "with",
    "apply",
    "also",
    // Honorary scoping functions. See https://kotlinlang.org/docs/reference/scope-functions.html
    "takeIf",
    "takeUnless" -> true
    else -> false
  }
}

/** Returns true if the given node appears to be one of the scope functions. */
fun isScopingFunction(node: UCallExpression): Boolean {
  if (isScopingFunctionName(node.methodIdentifier?.name ?: node.methodName)) {
    val called =
      node.resolve()
        // if not found, assume true because in the IDE these builtins often resolve to null
        // and the name is clue enough
        ?: return true
    return isScopingFunction(called)
  } else {
    return false
  }
}

/** Returns true if the given node appears to be one of the scope functions. */
fun isScopingFunction(method: PsiMethod): Boolean {
  return if (isScopingFunctionName(method.name)) {
    // See libraries/stdlib/jvm/build/stdlib-declarations.json
    method.containingClass?.qualifiedName == "kotlin.StandardKt__StandardKt"
  } else {
    false
  }
}

/**
 * Returns true if the given call represents a Kotlin scope function where the return value is the
 * lambda result; see https://kotlinlang.org/docs/scope-functions.html#function-selection
 */
fun isReturningLambdaResult(node: UCallExpression): Boolean {
  val name = getMethodName(node)
  if (name == "let" || name == "run" || name == "with") {
    return isScopingFunction(node)
  }
  return false
}

fun PsiParameter.isReceiver(): Boolean {
  // It's tempting to just check
  //    this is KtUltraLightReceiverParameter
  // here, but besides that being an internal class, that approach
  // would only work for source references; for compiled Kotlin
  // code (including the runtime library) these will just be
  // ClsParameterImpl, so we have to resort to name patterns anyway.

  // Fully qualified names here because we can't suppress usage in import list
  val name = name
  return name.startsWith("\$this") || name.startsWith("\$self")
}

/** For a qualified or parenthesized expression, returns the selector, or otherwise returns self. */
fun UElement.findSelector(): UElement {
  var curr = this
  while (true) {
    curr =
      when (curr) {
        is UQualifiedReferenceExpression -> curr.selector
        is UParenthesizedExpression -> curr.expression
        else -> break
      }
  }
  return curr
}

/** For an element in an expression, returns the next expression in the same block if any. */
fun UElement.nextStatement(): UExpression? {
  var prev = this
  var curr = prev.uastParent
  while (curr != null) {
    if (curr is UBlockExpression) {
      val expressions = curr.expressions
      val index = expressions.indexOf(prev)
      return if (index != -1 && index < expressions.size - 1) {
        expressions[index + 1]
      } else {
        null
      }
    }
    prev = curr
    curr = curr.uastParent
  }

  return null
}

/**
 * UAST adds an implicit lambda return no matter what, so we may need to unwrap that.
 *
 * Before KTIJ-26541, lambda's last expression was wrapped with an implicit return only if it is
 * used as lambda's return while lambda's return type is Unit or Nothing. To avoid an expensive
 * type/resolution involved in parent retrieval, such implicit lambda return expression is added
 * unconditionally.
 *
 * That is, you will see something like: ULambdaExpression [{...}] : PsiType:Function0<? extends
 * Unit> UBlockExpression [{...}] UReturnExpression [return ...] // may not be correct _real last
 * expression_
 */
fun UElement.isIncorrectImplicitReturnInLambda(): Boolean {
  if (this !is UReturnExpression || this.sourcePsi != null) return false
  if (uastParent !is UBlockExpression) return false
  val block = uastParent as UBlockExpression
  if (block.expressions.lastOrNull() != this) return false
  if (block.uastParent !is ULambdaExpression) return false
  val lambda = block.uastParent as ULambdaExpression
  val lambdaReturnType =
    lambda
      .getReturnType()
      .let { returnType -> if (returnType is PsiWildcardType) returnType.bound else returnType }
      ?.canonicalText ?: return false
  // Only non-Unit returning lambda should have an implicit return at the end.
  return lambdaReturnType.endsWith("Unit") || lambdaReturnType.endsWith("Nothing")
}

// Copied from `...codeInspection.analysisUastUtil`
private fun ULambdaExpression.getReturnType(): PsiType? {
  val lambdaType = getLambdaType()
  return LambdaUtil.getFunctionalInterfaceReturnType(lambdaType)
}

// Copied from `...codeInspection.analysisUastUtil`
private fun ULambdaExpression.getLambdaType(): PsiType? =
  functionalInterfaceType
    ?: getExpressionType()
    ?: uastParent?.let {
      when (it) {
        is UVariable -> it.type // in Kotlin local functions looks like lambda stored in variable
        is UCallExpression -> it.getParameterForArgument(this)?.type
        else -> null
      }
    }

/**
 * Returns the current statement. If you for example have `foo.bar.baz();` and you invoke this on
 * `bar`, it will return the top level UQualifiedReferenceExpression.
 */
fun UElement.statement(): UExpression? {
  var prev = this.getParentOfType<UExpression>(false) ?: return null
  var curr = prev.uastParent
  while (curr != null) {
    if (curr is UBlockExpression || curr !is UExpression) {
      return if (prev is UParenthesizedExpression) {
        prev.expression
      } else {
        prev
      }
    }
    prev = curr
    curr = curr.uastParent
  }

  return prev
}

/** For an element in an expression, returns the previous expression in the same block if any. */
fun UElement.previousStatement(): UExpression? {
  var prev = this
  var curr = prev.uastParent
  while (curr != null) {
    if (curr is UBlockExpression) {
      val expressions = curr.expressions
      val index = expressions.indexOf(prev)
      return if (index > 0) {
        expressions[index - 1]
      } else {
        null
      }
    }
    prev = curr
    curr = curr.uastParent
  }

  return null
}

/**
 * Returns true if [this] element is a child or indirect child of the given [parent]. If [strict] is
 * false, this method will return true when [parent] is the same as [this].
 */
fun UElement.isBelow(parent: UElement, strict: Boolean = false): Boolean {
  var curr = if (strict) uastParent else this
  while (curr != null) {
    if (curr == parent) {
      return true
    }
    curr = curr.uastParent
  }
  return false
}

/**
 * Returns true if [this] element is a child or indirect child of the given [parent]. If [strict] is
 * false, this method will return true when [parent] is the same as [this].
 */
fun PsiElement?.isBelow(parent: PsiElement, strict: Boolean = false): Boolean {
  this ?: return false
  return PsiTreeUtil.isAncestor(parent, this, strict)
}

/**
 * Returns the class "containing" the given method. This is normally just `member.containingClass`,
 * but for extension functions and properties it's slightly more complicated (e.g. for `fun
 * String.test()` the containing class is `java.lang.String`).
 */
fun PsiMember.getReceiverOrContainingClass(): PsiClass? {
  return getReceiver() ?: containingClass
}

/**
 * Given a [PsiMethod] or [PsiField], if it's an extension method or an extension property, returns
 * the [PsiClass] for the extension. For example, for `fun String.test()` the containing class is
 * `java.lang.String`.
 */
fun PsiMember.getReceiver(): PsiClass? {
  val callable =
    when (val unwrapped = this.unwrapped) {
      is KtNamedFunction,
      is KtProperty -> unwrapped as KtCallableDeclaration
      is KtPropertyAccessor -> getNonStrictParentOfType<KtProperty>(unwrapped)
      else -> return null
    } ?: return null
  val typeReference = callable.receiverTypeReference?.toUElement() as? UTypeReferenceExpression
  return (typeReference?.type as? PsiClassType)?.resolve()
}

/** Returns true if this if-expression is a UAST-generated if modeling an elvis (?:) expression. */
fun UIfExpression.isElvisIf(): Boolean {
  val parent = skipParenthesizedExprUp(uastParent)
  @Suppress("UnstableApiUsage")
  return parent is UExpressionList && parent.kind == KotlinSpecialExpressionKinds.ELVIS
}

/** Returns true if this call represents a `this(...)` or a `super(...)` constructor call. */
fun UCallExpression.isThisOrSuperConstructorCall(): Boolean {
  return this.isSuperConstructorCall() || this.isThisConstructorCall()
}

/** Returns true if this call represents a `super(...)` constructor call */
fun UCallExpression.isSuperConstructorCall(): Boolean {
  // In theory, this should be as easy as checking for node or node.receiver being
  // a UInstanceExpression, but in many practical cases that's not the case;
  // we get a plain PsiMethodCallExpression with a null receiver etc.
  return when (sourcePsi) {
    is KtSuperTypeCallEntry -> true
    is PsiMethodCallExpression -> methodName == "super"
    else -> false
  }
}

/** Returns true if this call represents a `this(...)` constructor call */
fun UCallExpression.isThisConstructorCall(): Boolean {
  // In theory, this should be as easy as checking for node or node.receiver being
  // a UInstanceExpression, but in many practical cases that's not the case;
  // we get a plain PsiMethodCallExpression with a null receiver etc.
  return when (sourcePsi) {
    is KtThisExpression -> true
    is PsiMethodCallExpression -> methodName == "this"
    else -> false
  }
}

/**
 * Like [UFile.accept], but in the case of multi-file classes (where multiple source files
 * containing top level declarations are annotated with `@JvmMultifileClass`, all naming the same
 * target class) the [UFile] will contain functions and properties from *different* files. Since
 * lint is visiting each source file, that means it would visit these methods multiple times, since
 * they're included in the single large [UFile] built up for each individual source file fragment.
 *
 * This method will visit a [UFile], but will limit itself to visiting just the parts corresponding
 * to the source file that the [UFile] was constructed from.
 */
fun UFile.acceptSourceFile(visitor: UastVisitor) {
  val sourcePsi = this.sourcePsi
  if (
    sourcePsi is KtFile &&
      sourcePsi.annotationEntries.any { it.shortName.toString() == "JvmMultifileClass" }
  ) {
    acceptMultiFileClass(visitor)
  } else {
    accept(visitor)
  }
}

/** Skips down across labeled expressions */
fun UExpression.skipLabeledExpression(): UExpression {
  var expression = this
  while (expression is ULabeledExpression) {
    expression = expression.expression
  }
  return expression
}

/**
 * Visits a multi-file class, limiting itself to just the parts from the same source file as the
 * root node.
 *
 * This method basically mirrors the implementation of [UFile.accept] and [UClass.accept], except
 * that it specifically looks for declarations that seem to have been merged in from a different
 * source file than the origin one at the top level, and it then skips those.
 */
private fun UFile.acceptMultiFileClass(visitor: UastVisitor) {
  val targetFile = sourcePsi.virtualFile
  if (visitor.visitFile(this)) return

  //noinspection ExternalAnnotations
  uAnnotations.acceptList(visitor)
  imports.acceptList(visitor)

  for (uClass in classes) {
    if (visitor.visitClass(uClass)) return
    //noinspection ExternalAnnotations
    uClass.uAnnotations.acceptList(visitor)
    for (declaration in uClass.uastDeclarations) {
      val declarationFile = declaration.sourcePsi?.containingFile?.virtualFile
      if (declarationFile == null || declarationFile == targetFile) {
        declaration.accept(visitor)
      }
    }
    visitor.afterVisitClass(uClass)
  }

  visitor.afterVisitFile(this)
}

/**
 * Does this expression have an unconditional return? This means that all possible branches contains
 * a return or exception throw or yield.
 */
fun UExpression.isUnconditionalReturn(): Boolean {
  val statement = this
  @Suppress("UnstableApiUsage") // UYieldExpression not yet stable
  if (statement is UBlockExpression) {
    statement.expressions.lastOrNull()?.let {
      return it.isUnconditionalReturn()
    }
  } else if (statement is UExpressionList) {
    statement.expressions.lastOrNull()?.let {
      return it.isUnconditionalReturn()
    }
  } else if (statement is UYieldExpression) {
    // (Kotlin when statements will sometimes be represented using yields in the UAST
    // representation)
    val yieldExpression = statement.expression
    if (yieldExpression != null) {
      return yieldExpression.isUnconditionalReturn()
    }
  } else if (statement is UParenthesizedExpression) {
    return statement.expression.isUnconditionalReturn()
  } else if (statement is UIfExpression) {
    val thenExpression = statement.thenExpression
    val elseExpression = statement.elseExpression
    if (thenExpression != null && elseExpression != null) {
      return thenExpression.isUnconditionalReturn() && elseExpression.isUnconditionalReturn()
    }
    return false
  } else if (statement is USwitchExpression) {
    for (case in statement.body.expressions) {
      if (case is USwitchClauseExpressionWithBody) {
        if (!case.body.isUnconditionalReturn()) {
          return false
        }
      }
    }
    return true
  }

  if (statement is UReturnExpression || statement is UThrowExpression) {
    return true
  } else if (statement is UCallExpression) {
    val methodName = getMethodName(statement)
    // Look for Kotlin runtime library methods that unconditionally exit
    if ("error" == methodName || "TODO" == methodName) {
      return true
    }
  }

  return false
}

/**
 * Finds the common ancestor of [element1] and [element2]. (Based on the equivalent
 * [PsiTreeUtil.findCommonParent] implementation.)
 */
fun findCommonParent(element1: UElement, element2: UElement): UElement? {
  if (element1 === element2) return element1
  var depth1 = getDepth(element1)
  var depth2 = getDepth(element2)

  var parent1: UElement? = element1
  var parent2: UElement? = element2
  while (depth1 > depth2) {
    parent1 = parent1?.uastParent
    depth1--
  }
  while (depth2 > depth1) {
    parent2 = parent2?.uastParent
    depth2--
  }
  while (parent1 != null && parent2 != null && parent1 != parent2) {
    parent1 = parent1.uastParent
    parent2 = parent2.uastParent
  }
  if (parent2 == null) {
    return null
  }
  return parent1
}

private fun getDepth(element: UElement): Int {
  var depth = 0
  var parent: UElement? = element
  while (parent != null) {
    depth++
    parent = parent.uastParent
  }
  return depth
}
