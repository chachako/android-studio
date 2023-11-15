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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.internal.cxx.configure.CMakeVersion;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.v2.ide.Library;
import com.android.builder.model.v2.models.VariantDependencies;
import java.io.File;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for rsSupportMode. */
public class RsSupportModeTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("rsSupportMode")
                    .setCmakeVersion(CMakeVersion.DEFAULT.getSdkFolderName())
                    .setWithCmakeDirInLocalProp(true)
                    .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                    .addGradleProperties(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.getPropertyName() + "=false")
                    .create();

    @Test
    public void testRsSupportMode() {
        project.execute("clean", "assembleDebug", "assembleX86DebugAndroidTest");

        VariantDependencies x86Debug =
                project.modelV2()
                        .ignoreSyncIssues()
                        .fetchModels("x86Debug", null)
                        .getContainer()
                        .getProject()
                        .getVariantDependencies();

        Map<String, Library> libraries = x86Debug.getLibraries();
        assertThat(libraries).isNotEmpty();

        boolean foundSupportJar = false;
        for (Library lib : libraries.values()) {
            File file = lib.getArtifact();
            if (SdkConstants.FN_RENDERSCRIPT_V8_JAR.equals(file.getName())) {
                foundSupportJar = true;
                break;
            }
        }

        assertThat(foundSupportJar).isTrue();
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "x86"))
                .containsClass("Landroid/support/v8/renderscript/RenderScript;");
    }
}
