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

package com.android.tools.instrumentation.threading.agent.callback;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class BaselineViolationsTest {

    static final String BASELINE_TEXT =
            "#comment\n"
                    + "   \n"
                    + "\n"
                    + "com.android.ClassA#methodOne\n"
                    + "com.android.ClassA#methodTwo\n"
                    + "#com.android.ClassA#commentedOutMethod\n";

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Test
    public void methodInBaseline_isIgnored() {
        InputStream stream = new ByteArrayInputStream(BASELINE_TEXT.getBytes());
        BaselineViolations baseline = BaselineViolations.fromStream(stream);

        assertThat(baseline.isIgnored(createStackTrace("com.android.ClassA", "methodOne")))
                .isTrue();
        assertThat(
                        baseline.isIgnored(
                                createStackTrace(
                                        "com.android.ClassA",
                                        "methodOne",
                                        "ParentClass",
                                        "parentMethod")))
                .isTrue();
    }

    @Test
    public void methodNotInBaseline_isNotIgnored() {
        InputStream stream = new ByteArrayInputStream(BASELINE_TEXT.getBytes());
        BaselineViolations baseline = BaselineViolations.fromStream(stream);

        assertThat(baseline.isIgnored(createStackTrace("com.android.ClassA", "notListedMethod")))
                .isFalse();
        assertThat(baseline.isIgnored(createStackTrace("com.android.NotListedClass", "methodOne")))
                .isFalse();
        assertThat(
                        baseline.isIgnored(
                                createStackTrace("com.android.ClassA", "methodOnePlusSuffix")))
                .isFalse();
        assertThat(
                        baseline.isIgnored(
                                createStackTrace("com.android.ClassA", "prefixPlusMethodOne")))
                .isFalse();
    }

    @Test
    public void commentedOutMethod_isNotIgnored() {
        InputStream stream = new ByteArrayInputStream(BASELINE_TEXT.getBytes());
        BaselineViolations baseline = BaselineViolations.fromStream(stream);

        assertThat(baseline.isIgnored(createStackTrace("com.android.ClassA", "commentedOutMethod")))
                .isFalse();
    }

    @Test
    public void stackTraceIsNotIgnored_whenBaseLineMethodIsUpTheStack() {
        // If the callstack looks like "method1->method2" then it should be excluded from
        // threading checks if "method2" is in the baseline. The presence of "method1" in
        // the baseline has no effect on the exclusion.
        InputStream stream = new ByteArrayInputStream(BASELINE_TEXT.getBytes());
        BaselineViolations baseline = BaselineViolations.fromStream(stream);

        assertThat(
                        baseline.isIgnored(
                                createStackTrace(
                                        "leafClass",
                                        "leafMethod",
                                        "com.android.ClassA",
                                        "methodOne")))
                .isFalse();
    }

    private static List<StackTraceElement> createStackTrace(
            String declaringClass1, String methodName1) {
        return ImmutableList.of(createStackTraceElement(declaringClass1, methodName1));
    }

    private static List<StackTraceElement> createStackTrace(
            String declaringClass1,
            String methodName1,
            String declaringClass2,
            String methodName2) {
        return ImmutableList.of(
                createStackTraceElement(declaringClass1, methodName1),
                createStackTraceElement(declaringClass2, methodName2));
    }

    private static StackTraceElement createStackTraceElement(
            String declaringClass, String methodName) {
        return new StackTraceElement(declaringClass, methodName, null, 0);
    }
}
