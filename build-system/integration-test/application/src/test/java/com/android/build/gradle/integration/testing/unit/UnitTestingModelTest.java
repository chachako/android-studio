/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.testing.unit;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.ArtifactTypeUtil;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.v2.dsl.ProductFlavor;
import com.android.builder.model.v2.ide.JavaArtifact;
import com.android.builder.model.v2.ide.SourceProvider;
import com.android.builder.model.v2.ide.SourceSetContainer;
import com.android.builder.model.v2.ide.Variant;
import com.android.builder.model.v2.models.BasicAndroidProject;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.truth.Truth;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.android.SdkConstants.FN_R_CLASS_JAR;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

/** Tests for the unit-tests related parts of the builder model. */
public class UnitTestingModelTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("unitTestingComplexProject").create();

    @Test
    public void unitTestingArtifactsAreIncludedInTheModel() throws Exception {
        // Build the project, so we can verify paths in the model exist.
        project.executor().run("test");

        ModelContainerV2.ModelInfo model = project.modelV2()
                .fetchModels()
                .getContainer()
                .getProject(":app", ":");

        for (Variant variant : model.getAndroidProject().getVariants()) {
            List<File> expectedClassesFolders = new ArrayList<>();
            expectedClassesFolders.add(
                    new File(
                            ArtifactTypeUtil.getOutputDir(
                                    InternalArtifactType
                                            .COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
                                            .INSTANCE,
                                    project.getSubproject("app").getBuildDir()),
                            variant.getName() + "/" + FN_R_CLASS_JAR)
            );
            expectedClassesFolders.add(project.file("app/build/tmp/kotlin-classes/"
                    + variant.getName()));
            if (variant.getName().equals("release")) {
                expectedClassesFolders.add(
                        project.file("app/build/kotlinToolingMetadata"));
                expectedClassesFolders.add(
                        project.file("app/build/intermediates/javac/release/classes"));
            } else {
                expectedClassesFolders.add(
                        project.file("app/build/intermediates/javac/debug/classes"));
            }
            Truth.assertThat(variant.getMainArtifact().getClassesFolders())
                    .containsExactlyElementsIn(expectedClassesFolders);

            JavaArtifact unitTestArtifact = variant.getUnitTestArtifact();
            Truth.assertThat(unitTestArtifact.getAssembleTaskName()).contains("UnitTest");
            Truth.assertThat(unitTestArtifact.getAssembleTaskName())
                    .contains(StringHelper.usLocaleCapitalize(variant.getName()));
            Truth.assertThat(unitTestArtifact.getCompileTaskName()).contains("UnitTest");
            Truth.assertThat(unitTestArtifact.getCompileTaskName())
                    .contains(StringHelper.usLocaleCapitalize(variant.getName()));

            assertThat(unitTestArtifact.getClassesFolders())
                    .isNotEqualTo(variant.getMainArtifact().getClassesFolders());

            assertThat(unitTestArtifact.getClassesFolders())
                    .containsExactly(
                            project.file(
                                    "app/build/tmp/kotlin-classes/"
                                            + variant.getName()
                                            + "UnitTest"),
                            project.file(
                                    "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/"
                                            + variant.getName()
                                            + "/R.jar"),
                            project.file("app/build/intermediates/javac/"
                                    + variant.getName()
                                    + "UnitTest/classes")
                    );
        }

        SourceProvider sourceProvider = model.getBasicAndroidProject()
                .getMainSourceSet()
                .getUnitTestSourceProvider();

        Truth.assertThat(sourceProvider.getJavaDirectories()).hasSize(1);
        Truth.assertThat(sourceProvider.getJavaDirectories().iterator().next().getAbsolutePath())
                .endsWith(FileUtils.join("test", "java"));
        Truth.assertThat(sourceProvider.getKotlinDirectories()).hasSize(2);
        Truth.assertThat(sourceProvider.getKotlinDirectories())
                .containsExactly(
                        project.file("app/src/test/java"), project.file("app/src/test/kotlin"));
    }

    @Test
    public void flavors() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "android {\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors { paid; free }\n"
                        + "}");
        ModelContainerV2.ModelInfo model =
                project.modelV2().fetchModels().getContainer().getProject(":app", ":");

        assertThat(model.getAndroidDsl().getProductFlavors()).hasSize(2);

        Collection<SourceSetContainer> productFlavorSourceSets = model.getBasicAndroidProject()
                .getProductFlavorSourceSets();
        assertThat(productFlavorSourceSets).hasSize(2);

        for (SourceSetContainer flavor : productFlavorSourceSets) {
            SourceProvider sourceProvider = flavor.getUnitTestSourceProvider();
            assertThat(sourceProvider.getJavaDirectories()).hasSize(1);
            String flavorDir = sourceProvider.getName();
            assertThat(sourceProvider.getJavaDirectories().iterator().next().getAbsolutePath())
                    .endsWith(flavorDir + File.separator + "java");
            Truth.assertThat(sourceProvider.getKotlinDirectories())
                    .containsExactly(
                            project.file("app/src/" + flavorDir + "/java"),
                            project.file("app/src/" + flavorDir + "/kotlin"));
        }
    }
}
