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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.DexSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.coverage.JacocoOptions;
import com.android.builder.model.v2.ide.GraphItem;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.google.common.truth.Truth8;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test for jacoco agent runtime dependencies. */
public class JacocoDependenciesTest {

    // AGP keeps an old version of the Jacoco libraries for testing version changes.
    private final String oldJacocoVersion = "0.7.4.201502262128";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    @Before
    public void setUp() throws Exception {
        project.setIncludedProjects("app", "library");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\ndependencies {\n" + "    api project(':library')\n" + "}\n");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\nandroid.buildTypes.debug.enableAndroidTestCoverage = true");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\nandroid.buildTypes.debug.enableAndroidTestCoverage = true");
    }

    @Test
    public void checkJacocoInApp() throws IOException, InterruptedException {
        project.executor().run("clean", "app:assembleDebug");

        Apk apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk.getFile()).isFile();

        Optional<Dex> dexOptional = apk.getMainDexFile();
        Truth8.assertThat(dexOptional).isPresent();

        assertThat(dexOptional.get()).containsClasses("Lorg/jacoco/agent/rt/IAgent;");
    }

    @Test
    public void checkJacocoInLibAndroidTest() throws IOException, InterruptedException {
        project.executor().run("clean", "library:assembleDebugAndroidTest");

        Apk apk =
                project.getSubproject(":library")
                        .getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG);
        assertThat(apk.getFile()).isFile();

        Optional<Dex> dexOptional = apk.getMainDexFile();
        Truth8.assertThat(dexOptional).isPresent();

        assertThat(dexOptional.get()).containsClasses("Lorg/jacoco/agent/rt/IAgent;");
    }

    @Test
    public void checkJacocoNotInAppWhenLibraryHasTestCoverageEnabled()
            throws IOException, InterruptedException {
        TestFileUtils.searchAndReplace(
                project.getSubproject("app").getBuildFile(),
                "android.buildTypes.debug.enableAndroidTestCoverage = true",
                "android.buildTypes.debug.enableAndroidTestCoverage = false");

        project.executor().run("clean", "app:assembleDebug");

        Apk apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk.getFile()).isFile();

        Optional<Dex> dexOptional = apk.getMainDexFile();
        Truth8.assertThat(dexOptional).isPresent();

        assertThat(dexOptional.get()).doesNotContainClasses("Lorg/jacoco/agent/rt/IAgent;");
    }

    @Test
    public void checkDefaultVersion() {
        assertAgentMavenCoordinates("org.jacoco|org.jacoco.agent|" + JacocoOptions.DEFAULT_VERSION);
    }

    @Test
    public void checkVersionForced() throws IOException {
        TestFileUtils.searchAndReplace(
                project.getSubproject("app").getBuildFile(),
                "apply plugin: 'com.android.application'",
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "dependencies {\n"
                        + "  implementation "
                        + "'org.jacoco:org.jacoco.agent:"
                        + oldJacocoVersion
                        + ":runtime'\n"
                        + "}\n");
        assertAgentMavenCoordinates("org.jacoco|org.jacoco.agent|" + JacocoOptions.DEFAULT_VERSION);
    }

    @Test
    public void checkAgentRuntimeVersionWhenOverridden() throws IOException {
        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n" + "android.jacoco.version '" + oldJacocoVersion + "'\n");

        assertAgentMavenCoordinates("org.jacoco|org.jacoco.agent|" + oldJacocoVersion);
    }

    private void assertAgentMavenCoordinates(@NonNull String expectedPrefix) {
        List<GraphItem> runtimeDeps =
                project.modelV2()
                        .fetchModels("debug", null)
                        .getContainer()
                        .getProject(":app")
                        .getVariantDependencies()
                        .getMainArtifact()
                        .getRuntimeDependencies();

        List<String> keys =
                runtimeDeps.stream().map(GraphItem::getKey).collect(Collectors.toList());

        // assert that one of the keys contains the expected prefix
        boolean found = false;
        for (String key : keys) {
            if (key.startsWith(expectedPrefix)) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }
}
