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

import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtilsV2;
import com.android.build.gradle.integration.common.utils.ModelContainerUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.v2.ide.AndroidArtifact;
import com.android.builder.model.v2.ide.Variant;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.collect.MoreCollectors;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Tests for generated source registration APIs.
 *
 * <p>Includes the following APIs:
 *
 * <ul>
 *   <li>registerJavaGeneratingTask
 *   <li>registerResGeneratingTask
 *   <li>registerGeneratedResFolders
 * </ul>
 */
public class GenFolderApiTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("genFolderApi").create();

    private static List<String> ideSetupTasks;

    private static ModelContainerV2 container;

    @BeforeClass
    public static void setUp() throws Exception {
        project.executor()
                .withArgument("-P" + "inject_enable_generate_values_res=true")
                .run("assembleDebug");
        container =
                project.modelV2()
                        .withArgument("-P" + "inject_enable_generate_values_res=true")
                        .fetchModels()
                        .getContainer();

        ideSetupTasks = ModelContainerUtils.getDebugGenerateSourcesCommands(container);
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        container = null;
    }

    @Test
    public void checkTheCustomJavaGenerationTaskRan() throws Exception {
        try (Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG)) {
            assertThat(apk).containsClass("Lcom/custom/Foo;");
            assertThat(apk).containsClass("Lcom/custom/Bar;");
        }
    }

    @Test
    public void checkTheCustomResGenerationTaskRan() throws Exception {
        try (Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG)) {
            assertThat(apk).contains("res/xml/generated.xml");
            assertThat(apk)
                    .hasClass("Lcom/android/tests/basic/R$string;")
                    .that()
                    .hasField("generated_string");
        }
    }

    /** Regression test for b/120750247 */
    @Test
    public void checkCustomGenerationRunAtSync() throws Exception {
        project.executor()
                .withArgument("-P" + "inject_enable_generate_values_res=true")
                .run("clean");
        project.executor()
                .withArgument("-P" + "inject_enable_generate_values_res=true")
                .run(ideSetupTasks);

        AndroidArtifact mainArtifact =
                AndroidProjectUtilsV2.getVariantByName(
                                container.getProject().getAndroidProject(), "debug")
                        .getMainArtifact();

        File customCode =
                mainArtifact.getGeneratedSourceFolders().stream()
                        .filter(it -> it.getAbsolutePath().startsWith(getSourceFolderStart()))
                        .collect(MoreCollectors.onlyElement());
        assertThat(customCode).isDirectory();

        File customResources =
                mainArtifact.getGeneratedResourceFolders().stream()
                        .filter(it -> it.getAbsolutePath().startsWith(getCustomResPath()))
                        .collect(MoreCollectors.onlyElement());
        assertThat(customResources).isDirectory();

        File customResources2 =
                mainArtifact.getGeneratedResourceFolders().stream()
                        .filter(it -> it.getAbsolutePath().startsWith(getCustomRes2Path()))
                        .collect(MoreCollectors.onlyElement());
        assertThat(customResources2).isDirectory();
    }


    @Test
    public void checkAddingAndRemovingGeneratingTasks() throws Exception {
        project.executor()
                .withArgument("-P" + "inject_enable_generate_values_res=false")
                .run("assembleDebug");

        try (Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG)) {
            assertThat(apk)
                    .hasClass("Lcom/android/tests/basic/R$string;")
                    .that()
                    .doesNotHaveField("generated_string");
        }

        project.executor()
                .withArgument("-P" + "inject_enable_generate_values_res=true")
                .run("assembleDebug");
        try (Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG)) {
            assertThat(apk)
                    .hasClass("Lcom/android/tests/basic/R$string;")
                    .that()
                    .hasField("generated_string");
        }
    }

    @Test
    public void checkJavaFolderInModel() {
        for (Variant variant : container.getProject().getAndroidProject().getVariants()) {

            AndroidArtifact mainInfo = variant.getMainArtifact();
            assertNotNull(
                    "Null-check on mainArtifactInfo for " + variant.getDisplayName(), mainInfo);

            Collection<File> genSourceFolder = mainInfo.getGeneratedSourceFolders();

            // We're looking for a custom folder
            String sourceFolderStart = getSourceFolderStart();
            boolean found = false;
            for (File f : genSourceFolder) {
                if (f.getAbsolutePath().startsWith(sourceFolderStart)) {
                    found = true;
                    break;
                }
            }

            assertTrue("custom generated source folder check", found);
        }
    }

    @Test
    public void checkResFolderInModel() {
        for (Variant variant : container.getProject().getAndroidProject().getVariants()) {
            com.android.builder.model.v2.ide.AndroidArtifact mainInfo = variant.getMainArtifact();
            assertNotNull(
                    "Null-check on mainArtifactInfo for " + variant.getDisplayName(), mainInfo);

            List<String> genResFolders =
                    mainInfo.getGeneratedResourceFolders()
                            .stream()
                            .map(File::getAbsolutePath)
                            .collect(Collectors.toList());

            assertThat(genResFolders).containsNoDuplicates();

            assertThat(genResFolders)
                    .containsAllOf(
                            getCustomResPath() + variant.getName(),
                            getCustomRes2Path() + variant.getName());
        }
    }

    @Test
    public void backwardsCompatible() throws Exception {
        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        assertThat(TestFileUtils.sha1NormalizedLineEndings(project.file("build.gradle")))
                .isEqualTo("384acd749b7c400845fb96eace7b0def85cade2e");
    }

    @NonNull
    private String getCustomResPath() {
        return FileUtils.join(project.getProjectDir().getAbsolutePath(), "build", "customRes")
                + File.separatorChar;
    }

    @NonNull
    private String getCustomRes2Path() {
        return FileUtils.join(project.getProjectDir().getAbsolutePath(), "build", "customRes2")
                + File.separatorChar;
    }

    @NonNull
    private String getSourceFolderStart() {
        return FileUtils.join(project.getProjectDir().getAbsolutePath(), "build", "customCode")
                + File.separatorChar;
    }
}
