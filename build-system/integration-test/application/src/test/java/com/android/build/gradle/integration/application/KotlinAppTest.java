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

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.builder.model.v2.ide.ProjectType;
import com.android.testutils.apk.Apk;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Assemble tests for kotlin. */
@Category(SmokeTests.class)
public class KotlinAppTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("kotlinApp").create();

    @After
    public void cleanUp() {
        project = null;
    }

    @Test
    public void projectModel() throws IOException {
        ModelContainerV2 models = project.modelV2().fetchModels().getContainer();
        ModelContainerV2.ModelInfo appModel = models.getProject(":app");

        assertThat(appModel.getBasicAndroidProject().getProjectType())
                .named("Project Type")
                .isEqualTo(ProjectType.APPLICATION);
        assertThat(appModel.getAndroidDsl().getCompileTarget())
                .named("Compile Target")
                .isEqualTo(GradleTestProject.getCompileSdkHash());
    }

    @Test
    public void apkContents() throws Exception {
        project.executor().run("clean", "app:assembleDebug");
        Apk apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);

        assertThat(apk).isNotNull();
        assertThat(apk).containsResource("layout/activity_layout.xml");
        assertThat(apk).containsResource("layout/lib_activity_layout.xml");
        assertThat(apk).containsMainClass("Lcom/example/android/kotlin/MainActivity;");
        assertThat(apk).containsMainClass("Lcom/example/android/kotlin/LibActivity;");
    }

    /** Regression test for b/155721209. */
    @Test
    public void aarContents() throws Exception {
        project.executor().run("clean", "library:assembleDebug");

        project.getSubproject("library")
                .getAar(
                        "debug",
                        aar -> {
                            try {
                                Path javaResource =
                                        aar.getJavaResource("META-INF/library_debug.kotlin_module");
                                assertThat(javaResource != null).isTrue();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
    }
}
