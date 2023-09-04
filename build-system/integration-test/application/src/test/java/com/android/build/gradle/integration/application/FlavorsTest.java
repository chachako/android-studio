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
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtilsV2;
import com.android.build.gradle.integration.common.utils.SourceProviderHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.VariantHelper;
import com.android.builder.core.ComponentType;
import com.android.builder.model.v2.dsl.BaseConfig;
import com.android.builder.model.v2.dsl.BuildType;
import com.android.builder.model.v2.dsl.ProductFlavor;
import com.android.builder.model.v2.ide.BasicArtifact;
import com.android.builder.model.v2.ide.BasicVariant;
import com.android.builder.model.v2.ide.ProjectType;
import com.android.builder.model.v2.ide.Variant;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for flavors. */
public class FlavorsTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("flavors").create();

    @Test
    public void checkFlavorsInModel() throws Exception {
        project.execute("clean", "assembleDebug");

        ModelContainerV2 container = project.modelV2().fetchModels().getContainer();
        ModelContainerV2.ModelInfo modelInfo = container.getProject();

        File projectDir = project.getProjectDir();

        assertThat(modelInfo.getBasicAndroidProject().getProjectType())
                .isEqualTo(ProjectType.APPLICATION);
        assertThat(modelInfo.getAndroidDsl().getFlavorDimensions())
                .containsExactly("group1", "group2");

        new SourceProviderHelper(
                        project.getName(),
                        projectDir,
                        "main",
                        modelInfo.getBasicAndroidProject().getMainSourceSet().getSourceProvider())
                .testV2();

        new SourceProviderHelper(
                        project.getName(),
                        projectDir,
                        ComponentType.ANDROID_TEST_PREFIX,
                        modelInfo
                                .getBasicAndroidProject()
                                .getMainSourceSet()
                                .getAndroidTestSourceProvider())
                .testV2();

        List<BuildType> buildTypes1 = modelInfo.getAndroidDsl().getBuildTypes();

        assertThat(buildTypes1.stream().map(BaseConfig::getName).collect(Collectors.toList()))
                .containsExactly("debug", "release");

        Map<String, String> expected =
                ImmutableMap.of("f1", "group1", "f2", "group1", "fa", "group2", "fb", "group2");

        List<ProductFlavor> productFlavors = modelInfo.getAndroidDsl().getProductFlavors();

        assertThat(productFlavors.stream().map(BaseConfig::getName).collect(Collectors.toList()))
                .containsExactlyElementsIn(expected.keySet());

        for (ProductFlavor flavor : productFlavors) {
            assertThat(flavor.getDimension())
                    .named("Flavor " + flavor.getName())
                    .isEqualTo(expected.get(flavor.getName()));
        }

        Collection<Variant> variants = modelInfo.getAndroidProject().getVariants();
        assertEquals("Variant Count", 8, variants.size());

        BasicVariant f1FaDebug =
                AndroidProjectUtilsV2.getVariantByName(
                        modelInfo.getBasicAndroidProject(), "f1FaDebug");
        assertThat(f1FaDebug.getProductFlavors()).containsExactly("f1", "fa");

        new VariantHelper(
                        AndroidProjectUtilsV2.getVariantByName(
                                modelInfo.getAndroidProject(), "f1FaDebug"),
                        projectDir,
                        "/f1Fa/debug/flavors-f1-fa-debug.apk")
                .test();
    }

    @Test
    public void compoundSourceSetsAreInModel() {
        project.execute("clean", "assembleDebug");

        ModelContainerV2.ModelInfo modelInfo =
                project.modelV2().fetchModels().getContainer().getProject();
        Collection<BasicVariant> variants = modelInfo.getBasicAndroidProject().getVariants();

        for (BasicVariant variant : variants) {
            BasicArtifact unitTestArtifact = variant.getUnitTestArtifact();
            if (unitTestArtifact != null) {
                assertThat(unitTestArtifact.getMultiFlavorSourceProvider())
                        .named("MultiFlavor SourceProvider for " + variant.getName())
                        .isNotNull();
                assertThat(unitTestArtifact.getVariantSourceProvider())
                        .named("Variant SourceProvider for " + variant.getName())
                        .isNotNull();
            }

            BasicArtifact androidTestArtifact = variant.getAndroidTestArtifact();
            if (androidTestArtifact != null) {
                assertThat(androidTestArtifact.getMultiFlavorSourceProvider())
                        .named("MultiFlavor SourceProvider for " + variant.getName())
                        .isNotNull();
                assertThat(androidTestArtifact.getVariantSourceProvider())
                        .named("Variant SourceProvider for " + variant.getName())
                        .isNotNull();
            }
        }
    }

    @Test
    public void checkFlavorSanitization() throws IOException, InterruptedException {
        TestFileUtils.searchAndReplace(project.getBuildFile(), "group1", "foo.bar");

        TestFileUtils.searchAndReplace(
                project.file("src/main/java/com/android/tests/flavors/MainActivity.java"),
                "FLAVOR_group1",
                "FLAVOR_foo_bar");

        project.executor().run("assembleDebug");

        assertThat(
                        project.file(
                                "build/generated/source/buildConfig/f1Fa/debug/com/android/tests/flavors/BuildConfig.java"))
                .contains("// From flavor dimension foo.bar");
        assertThat(
                        project.file(
                                "build/generated/source/buildConfig/f1Fa/debug/com/android/tests/flavors/BuildConfig.java"))
                .doesNotContain("// From flavor dimension group2");
    }
}
