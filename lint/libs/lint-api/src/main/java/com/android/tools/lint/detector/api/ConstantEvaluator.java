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
package com.android.tools.lint.detector.api;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;

/** Evaluates constant expressions */
public class ConstantEvaluator {
    boolean allowUnknown = false;
    boolean allowFieldInitializers = false;
    private final ConstantEvaluatorImpl impl = new ConstantEvaluatorImpl(this);

    /**
     * Whether we allow computing values where some terms are unknown. For example, the expression
     * `"foo" + x + "bar"` would return `null` without and `"foobar"` with.
     *
     * @return this for constructor chaining
     */
    public ConstantEvaluator allowUnknowns() {
        allowUnknown = true;
        return this;
    }

    public ConstantEvaluator allowFieldInitializers() {
        allowFieldInitializers = true;
        return this;
    }

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any
     *
     * @param node the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    @Nullable
    public Object evaluate(@Nullable UElement node) {
        return impl.evaluate(node);
    }

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any
     *
     * @param node the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    @Nullable
    public Object evaluate(@Nullable PsiElement node) {
        return impl.evaluate(node);
    }

    public static int getArraySize(@Nullable Object array) {
        return ConstantEvaluatorImpl.Companion.getArraySize(array);
    }

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any. Convenience
     * wrapper which creates a new {@link ConstantEvaluator}, evaluates the node and returns the
     * result.
     *
     * @param context the context to use to resolve field references, if any
     * @param node the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    @Nullable
    public static Object evaluate(@Nullable JavaContext context, @NotNull PsiElement node) {
        return ConstantEvaluatorImpl.Companion.evaluate(context, node);
    }

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any. Convenience
     * wrapper which creates a new {@link ConstantEvaluator}, evaluates the node and returns the
     * result.
     *
     * @param context the context to use to resolve field references, if any
     * @param element the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    @Nullable
    public static Object evaluate(@Nullable JavaContext context, @NotNull UElement element) {
        return ConstantEvaluatorImpl.Companion.evaluate(context, element);
    }

    /**
     * Evaluates the given node and returns the constant string it resolves to, if any. Convenience
     * wrapper which creates a new {@link ConstantEvaluator}, evaluates the node and returns the
     * result if the result is a string.
     *
     * @param context the context to use to resolve field references, if any
     * @param node the node to compute the constant value for
     * @param allowUnknown whether we should construct the string even if some parts of it are
     *     unknown
     * @return the corresponding string, if any
     */
    @Nullable
    public static String evaluateString(
            @Nullable JavaContext context, @NotNull PsiElement node, boolean allowUnknown) {
        return ConstantEvaluatorImpl.Companion.evaluateString(context, node, allowUnknown);
    }

    /**
     * Computes the last assignment to a given variable counting backwards from the given context
     * element
     *
     * @param usage the usage site to search backwards from
     * @param variable the variable
     * @return the last assignment or null
     */
    @Nullable
    public static PsiExpression findLastAssignment(
            @NotNull PsiElement usage, @NotNull PsiVariable variable) {
        return ConstantEvaluatorImpl.Companion.findLastAssignment(usage, variable, false);
    }

    /**
     * Evaluates the given node and returns the constant string it resolves to, if any. Convenience
     * wrapper which creates a new {@link ConstantEvaluator}, evaluates the node and returns the
     * result if the result is a string.
     *
     * @param context the context to use to resolve field references, if any
     * @param element the node to compute the constant value for
     * @param allowUnknown whether we should construct the string even if some parts of it are
     *     unknown
     * @return the corresponding string, if any
     */
    @Nullable
    public static String evaluateString(
            @Nullable JavaContext context, @NotNull UElement element, boolean allowUnknown) {
        return ConstantEvaluatorImpl.Companion.evaluateString(context, element, allowUnknown);
    }

    /** Returns true if the node is pointing to an array literal */
    public static boolean isArrayLiteral(@Nullable PsiElement node) {
        return ConstantEvaluatorImpl.Companion.isArrayLiteral(node);
    }

    /** Returns true if the node is pointing to an array literal */
    public static boolean isArrayLiteral(@Nullable UElement node) {
        return ConstantEvaluatorImpl.Companion.isArrayLiteral(node);
    }
}
