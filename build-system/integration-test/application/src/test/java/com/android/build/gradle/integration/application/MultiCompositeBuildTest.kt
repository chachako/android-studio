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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Test the dependencies of a complex multi module/multi build setup with android modules
 * in the included build(s).
 *
 * The dependencies from the root app looks like this:
 * :app:debugCompileClasspath
 * +--- project :composite0
 * |    +--- com.test.composite:composite2:1.0 -> project :composite2
 * |    \--- com.test.composite:composite3:1.0 -> project :TestCompositeLib3:composite3
 * |         \--- com.test.composite:composite4:1.0 -> project :composite4
 * +--- com.test.composite:composite1:1.0 -> project :TestCompositeLib1:composite1
 * |    +--- com.test.composite:composite2:1.0 -> project :composite2
 * |    \--- com.test.composite:composite3:1.0 -> project :TestCompositeLib3:composite3
 * |         \--- com.test.composite:composite4:1.0 -> project :composite4
 * \--- com.test.composite:composite4:1.0 -> project :composite4
 *
 * The modules are of the following types:
 * TestCompositeLib1 :app        -> android app
 * TestCompositeLib1 :composite1 -> android lib
 * TestCompositeLib2 :           -> java
 * TestCompositeLib3 :app        -> android app
 * TestCompositeLib3 :composite3 -> android lib
 * TestCompositeLib4 :           -> java
 *
 */
class MultiCompositeBuildTest: ModelComparator() {

    @JvmField
    @Rule
    val project = GradleTestProject.builder()
            .fromTestProject("multiCompositeBuild")
            .withIncludedBuilds(
                    "TestCompositeApp",
                    "TestCompositeLib1",
                    "TestCompositeLib3"
            ).create()

    @Test
    fun `dependencies for root app module`() {
        val model = project
            .getSubproject("TestCompositeApp")
            .modelV2()
            .withFailOnWarning(false)
            .fetchModels(variantName = "debug")

        val rootModelMap = model.container.rootInfoMap

        assertThat(rootModelMap.entries).hasSize(2)
        assertThat(rootModelMap.keys).containsExactly(":app", ":composite0")


        with(model).compareVariantDependencies(
            projectAction = { getProject(":app") }, goldenFile = "TestCompositeApp_app_VariantDependencies"
        )
    }

    @Test
    fun `dependencies for included build module`() {
        val model = project
            .getSubproject("TestCompositeLib1")
            .modelV2()
            .withFailOnWarning(false)
            .fetchModels(variantName = "debug")

        val rootModelMap = model.container.rootInfoMap
        assertThat(rootModelMap.entries).hasSize(2)
        assertThat(rootModelMap.keys).containsExactly(":app", ":composite1")

        with(model).compareVariantDependencies(
            projectAction = { getProject(":composite1") }, goldenFile = "TestCompositeLib1_composite1_VariantDependencies"
        )

        with(model).compareVariantDependencies(
            projectAction = { getProject(":app") }, goldenFile = "TestCompositeLib1_app_VariantDependencies"
        )
    }
}
