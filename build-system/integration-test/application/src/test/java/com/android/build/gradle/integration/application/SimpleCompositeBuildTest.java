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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.google.common.truth.Truth;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Test;

public class SimpleCompositeBuildTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("simpleCompositeBuild")
                    .withDependencyChecker(false)
                    .create();

    @Test
    public void testBuild() {
        project.execute("clean", "assembleDebug");
        ModelContainerV2.ModelInfo modelInfo = project.modelV2()
                .fetchVariantDependencies("debug")
                .getContainer()
                .getProject(":app");

        List<com.android.builder.model.v2.ide.GraphItem>
                dependencies
                = modelInfo.getVariantDependencies().getMainArtifact().getCompileDependencies();

        Truth.assertThat(dependencies).hasSize(1);
        Truth.assertThat(
                        dependencies
                                .get(0)
                                .getKey()
                                .replace(
                                        "org.gradle.jvm.version>" + Runtime.version().feature(),
                                        "org.gradle.jvm.version>{Java_Version}"))
                .isEqualTo(
                        ":string-utils|:|org.gradle.category>library, org.gradle.dependency.bundling>external, org.gradle.jvm.version>{Java_Version}, org.gradle.libraryelements>jar, org.gradle.usage>java-api|org.sample:string-utils:1.0");
    }
}
