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
package com.android.tools.deploy.liveedit;

import static com.android.tools.deploy.liveedit.Utils.buildClass;

import org.junit.Assert;
import org.junit.Test;

public class TestMath {
    private static final Class CLAZZ = InvokeMath.class;
    private static final String CLASS_NAME = "InvokeMath";

    @Test
    public void testFloatPositiveInfinity() throws Exception {
        String methodName = "divf";
        String methodDesc = "(FF)F";
        byte[] byteCode = buildClass(CLAZZ);
        try {
            LiveEditStubs.addClass(CLASS_NAME, new Interpretable(byteCode), false);
            Object[] parameters = {null, null, 1f, 0f};
            Float f = (Float) LiveEditStubs.stubL(CLASS_NAME, methodName, methodDesc, parameters);
            Assert.assertTrue(
                    "Float +infinity expected", f.floatValue() == Float.POSITIVE_INFINITY);
        } finally {
            LiveEditStubs.deleteClass(CLASS_NAME);
        }
    }

    @Test
    public void testFloatNegativeInfinity() throws Exception {
        String methodName = "divf";
        String methodDesc = "(FF)F";
        byte[] byteCode = buildClass(CLAZZ);
        try {
            LiveEditStubs.addClass(CLASS_NAME, new Interpretable(byteCode), false);
            Object[] parameters = {null, null, -1f, 0f};
            Float f = (Float) LiveEditStubs.stubL(CLASS_NAME, methodName, methodDesc, parameters);
            Assert.assertTrue(
                    "Float -infinity expected", f.floatValue() == Float.NEGATIVE_INFINITY);
        } finally {
            LiveEditStubs.deleteClass(CLASS_NAME);
        }
    }

    @Test
    public void testFloatNaN() throws Exception {
        String methodName = "modf";
        String methodDesc = "(FF)F";
        byte[] byteCode = buildClass(CLAZZ);
        try {
            LiveEditStubs.addClass(CLASS_NAME, new Interpretable(byteCode), false);
            Object[] parameters = {null, null, 1f, 0f};
            Float f = (Float) LiveEditStubs.stubL(CLASS_NAME, methodName, methodDesc, parameters);
            Assert.assertTrue("Float NaN expected", f.isNaN());
        } finally {
            LiveEditStubs.deleteClass(CLASS_NAME);
        }
    }

    @Test
    public void testDoublePositiveInfinity() throws Exception {
        String methodName = "divd";
        String methodDesc = "(DD)D";
        byte[] byteCode = buildClass(CLAZZ);
        try {
            LiveEditStubs.addClass(CLASS_NAME, new Interpretable(byteCode), false);
            Object[] parameters = {null, null, 1d, 0d};
            Double d = (Double) LiveEditStubs.stubL(CLASS_NAME, methodName, methodDesc, parameters);
            Assert.assertTrue(
                    "Double +infinity expected", d.doubleValue() == Double.POSITIVE_INFINITY);
        } finally {
            LiveEditStubs.deleteClass(CLASS_NAME);
        }
    }

    @Test
    public void testDoubleNegativeInfinity() throws Exception {
        String methodName = "divd";
        String methodDesc = "(DD)D";
        byte[] byteCode = buildClass(CLAZZ);
        try {
            LiveEditStubs.addClass(CLASS_NAME, new Interpretable(byteCode), false);
            Object[] parameters = {null, null, -1d, 0d};
            Double d = (Double) LiveEditStubs.stubL(CLASS_NAME, methodName, methodDesc, parameters);
            Assert.assertTrue(
                    "Double -infinity expected", d.doubleValue() == Double.NEGATIVE_INFINITY);
        } finally {
            LiveEditStubs.deleteClass(CLASS_NAME);
        }
    }

    @Test
    public void testDoubleNaN() throws Exception {
        String methodName = "modd";
        String methodDesc = "(DD)D";
        byte[] byteCode = buildClass(CLAZZ);
        try {
            LiveEditStubs.addClass(CLASS_NAME, new Interpretable(byteCode), false);
            Object[] parameters = {null, null, 1d, 0d};
            Double d = (Double) LiveEditStubs.stubL(CLASS_NAME, methodName, methodDesc, parameters);
            Assert.assertTrue("Double NaN expected", d.isNaN());
        } finally {
            LiveEditStubs.deleteClass(CLASS_NAME);
        }
    }
}
