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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.utils.SourceProviderHelper;
import com.android.builder.core.ComponentType;
import com.android.builder.model.v2.ide.ProjectType;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for migrated. */
public class MigratedTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("migrated").create();

    @Test
    public void checkModelReflectsMigratedSourceProviders() throws IOException {
        project.execute("clean", "assembleDebug");

        ModelContainerV2 container = project.modelV2().fetchModels().getContainer();
        ModelContainerV2.ModelInfo modelInfo = container.getProject();
        File projectDir = project.getProjectDir();

        assertThat(modelInfo.getBasicAndroidProject().getProjectType())
                .isEqualTo(ProjectType.APPLICATION);

        new SourceProviderHelper(
                        project.getName(),
                        projectDir,
                        "main",
                        modelInfo.getBasicAndroidProject().getMainSourceSet().getSourceProvider())
                .setJavaDir("src")
                .setKotlinDirs()
                .setResourcesDir("src")
                .setAidlDir("src")
                .setRenderscriptDir("src")
                .setResDir("res")
                .setAssetsDir("assets")
                .setManifestFile("AndroidManifest.xml")
                .testV2();

        new SourceProviderHelper(
                        project.getName(),
                        projectDir,
                        ComponentType.ANDROID_TEST_PREFIX,
                        modelInfo
                                .getBasicAndroidProject()
                                .getMainSourceSet()
                                .getAndroidTestSourceProvider())
                .setJavaDir("tests/java")
                .setKotlinDirs("tests/kotlin", "tests/java")
                .setResourcesDir("tests/resources")
                .setAidlDir("tests/aidl")
                .setJniDir("tests/jniLibs")
                .setRenderscriptDir("tests/rs")
                .setBaselineProfileDir("tests/baselineProfiles")
                .setResDir("tests/res")
                .setAssetsDir("tests/assets")
                .setManifestFile("tests/AndroidManifest.xml")
                .testV2();
    }
}
