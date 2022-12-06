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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.idea.protobuf.ByteString;
import java.io.IOException;
import java.util.Collection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LiveEditAccessorTest extends LiveEditTestBase {
    @Parameterized.Parameters
    public static Collection<String> artFlags() {
        return ALL_ART_FLAGS;
    }

    public LiveEditAccessorTest(String artFlag) {
        super(artFlag);
    }

    @Test
    public void testAccessPrivateVariableFromLambda() throws IOException {
        android.loadDex(DEX_LOCATION + ":" + LIVE_EDIT_LAMBDA_DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "invokeLiveEditUseAccessor");
        Assert.assertTrue(android.waitForInput("UseAccessor: 1", RETURN_VALUE_TIMEOUT));

        Deploy.LiveEditClass clazz =
                Deploy.LiveEditClass.newBuilder()
                        .setClassName("pkg/UseAccessor")
                        .setClassData(
                                ByteString.copyFrom(
                                        getClassBytes(
                                                "pkg/UseAccessor.class",
                                                CompileClassLocation.KOTLIN_SWAPPED_LOCATION)))
                        .build();

        Deploy.LiveEditClass proxy =
                Deploy.LiveEditClass.newBuilder()
                        .setClassName("pkg/UseAccessor$accessX$1")
                        .setClassData(
                                ByteString.copyFrom(
                                        getClassBytes(
                                                "pkg/UseAccessor$accessX$1.class",
                                                CompileClassLocation.KOTLIN_SWAPPED_LOCATION)))
                        .build();

        Deploy.LiveEditRequest request =
                Deploy.LiveEditRequest.newBuilder()
                        .addTargetClasses(clazz)
                        .addSupportClasses(proxy)
                        .setPackageName(PACKAGE)
                        .build();

        Deploy.AgentLiveEditResponse response = sendUpdateRequest(request);
        Assert.assertEquals(Deploy.AgentLiveEditResponse.Status.OK, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "invokeLiveEditUseAccessor");
        Assert.assertTrue(android.waitForInput("UseAccessor: 2", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testAddNewAccessPrivateVariableFromLambda() throws IOException {
        android.loadDex(DEX_LOCATION + ":" + LIVE_EDIT_LAMBDA_DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "invokeLiveEditAddAccessor");
        Assert.assertTrue(android.waitForInput("AddAccessor: 10", RETURN_VALUE_TIMEOUT));

        Deploy.LiveEditClass clazz =
                Deploy.LiveEditClass.newBuilder()
                        .setClassName("pkg/AddAccessor")
                        .setClassData(
                                ByteString.copyFrom(
                                        getClassBytes(
                                                "pkg/AddAccessor.class",
                                                CompileClassLocation.KOTLIN_SWAPPED_LOCATION)))
                        .build();

        Deploy.LiveEditClass proxy =
                Deploy.LiveEditClass.newBuilder()
                        .setClassName("pkg/AddAccessor$accessX$1")
                        .setClassData(
                                ByteString.copyFrom(
                                        getClassBytes(
                                                "pkg/AddAccessor$accessX$1.class",
                                                CompileClassLocation.KOTLIN_SWAPPED_LOCATION)))
                        .build();

        Deploy.LiveEditRequest request =
                Deploy.LiveEditRequest.newBuilder()
                        .addTargetClasses(clazz)
                        .addSupportClasses(proxy)
                        .setPackageName(PACKAGE)
                        .build();

        Deploy.AgentLiveEditResponse response = sendUpdateRequest(request);
        Assert.assertEquals(Deploy.AgentLiveEditResponse.Status.OK, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "invokeLiveEditAddAccessor");
        Assert.assertTrue(android.waitForInput("AddAccessor: 20", RETURN_VALUE_TIMEOUT));
    }
}
