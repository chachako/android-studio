/*
 * Copyright (C) 2024 The Android Open Source Project
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

public class MultiDimensionArrayTest {

    @Test
    public void testInt() throws Exception {
        byte[] classInput = buildClass(this.getClass());
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "createIntArray", "()I");
        Object value = ev.evalStatic(new Object[] {});
        Assert.assertEquals(1, value);
    }

    public static int createIntArray() {
        int[] x = new int[] {1, 2, 3};
        return intAt(x);
    }

    private static int intAt(int[] x) {
        return x[0];
    }

    @Test
    public void testMultiInt() throws Exception {
        byte[] classInput = buildClass(this.getClass());
        MethodBodyEvaluator ev =
                new MethodBodyEvaluator(classInput, "createMultiDimIntArray", "()I");
        Object value = ev.evalStatic(new Object[] {});
        Assert.assertEquals(5, value);
    }

    public static int createMultiDimIntArray() {
        int[][] x =
                new int[][] {
                    new int[] {1, 2, 3},
                    new int[] {4, 5, 6},
                    new int[] {7, 8, 9},
                };
        return intAt(x);
    }

    private static int intAt(int[][] x) {
        return x[1][1];
    }

    @Test
    public void testMultiString() throws Exception {
        byte[] classInput = buildClass(this.getClass());
        MethodBodyEvaluator ev =
                new MethodBodyEvaluator(classInput, "createMultiDimStringArray", "()I");
        Object value = ev.evalStatic(new Object[] {});
        Assert.assertEquals(9, value);
    }

    public static int createMultiDimStringArray() {
        String[][] x =
                new String[][] {
                    new String[] {"1", "2", "3"},
                    new String[] {"4", "5", "6"},
                    new String[] {"7", "8", "9"},
                };
        return intAt(x);
    }

    private static int intAt(String[][] x) {
        return Integer.parseInt(x[2][2]);
    }

    @Test
    public void checkDimensions() throws Exception {
        byte[] classInput = buildClass(this.getClass());
        MethodBodyEvaluator ev =
                new MethodBodyEvaluator(
                        classInput, "allocateMultiDimStringArray", "()[[Ljava/lang/String;");
        String[][] value = (String[][]) ev.evalStatic(new Object[] {});
        Assert.assertEquals(3, value.length);
        Assert.assertEquals(1, value[0].length);
        Assert.assertEquals(5, value[1].length);
        Assert.assertEquals(3, value[2].length);
        Assert.assertEquals(9, intAt(value));
    }

    public static String[][] allocateMultiDimStringArray() {
        String[][] x =
                new String[][] {
                    new String[] {"1"}, new String[] {"2", "3", "4", "5", "6"}, new String[] {"7"},
                };
        x[2] = new String[] {"7", "8", "nine"};
        x[2][2] = "9";
        return x;
    }
}
