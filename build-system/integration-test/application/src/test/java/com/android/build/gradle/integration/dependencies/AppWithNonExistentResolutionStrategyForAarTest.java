/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.dependencies;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.v2.ide.UnresolvedDependency;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** test for flavored dependency on a different package. */
public class AppWithNonExistentResolutionStrategyForAarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        project.setIncludedProjects("app", "library");

        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compileOnly project(\":library\")\n"
                        + "}\n"
                        + "\n"
                        + "configurations {\n"
                        + "  debugCompileClasspath\n"
                        + "  debugRuntimeClasspath\n"
                        + "}\n"
                        + "\n"
                        + "configurations.debugCompileClasspath {\n"
                        + "  resolutionStrategy {\n"
                        + "    eachDependency { DependencyResolveDetails details ->\n"
                        + "      if (details.requested.name == \"jdeferred-android-aar\") {\n"
                        + "        details.useVersion \"-1.-1.-1\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n"
                        + "configurations.debugRuntimeClasspath {\n"
                        + "  resolutionStrategy {\n"
                        + "    eachDependency { DependencyResolveDetails details ->\n"
                        + "      if (details.requested.name == \"jdeferred-android-aar\") {\n"
                        + "        details.useVersion \"-1.-1.-1\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n"
                        + "\n");

        TestFileUtils.appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    api \"org.jdeferred:jdeferred-android-aar:1.2.3\"\n"
                        + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkUnresolvedDepInTheModel() {
        List<UnresolvedDependency> unresolvedDependencies =
                project.modelV2()
                        .ignoreSyncIssues()
                        .fetchVariantDependencies("debug")
                        .getContainer()
                        .getProject(":app")
                        .getVariantDependencies()
                        .getMainArtifact()
                        .getUnresolvedDependencies();

        assertThat(unresolvedDependencies.size()).isEqualTo(1);
        assertThat(unresolvedDependencies.get(0).getName())
                .isEqualTo("org.jdeferred:jdeferred-android-aar:-1.-1.-1");
    }
}
