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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtilsV2;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtilsV2;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.v2.ide.Variant;
import com.android.builder.model.v2.models.AndroidProject;
import java.io.File;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for renamedApk. */
public class RenamedApkTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("renamedApk").create();

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkModelReflectsRenamedApk() throws Exception {
        project.executor().run("clean", "assembleDebug");
        AndroidProject projectBuildOutput =
                project.modelV2()
                        .ignoreSyncIssues()
                        .fetchModels(null, null)
                        .getContainer()
                        .getProject(null, ":")
                        .getAndroidProject();
        Variant debugVariant = AndroidProjectUtilsV2.getDebugVariant(projectBuildOutput);
        Collection<String> outputFiles = ProjectBuildOutputUtilsV2.getApkFolderOutput(debugVariant);

        File buildDir = new File(project.getProjectDir(), "build/outputs/apk/debug");

        assertEquals(1, outputFiles.size());
        String output = outputFiles.iterator().next();

        String variantName = BuilderConstants.DEBUG;
        assertEquals(
                "Output file for " + variantName,
                new File(buildDir, variantName + ".apk"),
                new File(output));
    }

    @Test
    public void checkRenamedApk() {
        File debugApk = project.file("build/outputs/apk/debug/debug.apk");
        assertTrue("Check output file: " + debugApk, debugApk.isFile());
    }

    /** Regression test for b/148641149. */
    @Test
    public void checkWarningForRelativePath() throws Exception {
        GradleBuildResult result;
        result = project.executor().run("clean", "assembleDebug");
        ScannerSubject.assertThat(result.getStdout())
                .doesNotContain(
                        "Relative paths are not supported when setting an output file name.");
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "outputFileName = \"", "outputFileName = \"../");
        result = project.executor().run("clean", "assembleDebug");
        ScannerSubject.assertThat(result.getStdout())
                .contains("Relative paths are not supported when setting an output file name.");
    }
}
