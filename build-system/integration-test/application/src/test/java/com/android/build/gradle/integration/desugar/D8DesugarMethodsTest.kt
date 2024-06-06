/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.desugar

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.v2.models.AndroidProject
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class D8DesugarMethodsTest {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun testModelFetching() {
        val model = project.modelV2().fetchModels().container.getProject(":").androidProject
        val expectedMethods = "java/lang/Boolean#compare(ZZ)I"
        val d8BackportedMethods = model!!.variants.first().desugaredMethods
            .find { it.name.contains("D8BackportedDesugaredMethods.txt") }
        assertTrue {
            d8BackportedMethods!!.readLines().contains(expectedMethods)
        }
    }


    @Test
    fun testMethodListAffectedByMinSdk() {
        TestFileUtils.appendToFile(
            project.buildFile,
            "android.defaultConfig.minSdk = 24"
        )
        var model = project.modelV2().fetchModels().container.getProject(":").androidProject
        val requireNonNullElseGetMethod =
            "java/util/Objects#requireNonNullElseGet(Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;"
        // check requireNonNullElseGetMethod exist in backported list when minSdk is 24 for all
        // variants
        var desugaredMethodsForDebug = getDesugaredMethods(model, "debug")
        var desugaredMethodsForRelease =  getDesugaredMethods(model, "release")
        assertTrue { desugaredMethodsForDebug!!.readLines().contains(requireNonNullElseGetMethod) }
        assertTrue { desugaredMethodsForRelease!!.readLines().contains(requireNonNullElseGetMethod) }
        // set minSdk to 23 for release variant and ensure requireNonNullElseGetMethod doesn't exist
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                def releaseSelector = androidComponents.selector().withBuildType("release")

                androidComponents.beforeVariants(releaseSelector) { variantBuilder ->
                    variantBuilder.minSdk = 23
                }
            """.trimIndent()
        )
        model = project.modelV2().fetchModels().container.getProject(":").androidProject
        desugaredMethodsForDebug = getDesugaredMethods(model, "debug")
        desugaredMethodsForRelease = getDesugaredMethods(model, "release")
        assertTrue { desugaredMethodsForDebug!!.readLines().contains(requireNonNullElseGetMethod) }
        assertFalse {
            desugaredMethodsForRelease!!.readLines().contains(requireNonNullElseGetMethod)
        }
    }

    private fun getDesugaredMethods(model: AndroidProject?, variantName: String): File? {
        check(model != null) { "model should not be null" }
        return model.variants.first { it.name == variantName }.desugaredMethods
            .find { it.name.contains("D8BackportedDesugaredMethods.txt") }
    }
}
