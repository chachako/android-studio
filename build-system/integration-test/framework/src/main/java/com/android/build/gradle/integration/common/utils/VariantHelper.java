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

package com.android.build.gradle.integration.common.utils;

import static com.google.common.truth.Truth.assertThat;

import com.android.builder.model.v2.ide.Variant;
import java.io.File;
import java.util.Collection;
import java.util.List;

public class VariantHelper {
    private final File projectDir;
    private final String outputFileName;
    private final Variant variant;

    public VariantHelper(
            Variant variant,
            File projectDir,
            String outputFileName) {
        this.variant = variant;
        this.projectDir = projectDir;
        this.outputFileName = outputFileName;
    }

    public void test() {
        com.android.builder.model.v2.ide.AndroidArtifact mainArtifact = variant.getMainArtifact();
        assertThat(mainArtifact).named("Main Artifact null-check").isNotNull();

        String variantName = variant.getName();
        File build = new File(projectDir,  "build");
        File apk = new File(build, "outputs/apk/" + outputFileName);

        Collection<File> generatedSourceFolders = mainArtifact.getGeneratedSourceFolders();
        assertThat(generatedSourceFolders).named("Gen src Folder count").hasSize(2);

        List<String> apkFolderOutput = ProjectBuildOutputUtilsV2.getApkFolderOutput(variant);
        assertThat(apkFolderOutput).named("artifact output").isNotNull();
        assertThat(apkFolderOutput).hasSize(1);

        File singleOutputFile = new File(ProjectBuildOutputUtilsV2.getSingleOutputFile(variant));
        assertThat(singleOutputFile).named(variantName + " output").isEqualTo(apk);
    }
}
