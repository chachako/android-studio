/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UElement

/** Evaluates constant expressions  */
class ConstantEvaluator {
    internal var allowUnknown = false
    internal var allowFieldInitializers = false
    private val impl = ConstantEvaluatorImpl(this)

    /**
     * Whether we allow computing values where some terms are unknown. For example, the expression
     * `"foo" + x + "bar"` would return `null` without and `"foobar"` with.
     *
     * @return this for constructor chaining
     */
    fun allowUnknowns(): ConstantEvaluator = this.also { allowUnknown = true }
    fun allowFieldInitializers(): ConstantEvaluator = this.also { allowFieldInitializers = true }

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any
     *
     * @param node the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    fun evaluate(node: UElement?): Any? = impl.evaluate(node)

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any
     *
     * @param node the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    fun evaluate(node: PsiElement?): Any? = impl.evaluate(node)

    companion object {
        fun getArraySize(array: Any?): Int = ConstantEvaluatorImpl.getArraySize(array)

        /**
         * Evaluates the given node and returns the constant value it resolves to, if any. Convenience
         * wrapper which creates a new [ConstantEvaluator], evaluates the node and returns
         * the result.
         *
         * @param context the context to use to resolve field references, if any
         * @param node the node to compute the constant value for
         * @return the corresponding constant value - a String, an Integer, a Float, and so on
         */
        @JvmStatic
        fun evaluate(context: JavaContext?, node: PsiElement): Any? = ConstantEvaluatorImpl.evaluate(context, node)

        /**
         * Evaluates the given node and returns the constant value it resolves to, if any. Convenience
         * wrapper which creates a new [ConstantEvaluator], evaluates the node and returns
         * the result.
         *
         * @param context the context to use to resolve field references, if any
         * @param element the node to compute the constant value for
         * @return the corresponding constant value - a String, an Integer, a Float, and so on
         */
        @JvmStatic
        fun evaluate(context: JavaContext?, element: UElement): Any? = ConstantEvaluatorImpl.evaluate(context, element)

        /**
         * Evaluates the given node and returns the constant string it resolves to, if any. Convenience
         * wrapper which creates a new [ConstantEvaluator], evaluates the node and returns
         * the result if the result is a string.
         *
         * @param context the context to use to resolve field references, if any
         * @param node the node to compute the constant value for
         * @param allowUnknown whether we should construct the string even if some parts of it are
         * unknown
         * @return the corresponding string, if any
         */
        @JvmStatic
        fun evaluateString(context: JavaContext?, node: PsiElement, allowUnknown: Boolean): String? =
            ConstantEvaluatorImpl.evaluateString(context, node, allowUnknown)

        /**
         * Computes the last assignment to a given variable counting backwards from the given context
         * element
         *
         * @param usage the usage site to search backwards from
         * @param variable the variable
         * @param allowNonConst If set to true and the returned assignment is non-null, this means that
         * the last assignment is inside an if/else block, whose execution may not be statically
         * determinable.
         * @return the last assignment or null
         */
        @JvmStatic
        fun findLastAssignment(usage: PsiElement, variable: PsiVariable): PsiExpression? =
            ConstantEvaluatorImpl.findLastAssignment(usage, variable)

        /**
         * Evaluates the given node and returns the constant string it resolves to, if any. Convenience
         * wrapper which creates a new [ConstantEvaluator], evaluates the node and returns
         * the result if the result is a string.
         *
         * @param context the context to use to resolve field references, if any
         * @param element the node to compute the constant value for
         * @param allowUnknown whether we should construct the string even if some parts of it are
         * unknown
         * @return the corresponding string, if any
         */
        @JvmStatic
        fun evaluateString(context: JavaContext?, element: UElement, allowUnknown: Boolean): String? =
            ConstantEvaluatorImpl.evaluateString(context, element, allowUnknown)

        /** Returns true if the node is pointing to an array literal  */
        @JvmStatic
        fun isArrayLiteral(node: PsiElement?): Boolean = ConstantEvaluatorImpl.isArrayLiteral(node)

        /** Returns true if the node is pointing to an array literal  */
        @JvmStatic
        fun isArrayLiteral(node: UElement?): Boolean = ConstantEvaluatorImpl.isArrayLiteral(node)
    }
}
