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
package com.android.tools.deploy.liveedit;

import static com.android.tools.deploy.liveedit.Utils.buildClass;

import org.junit.Assert;
import org.junit.Test;

public class InfiniteLoopDetectionTest {

    @Test
    public void testWhileTrueLoop() throws Exception {
        byte[] classInput = buildClass(this.getClass());
        MethodBodyEvaluator ev =
                new MethodBodyEvaluator(classInput, "methodWithInfiniteLoop", "()I");
        try {
            ev.evalStatic(new Object[] {});
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Infinite Loop"));
        }
    }

    @Test
    public void testWhileTrueLoopKotlin() throws Exception {
        byte[] classInput = buildClass(InfiniteLoopsKotlin.Companion.getClass());
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "infiniteLoop1", "()I");
        try {
            ev.eval(
                    InfiniteLoopsKotlin.Companion,
                    "com.android.tools.deploy.liveedit.InfiniteLoopsKotlin.Companion",
                    new Object[] {});
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Infinite Loop"));
        }

        ev = new MethodBodyEvaluator(classInput, "infiniteLoop2", "()I");
        try {
            ev.eval(
                    InfiniteLoopsKotlin.Companion,
                    "com.android.tools.deploy.liveedit.InfiniteLoopsKotlin.Companion",
                    new Object[] {});
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Infinite Loop"));
        }
    }

    @Test
    public void testNormalLoop() throws Exception {
        byte[] classInput = buildClass(this.getClass());
        MethodBodyEvaluator ev =
                new MethodBodyEvaluator(classInput, "methodWithNormalBackEdge", "()I");
        Object result = ev.evalStatic(new Object[] {});
        // arithmetic sum = n * (n - 1) / 2
        Assert.assertEquals(result, 100 * (100 - 1) / 2);
    }

    public static int methodWithInfiniteLoop() {
        while (true) {}
    }

    public static int methodWithNormalBackEdge() {
        int x = 0;
        for (int i = 0; i < 100; i++) {
            x += i;
        }
        return x;
    }
}
