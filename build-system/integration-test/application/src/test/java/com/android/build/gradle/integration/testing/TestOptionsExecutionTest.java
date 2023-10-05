/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.testing;

import static org.junit.Assert.assertEquals;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtilsV2;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.v2.ide.TestInfo;
import com.android.builder.model.v2.ide.Variant;
import com.android.builder.model.v2.models.AndroidProject;
import org.junit.Rule;
import org.junit.Test;

public class TestOptionsExecutionTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void returnsAto() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android { testOptions.execution \"android_test_orchestrator\" }");

        check(TestInfo.Execution.ANDROID_TEST_ORCHESTRATOR);
    }

    @Test
    public void returnsAto_androidx() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android { testOptions.execution \"androidx_test_orchestrator\" }");

        check(TestInfo.Execution.ANDROIDX_TEST_ORCHESTRATOR);
    }

    @Test
    public void returnsHostByDefault() {
        check(TestInfo.Execution.HOST);
    }

    private void check(TestInfo.Execution androidTestOrchestrator) {
        AndroidProject androidProject =
                project.modelV2().fetchModels().getContainer().getProject().getAndroidProject();

        Variant debugVariant = AndroidProjectUtilsV2.getVariantByName(androidProject, "debug");

        TestInfo.Execution execution =
                debugVariant.getAndroidTestArtifact().getTestInfo().getExecution();

        assertEquals(execution, androidTestOrchestrator);
    }
}
