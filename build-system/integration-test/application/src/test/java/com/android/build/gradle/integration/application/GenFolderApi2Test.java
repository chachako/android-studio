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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.v2.ide.AndroidArtifact;
import com.android.builder.model.v2.ide.JavaArtifact;
import com.android.builder.model.v2.ide.Variant;
import com.android.builder.model.v2.models.AndroidProject;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

/** Tests for addJavaSourceFoldersToModel Variant API. */
public class GenFolderApi2Test {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("genFolderApi2").create();

    @Test
    public void checkJavaFolderInModel() {
        AndroidProject androidProject =
                project.modelV2().fetchModels().getContainer().getProject().getAndroidProject();

        File projectDir = project.getProjectDir();
        File buildDir = new File(projectDir, "build");

        for (Variant variant : androidProject.getVariants()) {
            AndroidArtifact mainInfo = variant.getMainArtifact();
            assertNotNull(
                    "Null-check on mainArtifactInfo for " + variant.getDisplayName(), mainInfo);
            Collection<File> genSourceFolder = mainInfo.getGeneratedSourceFolders();

            // We're looking for a custom folder.
            String sourceFolderStart =
                    new File(buildDir, "customCode").getAbsolutePath() + File.separatorChar;

            assertThat(
                            genSourceFolder.stream()
                                    .anyMatch(
                                            it ->
                                                    it.getAbsolutePath()
                                                            .startsWith(sourceFolderStart)))
                    .isTrue();

            JavaArtifact unitTestArtifact = variant.getUnitTestArtifact();
            List<File> sortedFolders =
                    unitTestArtifact
                            .getGeneratedSourceFolders()
                            .stream()
                            .sorted()
                            .collect(Collectors.toList());
            assertThat(sortedFolders).hasSize(3);
            assertThat(sortedFolders.get(0).getAbsolutePath()).startsWith(sourceFolderStart);
            assertThat(sortedFolders.get(0).getAbsolutePath()).endsWith("-1");
            assertThat(sortedFolders.get(1).getAbsolutePath()).startsWith(sourceFolderStart);
            assertThat(sortedFolders.get(1).getAbsolutePath()).endsWith("-2");
        }
    }

    @Test
    public void backwardsCompatible() throws Exception {
        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        assertThat(TestFileUtils.sha1NormalizedLineEndings(project.file("build.gradle")))
                .isEqualTo("a410be0e51240b2355feaa723973239115f8db3e");
    }
}
