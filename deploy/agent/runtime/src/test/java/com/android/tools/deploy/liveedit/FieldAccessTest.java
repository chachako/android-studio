/*
 * Copyright (C) 2021 The Android Open Source Project
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

import java.io.IOException;
import org.junit.Assert;

public class FieldAccessTest {
    @org.junit.Test
    public void testIntFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "getIntFields", "()I")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getIntFields(), result);
    }

    @org.junit.Test
    public void testByteFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "getByteFields", "()I")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getByteFields(), result);
    }

    @org.junit.Test
    public void testShortFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "getShortFields", "()I")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getShortFields(), result);
    }

    @org.junit.Test
    public void testLongFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "getLongFields", "()J")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getLongFields(), result);
    }

    @org.junit.Test
    public void testFloatFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "getFloatFields", "()F")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getFloatFields(), result);
    }

    @org.junit.Test
    public void testDoubleFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "getDoubleFields", "()D")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getDoubleFields(), result);
    }

    @org.junit.Test
    public void testBooleanFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "getBooleanFields", "()Z")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getBooleanFields(), result);
    }

    @org.junit.Test
    public void testCharFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "getCharFields", "()Ljava/lang/String;")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getCharFields(), result);
    }

    @org.junit.Test
    public void testObjectFields() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "getObjectFields", "()Ljava/lang/String;")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getObjectFields(), result);
    }

    @org.junit.Test
    public void testSetBooleanField() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "setBoolean", "()V").evalStatic(new Object[0]);
    }

    @org.junit.Test
    public void testSetStaticBooleanField() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "setStaticBoolean", "()V")
                        .evalStatic(new Object[0]);
    }

    @org.junit.Test
    public void testAccessProtectedParent() throws Exception {
        byte[] classInput = buildClass(Child.class);
        Child child = new Child(0);
        int protectedFieldValue = 5;
        Object result =
                new MethodBodyEvaluator(classInput, "accessParentProtectedField", "(I)I")
                        .eval(child, Child.class.getTypeName(), new Object[] {protectedFieldValue});
        Integer i = (Integer) result;

        Assert.assertEquals(
                "Accessed parent field",
                child.accessParentProtectedField(protectedFieldValue),
                i.intValue());
    }

    @org.junit.Test
    public void testAccessProtectedParentWithoutSuper() throws Exception {
        byte[] classInput = buildClass(Child.class);
        Child child = new Child(0);
        int protectedFieldValue = 5;
        Object result =
                new MethodBodyEvaluator(
                                classInput, "accessParentProtectedFieldWithoutSuper", "(I)I")
                        .eval(child, Child.class.getTypeName(), new Object[] {protectedFieldValue});
        Integer i = (Integer) result;

        Assert.assertEquals(
                "Accessed parent field",
                child.accessParentProtectedFieldWithoutSuper(protectedFieldValue),
                i.intValue());
    }

    @org.junit.Test
    public void testGetParentStatic() throws Exception {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "getInheritedStatic", "()Ljava/lang/String;")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getInheritedStatic(), result);
    }

    @org.junit.Test
    public void testParentViaChildStatic() throws IOException {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(
                                classInput, "getParentViaChildStatic", "()Ljava/lang/String;")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getParentViaChildStatic(), result);
    }

    @org.junit.Test
    public void testParentViaParentStatic() throws IOException {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(
                                classInput, "getParentViaParentStatic", "()Ljava/lang/String;")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getParentViaParentStatic(), result);
    }

    @org.junit.Test
    public void testChildViaChildStatic() throws IOException {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(
                                classInput, "getChildViaChildStatic", "()Ljava/lang/String;")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getChildViaChildStatic(), result);
    }

    @org.junit.Test
    public void testFieldFromInterface() throws IOException {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(
                                classInput, "getInterfaceInheritedField", "()Ljava/lang/Object;")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getInterfaceInheritedField(), result);
    }

    @org.junit.Test
    public void testFieldFromInterfaceAncestor() throws IOException {
        byte[] classInput = buildClass(FieldTestTarget.class);
        Object result =
                new MethodBodyEvaluator(
                                classInput,
                                "getInterfaceAncestorInheritedField",
                                "()Ljava/lang/Object;")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(FieldTestTarget.getInterfaceAncestorInheritedField(), result);
    }
}
