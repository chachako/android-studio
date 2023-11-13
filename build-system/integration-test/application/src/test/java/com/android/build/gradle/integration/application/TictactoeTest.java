/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.builder.model.v2.ide.GraphItem;
import com.android.builder.model.v2.ide.ProjectType;
import java.util.List;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for tictactoe. */
public class TictactoeTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("tictactoe").create();

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void testModel() {
        project.execute("clean", "assembleDebug");

        ModelContainerV2.ModelInfo libModel =
                project.modelV2().fetchModels().getContainer().getProject(":lib");
        assertNotNull(libModel);
        assertEquals(libModel.getBasicAndroidProject().getProjectType(), ProjectType.LIBRARY);

        ModelContainerV2.ModelInfo appModel =
                project.modelV2().fetchModels("debug", null).getContainer().getProject(":app");
        assertNotNull(appModel);
        List<GraphItem> compileDependencies =
                appModel.getVariantDependencies().getMainArtifact().getCompileDependencies();

        assertThat(compileDependencies).hasSize(1);
        assertThat(compileDependencies.get(0).getKey()).contains(":lib");
    }
}
