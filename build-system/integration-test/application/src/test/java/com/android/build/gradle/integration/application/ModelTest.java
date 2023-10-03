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

package com.android.build.gradle.integration.application;

import static com.android.SdkConstants.FN_R_CLASS_JAR;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtilsV2;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.ArtifactTypeUtil;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidGradlePluginProjectFlags.BooleanFlag;
import com.android.builder.model.v2.ide.AndroidArtifact;
import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags;
import com.android.builder.model.v2.ide.JavaArtifact;
import com.android.builder.model.v2.ide.UnresolvedDependency;
import com.android.builder.model.v2.ide.Variant;
import com.android.builder.model.v2.models.AndroidProject;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;
import java.io.File;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;

/** General Model tests */
public class ModelTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void unresolvedFixedDependencies() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\ndependencies {\n    api 'foo:bar:1.2.3'\n}\n");
        ModelContainerV2.ModelInfo modelInfo = project.modelV2()
                .ignoreSyncIssues()
                .fetchModels("debug", null)
                .getContainer()
                .getProject();
        List<UnresolvedDependency> unresolvedDependencies = modelInfo
                .getVariantDependencies()
                .getMainArtifact()
                .getUnresolvedDependencies();

        assertThat(unresolvedDependencies).hasSize(1);
        UnresolvedDependency dependency = Iterables.getOnlyElement(unresolvedDependencies);
        assertThat(dependency.getName()).isEqualTo("foo:bar:1.2.3");
        assertThat(dependency.getCause()).isNull();
    }

    @Test
    public void unresolvedDynamicDependencies() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\n" + "dependencies {\n" + "    api 'foo:bar:+'\n" + "}");

        ModelContainerV2.ModelInfo modelInfo = project.modelV2()
                .ignoreSyncIssues()
                .fetchModels("debug", null)
                .getContainer()
                .getProject();
        List<UnresolvedDependency> unresolvedDependencies = modelInfo
                .getVariantDependencies()
                .getMainArtifact()
                .getUnresolvedDependencies();

        assertThat(unresolvedDependencies).hasSize(1);
        UnresolvedDependency dependency = Iterables.getOnlyElement(unresolvedDependencies);
        assertThat(dependency.getName()).isEqualTo("foo:bar:+");
        assertThat(dependency.getCause()).isNull();
    }

    /** Regression test for bug 133326990. */
    @Test
    public void checkRJarIsIncludedInModel() {
        AndroidProject androidProject = project.modelV2()
                .fetchModels()
                .getContainer()
                .getProject()
                .getAndroidProject();

        for (Variant variant : androidProject.getVariants()) {
            File rJar =
                    new File(
                            ArtifactTypeUtil.getOutputDir(
                                    COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR.INSTANCE,
                                    project.getBuildDir()),
                            variant.getName() + "/" + FN_R_CLASS_JAR);
            assertThat(variant.getMainArtifact().getClassesFolders()).contains(rJar);
        }
    }

    /** Sanity test that makes sure no unexpected directories end up in the model. */
    @Test
    public void generatedSources() {
        AndroidProject projectModel = project.modelV2()
                .fetchModels()
                .getContainer()
                .getProject()
                .getAndroidProject();

        Truth.assertThat(projectModel).isNotNull();
        Variant debugVariant =
                AndroidProjectUtilsV2.getVariantByName(projectModel, "debug");

        AndroidArtifact debugArtifact = debugVariant.getMainArtifact();
        assertThat(debugArtifact.getGeneratedSourceFolders())
                .containsExactly(project.file("build/generated/ap_generated_sources/debug/out"));
        assertThat(debugArtifact.getGeneratedResourceFolders())
                .containsExactly(project.file("build/generated/res/resValues/debug"));

        AndroidArtifact androidTestArtifact = debugVariant.getAndroidTestArtifact();
        assertThat(androidTestArtifact.getGeneratedSourceFolders())
                .containsExactly(
                        project.file("build/generated/ap_generated_sources/debugAndroidTest/out"));
        assertThat(androidTestArtifact.getGeneratedResourceFolders())
                .containsExactly(project.file("build/generated/res/resValues/androidTest/debug"));

        JavaArtifact unitTestArtifact = debugVariant.getUnitTestArtifact();
        assertThat(unitTestArtifact.getGeneratedSourceFolders())
                .containsExactly(
                        project.file("build/generated/ap_generated_sources/debugUnitTest/out"));
    }

    @Test
    public void returnsInstrumentedTestTaskName() {
        AndroidProject projectModel = project.modelV2()
                .fetchModels()
                .getContainer()
                .getProject()
                .getAndroidProject();

        assertThat(projectModel).isNotNull();
        Variant debugVariant =
                AndroidProjectUtilsV2.getVariantByName(projectModel, "debug");

        AndroidArtifact androidTestArtifact = debugVariant.getAndroidTestArtifact();
        assertThat(androidTestArtifact.getTestInfo().getInstrumentedTestTaskName())
                .isEqualTo("connectedDebugAndroidTest");
    }

    @Test
    public void checkFlagsNamespacedRClassOff() throws Exception {
        AndroidProject androidProject = project.modelV2()
                .with(BooleanOption.NON_TRANSITIVE_R_CLASS, false)
                .with(BooleanOption.USE_NON_FINAL_RES_IDS, false)
                .fetchModels()
                .getContainer()
                .getProject()
                .getAndroidProject();

        AndroidGradlePluginProjectFlags flags = androidProject.getFlags();
        assertThat(flags).isNotNull();
        assertThat(flags.getFlagValue(BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS.name())).isTrue();
        assertThat(flags.getFlagValue(BooleanFlag.TEST_R_CLASS_CONSTANT_IDS.name())).isTrue();
        assertThat(flags.getFlagValue(BooleanFlag.TRANSITIVE_R_CLASS.name())).isTrue();
    }

    @Test
    public void checkFlagsNamespacedRClassOn() throws Exception {
        AndroidProject androidProject = project.modelV2()
                .with(BooleanOption.NON_TRANSITIVE_R_CLASS, true)
                .with(BooleanOption.USE_NON_FINAL_RES_IDS, true)
                .fetchModels()
                .getContainer()
                .getProject()
                .getAndroidProject();

        AndroidGradlePluginProjectFlags flags = androidProject.getFlags();
        assertThat(flags).isNotNull();
        assertThat(flags.getFlagValue(BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS.name())).isFalse();
        assertThat(flags.getFlagValue(BooleanFlag.TEST_R_CLASS_CONSTANT_IDS.name())).isFalse();
        assertThat(flags.getFlagValue(BooleanFlag.TRANSITIVE_R_CLASS.name())).isFalse();
    }
}
