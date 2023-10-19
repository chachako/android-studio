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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.getMethodName
import com.android.tools.lint.detector.api.getReceiverOrContainingClass
import com.android.tools.lint.detector.api.isBelow
import com.android.tools.lint.detector.api.isElvisIf
import com.android.tools.lint.detector.api.isJava
import com.android.tools.lint.detector.api.isReturningContext
import com.android.tools.lint.detector.api.isScopingIt
import com.android.tools.lint.detector.api.isScopingThis
import com.android.tools.lint.detector.api.skipLabeledExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UField
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedParentOrThis
import org.jetbrains.uast.kotlin.KotlinPostfixOperators
import org.jetbrains.uast.kotlin.kinds.KotlinSpecialExpressionKinds
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Helper class for analyzing data flow. To use it, initialize it with one or more AST elements that
 * you want to track, and then visit a method scope with this analyzer. It has a number of callback
 * methods you can override to find out when the value is returned, or used as an argument in a
 * call, or used as a receiver in a call, etc. See `lint/docs/api-guide/dataflow-analyzer.md.html`
 * for more.
 */
abstract class DataFlowAnalyzer(
  val initial: Collection<UElement>,
  initialReferences: Collection<PsiVariable> = emptyList()
) : AbstractUastVisitor() {

  /** The instance being tracked is the receiver for a method call. */
  open fun receiver(call: UCallExpression) {}

  /** A reference to a method on the tracked instance is being called */
  open fun methodReference(call: UCallableReferenceExpression) {}

  /** The instance being tracked is being returned from this block. */
  open fun returns(expression: UReturnExpression) {}

  /** The instance being tracked is being stored into a field. */
  open fun field(field: UElement) {}

  /** The instance being tracked is being stored into an array. */
  open fun array(array: UArrayAccessExpression) {}

  /**
   * The instance being tracked is being passed in a method call, where [call] is the method call
   * node, and the [reference] is the argument to the call which is passing the tracked instance.
   * (In some cases, it can also be a [UCallableReferenceExpression] where the method reference is
   * invoked in this call and the reference has captured one of the tracked instances.)
   */
  open fun argument(call: UCallExpression, reference: UElement) {}

  /** Whether there were one or more resolve failures */
  var failedResolve = false

  /**
   * We failed to resolve a reference; this means the code can be invalid or the environment
   * incorrect, and we should draw conclusions very carefully.
   */
  open fun failedResolve(reference: UElement) {
    // If it's a standalone unresolved call (whose value is not assigned, and which we're not making
    // further calls on),
    // ignore the failure.
    // E.g. if we have
    //    val cursor = createCursor()
    //    unresolved()
    //    cursor.close()
    // we don't need to give up on our analysis here since unresolved() is unlikely to affect the
    // result.
    var curr: UElement = skipParenthesizedExprUp(reference.uastParent) ?: return
    if (
      curr is UQualifiedReferenceExpression &&
        skipParenthesizedExprUp(curr.uastParent) is UQualifiedReferenceExpression
    ) {
      failedResolve = true
      return
    }
    while (curr !is UMethod) {
      if (curr is UDeclarationsExpression || curr is UBinaryExpression && curr.isAssignment()) {
        failedResolve = true
        return
      }
      curr = curr.uastParent ?: break
    }
  }

  /** Start tracking the given instance */
  protected open fun track(instance: UElement, source: UElement? = null): Boolean {
    return instances.add(instance)
  }

  /** Start tracking the given reference */
  protected open fun track(reference: PsiElement, source: UElement? = null): Boolean {
    return references.add(reference)
  }

  /**
   * If a tracked element is passed as an argument, [argument] will be invoked unless this method
   * returns true. This lets you exempt certain methods from being treated as an escape, such as
   * logging methods.
   */
  open fun ignoreArgument(call: UCallExpression, reference: UElement): Boolean {
    val name = call.methodName ?: call.methodIdentifier?.name ?: return false
    if (name == "print" || name == "println" || name == "log") {
      return true
    } else if (name.length == 1) {
      val receiver = call.receiver?.skipParenthesizedExprDown()
      if (receiver is USimpleNameReferenceExpression && receiver.identifier == "Log") {
        return true
      }
    }
    return false
  }

  /**
   * Tries to guess whether the given method call returns self. This is intended to be able to tell
   * that in a constructor call chain foo().bar().baz() is still invoking methods on the foo
   * instance.
   */
  @Suppress("RedundantIf")
  open fun returnsSelf(call: UCallExpression): Boolean {
    val resolvedCall =
      call.resolve()
        ?: run {
          failedResolve(call)
          return false
        }

    if (call.returnType is PsiPrimitiveType) {
      return false
    }

    // Some method names can suggest that this is not returning itself
    if (ignoreCopies()) {
      getMethodName(call)?.let { name ->
        if (
          name == "copy" ||
            name == "clone" ||
            name.startsWith("to") && name.length > 2 && Character.isUpperCase(name[2])
        ) {
          return false
        }
      }
    }

    val containingClass = resolvedCall.getReceiverOrContainingClass() ?: return false
    val returnTypeClass = (call.returnType as? PsiClassType)?.resolve()
    if (returnTypeClass == containingClass) {
      return true
    }

    // Kotlin stdlib functions also return "this" but for various
    // reasons don't have the right return type
    if (isReturningContext(call)) {
      return true
    }

    // Return a subtype is also likely self; see for example Snackbar
    if (
      returnTypeClass != null &&
        containingClass.name != "Object" &&
        returnTypeClass.isInheritor(containingClass, true)
    ) {
      return true
    }

    // Check if this is an extension method whose return type matches receiver
    if (call.returnType == getTypeOfExtensionMethod(resolvedCall)) {
      return true
    }

    return false
  }

  /**
   * Normally [returnsSelf] will try to guess whether a method returns itself, and one of the
   * heuristics is whether the method returns the type of its containing class. However, there are
   * some clues in the names when this may not be the case, such as "copy", or "clone", or "toX" (a
   * common conversion method convention in Kotlin). However, there may be scenarios where you
   * **do** want to consider these methods as transferring the tracked value, and in that case you
   * can return false from this method instead.
   */
  open fun ignoreCopies(): Boolean = true

  protected val references: MutableSet<PsiElement> = LinkedHashSet()
  protected val instances: MutableSet<UElement> = LinkedHashSet()
  protected val received: MutableSet<UElement> = LinkedHashSet()
  protected val types: MutableSet<PsiClass> = LinkedHashSet()

  init {
    if (references.isEmpty()) {
      references.addAll(initialReferences)
    }
    if (instances.isEmpty()) {
      instances.addAll(initial)
      for (element in initial) {
        if (element is UCallExpression) {
          val parent = skipParenthesizedExprUp(element.uastParent)
          if (parent is UQualifiedReferenceExpression && parent.selector == element) {
            instances.add(parent)
          }
        } else if (element is UVariable) {
          val reference = element.javaPsi as? PsiVariable
          if (reference != null && !references.contains(reference)) {
            references.add(reference)
          }
        }
        val type = (element as? UExpression)?.getExpressionType() as? PsiClassType
        type?.resolve()?.let { types.add(it) }
      }
    }
  }

  override fun visitCallExpression(node: UCallExpression): Boolean {
    val receiver = node.receiver?.skipParenthesizedExprDown()
    var matched = false
    if (receiver != null) {
      if (instances.contains(receiver)) {
        matched = true
      } else {
        val resolved = receiver.tryResolve()
        if (resolved != null) {
          if (references.contains(resolved)) {
            matched = true
          }
        }
      }
    } else if (received.contains(node)) {
      // We've already marked this (receiver-less) call as having been invoked on
      // a tracked element. For example, in tracker.apply { foo() }, foo has no
      // receiver, but if we've already determined that it binds to a method
      // in the tracked class, we've recorded that here to mark this as
      // being received on a tracked element.
      matched = true
    } else {
      val parent = skipParenthesizedExprUp(node.uastParent)
      val parentParent = skipParenthesizedExprUp(parent?.uastParent)
      val lambda =
        parent as? ULambdaExpression
          ?: parentParent as? ULambdaExpression
          // Kotlin 1.3.50 may add another layer UImplicitReturnExpression
          ?: skipParenthesizedExprUp(parentParent)?.uastParent as? ULambdaExpression
      val lambdaCall = skipParenthesizedExprUp(lambda?.uastParent) as? UCallExpression
      if (lambdaCall != null && isReturningContext(lambdaCall)) {
        if (instances.contains(node)) {
          matched = true
        }
      } else if (isScopingThis(node)) {
        val args = node.valueArguments
        if (
          args.size == 2 &&
            instances.contains<UElement?>(args[0].skipParenthesizedExprDown()) &&
            args[1].skipParenthesizedExprDown() is ULambdaExpression
        ) {
          handleLambdaSuffix(args[1].skipParenthesizedExprDown() as ULambdaExpression, node)
        }
      }
    }

    if (matched) {
      if (!initial.contains(node)) {
        receiver(node)
      }
      if (returnsSelf(node)) {
        track(node, node)
        val parent = skipParenthesizedExprUp(node.uastParent) as? UQualifiedReferenceExpression
        if (parent != null) {
          track(parent, node)
          val parentParent =
            skipParenthesizedExprUp(parent.uastParent) as? UQualifiedReferenceExpression
          val chained = parentParent?.selector
          if (chained != null) {
            track(chained, node)
          }
        }
      }

      val lambda =
        node.valueArguments.lastOrNull()?.skipParenthesizedExprDown()?.skipLabeledExpression()
          as? ULambdaExpression
      if (lambda != null) {
        handleLambdaSuffix(lambda, node)
      }

      if (isScopingIt(node)) {
        val arguments = node.valueArguments
        if (arguments.size == 1) {
          val arg = arguments[0]
          if (arg is UCallableReferenceExpression && arg.qualifierExpression == null) {
            // target.let(::method) -> here target is being passed as a parameter to the method
            argument(node, receiver ?: arg)
          }
        }
      }

      val resolved = node.resolve()
      if (resolved != null) {
        val unwrapped = resolved.unwrapped
        if (unwrapped is KtNamedFunction) {
          if (unwrapped.isExtensionDeclaration()) {
            // The value is really escaping into an extension function.
            // (TODO: Consider flowing into the method and looking?)
            argument(node, receiver ?: node)
          }
        }
      }
    }
    return super.visitCallExpression(node)
  }

  override fun afterVisitCallExpression(node: UCallExpression) {
    for (expression in node.valueArguments) {
      if (instances.contains(expression)) {
        if (!ignoreArgument(node, expression)) {
          argument(node, expression)
        }
      } else if (expression is UReferenceExpression) {
        val resolved = expression.resolve()
        if (references.contains(resolved)) {
          if (!ignoreArgument(node, expression)) {
            argument(node, expression)
          }
          break
        }
      }
    }

    super.afterVisitCallExpression(node)
  }

  override fun afterVisitExpressionList(node: UExpressionList) {
    @Suppress("UnstableApiUsage")
    if (node.kind == KotlinSpecialExpressionKinds.ELVIS) {
      for (expression in node.expressions) {
        if (instances.contains(expression)) {
          track(node, expression)
        } else if (
          expression is UReferenceExpression && references.contains(expression.resolve())
        ) {
          track(node, expression)
        }
      }
    }
    super.afterVisitExpressionList(node)
  }

  override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
    val qualifier =
      node.qualifierExpression
        // For odd reasons, UCallableReferenceExpression#qualifierExpression
        // "can be null if the qualifierType is known" which the Kotlin implementation
        // does. But we care about more than the type; we want to make sure
        // that it's bound to the right instance, so we have to work a bit
        // harder here.
        ?: (node.sourcePsi as? KtCallableReferenceExpression)?.receiverExpression?.toUElement()

    if (qualifier != null) {
      if (instances.contains(qualifier)) {
        methodReference(node)
      } else if (qualifier is UReferenceExpression) {
        if (references.contains(qualifier.resolve())) {
          methodReference(node)
        }
      }
    }
    return super.visitCallableReferenceExpression(node)
  }

  private fun handleLambdaSuffix(lambda: ULambdaExpression, node: UCallExpression) {
    if (isScopingIt(node)) {
      // If we have X.let { Y }, and X is a tracked instance, we should now
      // also track references to the "it" variable (or whatever the lambda
      // variable is called). Same case for X.also { Y }.
      if (lambda.valueParameters.size == 1) {
        val lambdaVar = lambda.valueParameters.first()
        track(lambdaVar as UElement, node)
        addVariableReference(lambdaVar)
      }
    } else if (isScopingThis(node)) {
      /*
      We have a lambda, where the tracked instance is "this", e.g.
          target.apply {
             first()
             second().something()
             descriptor = Integer.valueOf(0)
             ...
             if (third) { fourth(1) } else fourth(2)
             "string".apply {
                fifth()
             }
             sixth.seventh()
          }

      When we end up visiting the elements inside the lambda body, we want
      to flag any calls that are invoked on this tracked instance.

      This won't work with the normal call receiver approach, where for
      each call we see if the receiver has already been tagged as referencing
      our tracked instance, since here there are no receivers.

      The bigger complication is knowing whether a given element is actually
      referencing "this". In the above example, this depends a lot on what
      the declarations look like. For example, if "first" is a method in
      the type of target, then it does, otherwise it does not. And the
      call to fifth() is in a nested lambda where "this" is a different
      object (a string), but if string does not define a method called
      "fifth", it *will* bind to the "fifth" method in the outer apply
      block. Note also that this isn't just for calls; the assignment to
      descriptor for example could be a field reference in the target class,
      or it could be to an unrelated variable or field in a scope outside
      this apply block.

      This is all a long way of saying that we cannot just look
      at the top level expressions of the lambda, and that we need to
      figure out the binding for each receiver in nested blocks. Ideally,
      this information would simply be available in UAST, or even by
      looking inside the Kotlin PSI. However, the information is not there,
      so we'll need to use some approximations. What we'll do is resolve
      the reference, find its type, and then search outwards for the
      first scope that has a compatible type.
      */

      lambda.body.accept(
        object : AbstractUastVisitor() {
          private fun checkBinding(
            node: UElement,
            resolved: PsiElement?,
            target: MutableSet<UElement>
          ) {
            val member = resolved as? PsiMember ?: return
            val containingClass = member.containingClass
            if (isMatchingType(containingClass)) {
              target.add(node)
            }

            if (member is PsiMethod) {
              getTypeOfExtensionMethod(member)?.resolve()?.let { extensionClass ->
                if (isMatchingType(extensionClass)) {
                  target.add(node)
                }
              }
            }
          }

          override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
          ): Boolean {
            checkBinding(node, node.resolve(), received)
            return super.visitSimpleNameReferenceExpression(node)
          }

          override fun visitCallExpression(node: UCallExpression): Boolean {
            val callReceiver = node.receiver?.skipParenthesizedExprDown()
            if (callReceiver == null) {
              checkBinding(node, node.resolve(), received)
            } else if (callReceiver is UThisExpression) {
              // "this" could still reference an outer this scope, not just
              // the closest one
              checkBinding(callReceiver, node.resolve(), instances)
            }
            return super.visitCallExpression(node)
          }

          override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
            // If we run into another lambda that also scopes this, and it's
            // of the same type, then stop recursing, since inside this lambda,
            // that other scope will take over.
            val parent = skipParenthesizedExprUp(node.uastParent) as? UCallExpression
            val typeClass = (parent?.getExpressionType() as? PsiClassType)?.resolve()
            if (isMatchingType(typeClass)) {
              // type matches; don't visit the lambda body since it hides this
              if (parent != null && isScopingThis(parent)) {
                return true
              }
            }
            return super.visitLambdaExpression(node)
          }
        }
      )
    }
  }

  /** Returns true if the given class matches the types of tracked element scopes */
  @Suppress("RedundantIf")
  private fun isMatchingType(containingClass: PsiClass?): Boolean {
    containingClass ?: return false

    if (types.any { containingClass == it }) {
      return true
    }

    if (containingClass.name != "Object" && types.any { it.isInheritor(containingClass, true) }) {
      return true
    }

    return false
  }

  override fun afterVisitParenthesizedExpression(node: UParenthesizedExpression) {
    val expression = node.expression
    if (instances.contains(expression)) {
      track(node, expression)
    } else if (expression is UReferenceExpression && references.contains(expression.resolve())) {
      track(node, expression)
    }
    super.afterVisitParenthesizedExpression(node)
  }

  override fun afterVisitLocalVariable(node: ULocalVariable) {
    val initializer = node.uastInitializer?.skipParenthesizedExprDown()
    if (initializer != null) {
      if (instances.contains(initializer)) {
        // Instance is stored in a variable
        addVariableReference(node, initializer)
      } else if (initializer is UReferenceExpression) {
        val resolved = initializer.resolve()
        if (resolved != null && references.contains(resolved)) {
          addVariableReference(node, initializer)
        }
      }
    }
    super.afterVisitLocalVariable(node)
  }

  override fun afterVisitPostfixExpression(node: UPostfixExpression) {
    @Suppress("UnstableApiUsage")
    if (node.operator == KotlinPostfixOperators.EXCLEXCL) {
      val element = node.operand
      if (instances.contains(element)) {
        track(node, element)
      } else if (element is UReferenceExpression) {
        val resolved = element.resolve()
        if (references.contains(resolved)) {
          track(node, element)
        }
      }
    }

    super.afterVisitPostfixExpression(node)
  }

  override fun afterVisitBinaryExpressionWithType(node: UBinaryExpressionWithType) {
    val operand = node.operand
    if (instances.contains(operand)) {
      track(node, operand)
    } else if (operand is UReferenceExpression) {
      val resolved = operand.resolve()
      if (references.contains(resolved)) {
        track(node, operand)
      }
    }
    super.afterVisitBinaryExpressionWithType(node)
  }

  protected fun addVariableReference(node: UVariable, source: UElement = node): Boolean {
    return (node.sourcePsi?.let { track(it, source) } ?: false).or(
      node.javaPsi?.let { track(it, source) } ?: false
    )
  }

  override fun afterVisitSwitchClauseExpression(node: USwitchClauseExpression) {
    if (node is USwitchClauseExpressionWithBody) {
      for (expression in node.body.expressions) {
        if (instances.contains(expression)) {
          val switch = node.getParentOfType<USwitchExpression>()
          if (switch != null) {
            track(switch, expression)
            break
          }
        }
      }
    }

    super.afterVisitSwitchClauseExpression(node)
  }

  @Suppress("UnstableApiUsage") // yield is still experimental
  override fun afterVisitYieldExpression(node: UYieldExpression) {
    val element: UElement? = node.expression
    if (element != null && instances.contains(element)) {
      track(node, element)
    }
    super.afterVisitYieldExpression(node)
  }

  override fun afterVisitLabeledExpression(node: ULabeledExpression) {
    val expression = node.expression
    if (instances.contains(expression)) {
      track(node, expression)
    }
    super.afterVisitLabeledExpression(node)
  }

  override fun afterVisitIfExpression(node: UIfExpression) {
    // We can't use `isKotlin` here because `?:` is desugared to a list of expressions:
    //   temp variable w/ lhs as an initializer,
    //   `if` expression w/ `temp != null` condition, where `else` branch contains rhs
    // _without_ sourcePsi. In contrast, [UIfExpression]s in Java always have sourcePsi.
    if (!isJava(node.sourcePsi)) { // Does not apply to Java
      // Handle Elvis operator
      val parent = skipParenthesizedExprUp(node.uastParent)
      if (parent != null && node.isElvisIf()) {
        val then = node.thenExpression?.skipParenthesizedExprDown()
        if (then is USimpleNameReferenceExpression) {
          val variable = then.resolve()
          if (variable != null) {
            if (references.contains(variable)) {
              track(parent, node)
            } else if (variable is UVariable) {
              val psi = variable.javaPsi
              val sourcePsi = variable.sourcePsi
              if (
                psi != null && references.contains(psi) ||
                  sourcePsi != null && references.contains(sourcePsi)
              ) {
                track(parent, node)
              }
            }
          }
        }
      }
    } else if (!node.isTernary) {
      super.afterVisitIfExpression(node)
      return
    }

    val thenExpression = node.thenExpression?.skipParenthesizedExprDown()
    val elseExpression = node.elseExpression?.skipParenthesizedExprDown()
    val thenReference =
      if (thenExpression is USimpleNameReferenceExpression) thenExpression.resolve() else null
    val elseReference =
      if (elseExpression is USimpleNameReferenceExpression) elseExpression.resolve() else null
    if (
      thenExpression != null && instances.contains(thenExpression) ||
        thenReference != null && references.contains(thenReference)
    ) {
      track(node, thenExpression)
    } else if (
      elseExpression != null && instances.contains(elseExpression) ||
        elseReference != null && references.contains(elseReference)
    ) {
      track(node, elseExpression)
    } else {
      if (thenExpression is UBlockExpression) {
        thenExpression.expressions.lastOrNull()?.let {
          if (instances.contains(it)) {
            track(node, it)
          }
        }
      }
      if (elseExpression is UBlockExpression) {
        elseExpression.expressions.lastOrNull()?.let {
          if (instances.contains(it)) {
            track(node, it)
          }
        }
      }
    }

    super.afterVisitIfExpression(node)
  }

  override fun afterVisitTryExpression(node: UTryExpression) {
    val tryBlock = node.tryClause as? UBlockExpression ?: return
    tryBlock.expressions.lastOrNull()?.let { lastExpression ->
      if (instances.contains(lastExpression)) {
        track(node, lastExpression)
      }
    }
    for (clause in node.catchClauses) {
      val clauseBody = clause.body as? UBlockExpression ?: continue
      clauseBody.expressions.lastOrNull()?.let { lastExpression ->
        if (instances.contains(lastExpression)) {
          track(node, lastExpression)
        }
      }
    }

    super.afterVisitTryExpression(node)
  }

  override fun afterVisitBinaryExpression(node: UBinaryExpression) {
    if (!node.isAssignment()) {
      super.afterVisitBinaryExpression(node)
      return
    }

    clearLhsVariable(node)

    val rhs = node.rightOperand
    if (instances.contains(rhs)) {
      addBinaryExpressionReferences(node, rhs)
    } else if (rhs is UReferenceExpression) {
      val resolved = rhs.resolve()
      if (resolved != null && references.contains(resolved)) {
        addBinaryExpressionReferences(node, rhs)
      }
    }

    super.afterVisitBinaryExpression(node)
  }

  private fun clearLhsVariable(node: UBinaryExpression) {
    // If we reassign one of the variables, clear it out
    val lhs = node.leftOperand.skipParenthesizedExprDown().tryResolve()
    if (lhs != null && lhs != initial && references.contains(lhs)) {
      val block = skipParenthesizedExprUp(node.uastParent)
      if (block is UBlockExpression && initial.size == 1) {
        val element = (initial.first() as? UExpression)?.skipParenthesizedExprDown() ?: return
        if (element.isBelow(node)) {
          return
        }
        val initialBlock =
          element.getParentOfType<UElement>(
            false,
            UBlockExpression::class.java,
            UIfExpression::class.java
          ) ?: return

        if (initialBlock === block) {
          references.remove(lhs)
        } else if (node.isBelow(initialBlock)) {
          var referenced = false
          val target = skipParenthesizedExprUp(node.uastParent) ?: return

          initialBlock.accept(
            object : AbstractUastVisitor() {
              private var reachedTarget = false

              override fun afterVisitElement(node: UElement) {
                if (node == target) {
                  reachedTarget = true
                }
                super.afterVisitElement(node)
              }

              override fun visitSimpleNameReferenceExpression(
                node: USimpleNameReferenceExpression
              ): Boolean {
                if (reachedTarget) {
                  val resolved = node.resolve()
                  if (lhs.isEquivalentTo(resolved)) {
                    referenced = true
                    return true
                  }
                }
                return super.visitSimpleNameReferenceExpression(node)
              }
            }
          )
          if (!referenced) {
            // The variable is reassigned in a different (deeper) block than the origin, but
            // it is not referenced further after that
            references.remove(lhs)
          }
        }
      }
    }
  }

  private fun addBinaryExpressionReferences(node: UBinaryExpression, rhs: UExpression): Boolean {
    val leftOperand = node.leftOperand
    if (leftOperand is UArrayAccessExpression) {
      array(leftOperand)
    }
    val lhs = leftOperand.tryResolve() ?: return false
    var referenceAdded = false
    when (lhs) {
      is UVariable -> referenceAdded = addVariableReference(lhs)
      is PsiLocalVariable -> referenceAdded = track(lhs, node)
      is PsiParameter -> referenceAdded = track(lhs, node)
      is PsiField -> field(rhs)
      is PsiMethod -> field(rhs)
    }
    return referenceAdded
  }

  override fun afterVisitReturnExpression(node: UReturnExpression) {
    val returnValue = node.returnExpression?.skipParenthesizedExprDown()
    if (returnValue != null) {
      if (instances.contains(returnValue)) {
        returns(node)
      } else if (returnValue is UReferenceExpression) {
        val resolved = returnValue.resolve()
        if (resolved != null && references.contains(resolved)) {
          returns(node)
        }
      }
    }
    super.afterVisitReturnExpression(node)
  }

  override fun visitMethod(node: UMethod): Boolean {
    // If the reference is in a constructor method, it's very likely being passed in
    // to a property or chained constructor
    if (node.sourcePsi is KtConstructor<*>) {
      if (node.sourcePsi is KtSecondaryConstructor) {
        // See if the tracked value is coming from an initializer if we're in a constructor, and if
        // so,
        // see if the property is a parameter
        val body = node.uastBody
        val call =
          if (body is UBlockExpression) {
            body.expressions.firstOrNull()
          } else {
            body
          }
        if (call is UCallExpression) {
          for (parameter in node.uastParameters) {
            val initializer = parameter.uastInitializer?.skipParenthesizedExprDown()
            if (initializer != null && instances.contains(initializer)) {
              argument(call, parameter)
            }
          }
        }
      } else {
        // See if the tracked value is coming from a parameter initializer if we're in a
        // constructor.
        // This means the value can escape into the class (even if it's just a variable, not a
        // property,
        // constructor blocks throughout the class can directly reference it.)
        for (parameter in node.uastParameters) {
          val initializer = parameter.uastInitializer?.skipParenthesizedExprDown()
          if (initializer != null && instances.contains(initializer)) {
            field(parameter)
          }
        }
      }
    }
    return super.visitMethod(node)
  }

  /**
   * Dump the tracked elements of the analyzer; this is used for debugging only. Left in the code
   * since it's really useful anytime we need to debug what's happening (including when debugging
   * checks using the data flow analyzer).
   */
  override fun toString(): String {
    val sb = StringBuilder()
    sb.append("Instances:\n")
    for (instance in instances) {
      sb.append(instance.id())
      sb.append("\n")
    }
    if (received.isNotEmpty()) {
      sb.append("Receivers:\n")
      for (receiver in received) {
        sb.append(receiver.id())
        sb.append("\n")
      }
    }
    if (references.isNotEmpty()) {
      sb.append("References:\n")
      for (reference in references) {
        sb.append(reference.id())
        sb.append("\n")
      }
    }

    return sb.toString()
  }

  /** Computes identifying string for the given element; used for debugging only */
  fun UElement.id(): String {
    val s =
      Integer.toHexString(System.identityHashCode(this)) +
        ":" +
        this.sourcePsi?.text?.replace(Regex("\\s+"), " ")
    val max = 100
    return if (s.length > max) {
      s.substring(0, max / 2) + "..." + s.substring(s.length - (max / 2 + 3))
    } else {
      s
    }
  }

  fun PsiElement.id(): String {
    val s =
      Integer.toHexString(System.identityHashCode(this)) +
        ":" +
        this.text?.replace(Regex("\\s+"), " ")
    val max = 100
    return if (s.length > max) {
      s.substring(0, max / 2) + "..." + s.substring(s.length - (max / 2 + 3))
    } else {
      s
    }
  }

  companion object {
    /** If this method looks like an extension method, return its receiver type */
    fun getTypeOfExtensionMethod(method: PsiMethod): PsiClassType? {
      // If this is an extension method whose return type matches receiver
      val parameterList = method.parameterList
      if (parameterList.parametersCount > 0) {
        val firstParameter = parameterList.getParameter(0)
        if (firstParameter is PsiParameter && firstParameter.name.startsWith("\$this\$")) {
          return firstParameter.type as? PsiClassType
        }
      }

      return null
    }

    fun getVariableElement(
      rhs: UCallExpression,
      allowChainedCalls: Boolean,
      allowFields: Boolean
    ): PsiVariable? {
      var parent = skipParenthesizedExprUp(rhs.getQualifiedParentOrThis().uastParent)

      // Handle some types of chained calls; e.g. you might have
      //    var = prefs.edit().put(key,value)
      // and here we want to skip past the put call
      if (allowChainedCalls) {
        while (true) {
          if (parent is UQualifiedReferenceExpression) {
            val parentParent = skipParenthesizedExprUp(parent.uastParent)
            if (parentParent is UQualifiedReferenceExpression) {
              parent = skipParenthesizedExprUp(parentParent.uastParent)
            } else if (parentParent is UVariable || parentParent is UPolyadicExpression) {
              parent = parentParent
              break
            } else {
              break
            }
          } else {
            break
          }
        }
      }

      if (parent != null && parent.isAssignment()) {
        val assignment = parent as UBinaryExpression
        val lhs = assignment.leftOperand
        if (lhs is UReferenceExpression) {
          val resolved = lhs.resolve()
          if (resolved is PsiVariable && (allowFields || resolved !is PsiField)) {
            // e.g. local variable, parameter - but not a field
            return resolved
          }
        }
      } else if (parent is UVariable && (allowFields || parent !is UField)) {
        return parent.javaPsi as? PsiVariable
      }

      return null
    }
  }
}

/**
 * [DataFlowAnalyzer] which also tracks whether the tracked instances escape into fields, method
 * calls, array assignments or as return values. Subclasses can check the value of the [escaped]
 * property after visiting.
 */
open class EscapeCheckingDataFlowAnalyzer(
  initial: Collection<UElement>,
  initialReferences: Collection<PsiVariable> = emptyList()
) : DataFlowAnalyzer(initial, initialReferences) {
  var escaped: Boolean = false

  override fun field(field: UElement) {
    escaped = true
  }

  override fun argument(call: UCallExpression, reference: UElement) {
    escaped = true
  }

  override fun returns(expression: UReturnExpression) {
    escaped = true
  }

  override fun array(array: UArrayAccessExpression) {
    escaped = true
  }
}

/**
 * Analyzer which makes it easier to check for a scenario where you want to track the flow from some
 * sort of initialization expression (such as creating a transaction) to make sure that it
 * eventually ends up calling one or more "target" methods (such as a transaction commit function).
 *
 * All you need to do is override one or more of the `isTargetMethod` functions to indicate that the
 * given call or method is the target you're looking for.
 *
 * There are some utility methods in the companion object which makes it even simpler for some
 * common and basic scenarios, but in general this continues to extend a UAST visitor, so you can
 * override various AST visitor methods to customize the logic as needed.
 */
abstract class TargetMethodDataFlowAnalyzer(
  initial: Collection<UElement>,
  initialReferences: Collection<PsiVariable> = emptyList()
) : EscapeCheckingDataFlowAnalyzer(initial, initialReferences) {
  var targetReached = false
  var targetReference: UElement? = null

  /**
   * Simple name filter; this lets you reject names that have no chance of being the target method,
   * so we don't need to proceed to resolve the call for further checking. For convenience, it's
   * safe to just return true here and do full filtering when given the method.
   */
  open fun isTargetMethodName(name: String): Boolean = true

  /**
   * Returns true if the given [method] is one of the targets we're after. If [method] is null,
   * there was a resolve problem.
   */
  open fun isTargetMethod(name: String, method: PsiMethod?): Boolean = false

  /**
   * Returns true if the given [method] is one of the targets we're after. If [method] is null,
   * there was a resolve problem. Here we're also passing in either the corresponding call
   * expression or corresponding method reference expression, in case you want to perform additional
   * validation.
   */
  open fun isTargetMethod(
    name: String,
    method: PsiMethod?,
    call: UCallExpression?,
    methodRef: UCallableReferenceExpression?
  ): Boolean {
    return isTargetMethod(name, method)
  }

  override fun visitElement(node: UElement): Boolean {
    return targetReached
  }

  override fun receiver(call: UCallExpression) {
    super.receiver(call)

    if (targetReached) {
      return
    }
    val name = call.methodName ?: call.methodIdentifier?.name
    if (name != null && isTargetMethodName(name)) {
      val resolved = call.resolve()
      if (isTargetMethod(name, resolved, call, null)) {
        targetReached = true
        targetReference = call
        return
      }
    }
  }

  override fun methodReference(call: UCallableReferenceExpression) {
    super.methodReference(call)
    val name = call.callableName
    if (isTargetMethodName(name)) {
      val resolved = call.resolve()
      if (resolved is PsiMethod) {
        if (isTargetMethod(name, resolved, null, call)) {
          val method =
            initial.firstOrNull()?.getParentOfType<UMethod>()
              ?: call.getParentOfType<UMethod>()
              ?: return
          val callTracker =
            object : EscapeCheckingDataFlowAnalyzer(listOf(call)) {
              override fun visitElement(node: UElement): Boolean {
                return targetReached
              }

              override fun receiver(call: UCallExpression) {
                targetReference = call
                targetReached = true
              }
            }
          method.accept(callTracker)
          if (callTracker.escaped) escaped = true
          if (callTracker.failedResolve) failedResolve = true
        }
      }
    }
  }

  /**
   * After visiting the given method, returns false if we reached the target, or if one of the
   * tracked instances escaped from the method (via a return or method call or assignment into a
   * field etc), or if we have some uncertainty about it (for example if there were resolve
   * problems, and we observed a call or method reference with a name match).
   */
  internal fun isMissingTarget(within: UMethod, allowEscape: Boolean): Boolean {
    if (targetReached || escaped && !allowEscape) {
      return false
    }
    if (failedResolve) {
      // There was a resolve failure somewhere; see if there is a match on the name
      // part anywhere, and if so, don't conclude that the target was never reached.
      var found = false
      within.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val name = node.methodName ?: node.methodIdentifier?.name
            if (name != null && isTargetMethodName(name)) {
              val resolved = node.resolve()
              if (resolved !is PsiMethod || isTargetMethod(name, resolved, node, null)) {
                found = true
              }
            }
            return found || super.visitCallExpression(node)
          }

          override fun visitCallableReferenceExpression(
            node: UCallableReferenceExpression
          ): Boolean {
            val name = node.callableName
            if (isTargetMethodName(name)) {
              val resolved = node.resolve()
              if (resolved !is PsiMethod || isTargetMethod(name, resolved, null, node)) {
                found = true
              }
            }
            return found || super.visitCallableReferenceExpression(node)
          }
        }
      )
      return !found
    }

    return true
  }

  companion object {
    /**
     * Creates a simple [TargetMethodDataFlowAnalyzer] looking for the given method (identified by
     * name and containing class fully qualified name) starting from the given source element.
     */
    fun create(source: UElement, targets: Map<String, List<String>>): TargetMethodDataFlowAnalyzer {
      return object : TargetMethodDataFlowAnalyzer(listOf(source)) {
        override fun isTargetMethodName(name: String): Boolean {
          return targets.containsKey(name)
        }

        override fun isTargetMethod(name: String, method: PsiMethod?): Boolean {
          val classes = targets[name] ?: return false
          return classes.contains(method?.containingClass?.qualifiedName)
        }
      }
    }

    /**
     * Creates a simple [TargetMethodDataFlowAnalyzer] looking for the given method (identified by
     * name and list of containing class fully qualified names) starting from the given source
     * element.
     */
    fun create(
      source: UElement,
      methodName: String,
      containingClass: String?
    ): TargetMethodDataFlowAnalyzer {
      return object : TargetMethodDataFlowAnalyzer(listOf(source)) {
        override fun isTargetMethodName(name: String): Boolean {
          return methodName == name
        }

        override fun isTargetMethod(name: String, method: PsiMethod?): Boolean {
          return containingClass == null ||
            method?.containingClass?.qualifiedName == containingClass
        }
      }
    }
  }
}

/**
 * Given a [TargetMethodDataFlowAnalyzer], visits this method, and then checks whether the target
 * was reached or not (and returns true if **not** found, or unknown because the instance escapes
 * this method and could be reached elsewhere.)
 *
 * (After visiting the given method, returns false if we reached the target, or if one of the
 * tracked instances escaped from the method (via a return or method call or assignment into a field
 * etc), or if we have some uncertainty about it, for example if there were resolve problems, and we
 * observed a call or method reference with a name match).
 */
fun UMethod.isMissingTarget(
  analyzer: TargetMethodDataFlowAnalyzer,
  allowEscape: Boolean = false
): Boolean {
  accept(analyzer)
  return analyzer.isMissingTarget(this, allowEscape)
}

/**
 * Returns true if the given method contains at least one call which [filter] returns true for.
 * Typically used in conjunction with [DataFlowAnalyzer] is [DataFlowAnalyzer.failedResolve] is
 * true; in that case, we can't confidently follow the flow from the initial expression to a target
 * method call on the right instance, but we can quickly check if the target call is never called on
 * *any* instance, and if so we're still sure there's a problem.
 *
 * TODO: Consider whether escape analysis is correct in this case...
 */
fun UMethod.anyCall(filter: (UCallExpression) -> Boolean): Boolean {
  var found = false
  accept(
    object : AbstractUastVisitor() {
      override fun visitCallExpression(node: UCallExpression): Boolean {
        if (filter(node)) {
          found = true
        }
        return found || super.visitCallExpression(node)
      }
    }
  )
  return found
}
