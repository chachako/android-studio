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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.idea.protobuf.ByteString;
import java.util.Collection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LiveEditInstrumentationTest extends LiveEditTestBase {
    @Parameterized.Parameters
    public static Collection<String> artFlags() {
        return ALL_ART_FLAGS;
    }

    public LiveEditInstrumentationTest(String artFlag) {
        super(artFlag);
    }

    @Test
    public void testTransformSucceeds() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        Deploy.LiveEditClass clazz =
                Deploy.LiveEditClass.newBuilder()
                        .setClassName("app/StubTarget")
                        .setClassData(
                                ByteString.copyFrom(
                                        getClassBytes(
                                                "app/StubTarget.class",
                                                CompileClassLocation.JAVA_ORIGINAL_LOCATION)))
                        .build();
        Deploy.LiveEditRequest request =
                Deploy.LiveEditRequest.newBuilder()
                        .addTargetClasses(clazz)
                        .setPackageName(PACKAGE)
                        .build();

        Deploy.AgentLiveEditResponse response = sendUpdateRequest(request);
        Assert.assertEquals(Deploy.AgentLiveEditResponse.Status.OK, response.getStatus());
    }

    @Test
    public void testAbstractTransformSucceeds() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        Deploy.LiveEditClass clazz =
                Deploy.LiveEditClass.newBuilder()
                        .setClassName("app/AbstractStubTarget")
                        .setClassData(
                                ByteString.copyFrom(
                                        getClassBytes(
                                                "app/AbstractStubTarget.class",
                                                CompileClassLocation.JAVA_ORIGINAL_LOCATION)))
                        .build();
        Deploy.LiveEditRequest request =
                Deploy.LiveEditRequest.newBuilder()
                        .addTargetClasses(clazz)
                        .setPackageName(PACKAGE)
                        .build();

        Deploy.AgentLiveEditResponse response = sendUpdateRequest(request);
        Assert.assertEquals(Deploy.AgentLiveEditResponse.Status.OK, response.getStatus());
    }
}
