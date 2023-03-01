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
public class LiveEditRecomposeCrashTest extends LiveEditTestBase {

    @Parameterized.Parameters
    public static Collection<String> artFlags() {
        return ALL_ART_FLAGS;
    }

    public LiveEditRecomposeCrashTest(String artFlag) {
        super(artFlag);
    }

    @Test
    public void testVersionChecksPass() throws IOException {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);
        Deploy.LiveEditClass clazz =
                Deploy.LiveEditClass.newBuilder()
                        .setClassName("pkg/LiveEditRecomposeCrashKt")
                        .setClassData(
                                ByteString.copyFrom(
                                        getClassBytes(
                                                "pkg/LiveEditRecomposeCrashKt.class",
                                                CompileClassLocation.KOTLIN_SWAPPED_LOCATION)))
                        .build();
        Deploy.LiveEditRequest request =
                Deploy.LiveEditRequest.newBuilder()
                        .addTargetClasses(clazz)
                        .setPackageName(PACKAGE)
                        .setComposable(true)
                        .addGroupIds(0x1111)
                        .build();
        Deploy.AgentLiveEditResponse response = sendUpdateRequest(request);
        Assert.assertEquals(Deploy.AgentLiveEditResponse.Status.OK, response.getStatus());
        // First time we Live Edit LiveEditRecomposeKt. The class get primed and we trigger
        // a full loadStateAndCompose.
        Assert.assertTrue(android.waitForInput("loadStateAndCompose", RETURN_VALUE_TIMEOUT));
        Deploy.AgentComposeStatusResponse composeStatusRes = sendPollRequest();
        Assert.assertEquals(
                Deploy.AgentComposeStatusResponse.Status.OK, composeStatusRes.getStatus());
        Assert.assertEquals(0, composeStatusRes.getExceptionsCount());
        composeStatusRes = sendPollRequest();
        Assert.assertEquals(
                Deploy.AgentComposeStatusResponse.Status.OK, composeStatusRes.getStatus());
        Assert.assertEquals(0, composeStatusRes.getExceptionsCount());
        clazz =
                Deploy.LiveEditClass.newBuilder()
                        .setClassName("pkg/LiveEditRecomposeCrashKt")
                        .setClassData(
                                ByteString.copyFrom(
                                        getClassBytes(
                                                "pkg/LiveEditRecomposeCrashKt.class",
                                                CompileClassLocation.KOTLIN_SWAPPED_LOCATION)))
                        .build();
        request =
                Deploy.LiveEditRequest.newBuilder()
                        .addTargetClasses(clazz)
                        .setPackageName(PACKAGE)
                        .setComposable(true)
                        .addGroupIds(0x1112)
                        .build();
        response = sendUpdateRequest(request);
        Assert.assertEquals(Deploy.AgentLiveEditResponse.Status.OK, response.getStatus());
        // We don't need to call anything. LiveEditRecompose should be triggered automatically
        // by the (mocked) Compose runtime when it needs to recompose.
        Assert.assertTrue(
                android.waitForInput("invalidateGroupsWithKey(0x1112)", RETURN_VALUE_TIMEOUT));
        Assert.assertTrue(
                android.waitForInput("pk.LiveEditRecomposeCrash.changed", RETURN_VALUE_TIMEOUT));
        composeStatusRes = sendPollRequest();
        Assert.assertEquals(
                Deploy.AgentComposeStatusResponse.Status.OK, composeStatusRes.getStatus());
        Assert.assertEquals(1, composeStatusRes.getExceptionsCount());
        Assert.assertEquals(
                "java.lang.RuntimeException",
                composeStatusRes.getExceptions(0).getExceptionClassName());
        Assert.assertEquals(
                "java.lang.RuntimeException: LiveEditRecomposeCrash() has crashed",
                composeStatusRes.getExceptions(0).getMessage());
        Assert.assertTrue(composeStatusRes.getExceptions(0).getRecoverable());
        composeStatusRes = sendPollRequest();
        Assert.assertEquals(
                Deploy.AgentComposeStatusResponse.Status.OK, composeStatusRes.getStatus());
        Assert.assertEquals(0, composeStatusRes.getExceptionsCount());
    }
}
