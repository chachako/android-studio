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
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth

import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tests to verify that build models detect instant enabling correctly.
 */
class ModelInstantAppCompatibleTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("dynamicApp")
        .create()

    @get:Rule
    val flavorProject = GradleTestProject.builder()
        .fromTestProject("basicMultiFlavors")
        .create()

    @get:Rule
    val buildTypeProject = GradleTestProject.builder()
        .fromTestProject("unitTestingBuildTypes")
        .create()

    @Test
    fun testBuildModelForInstantAppWithDynamicFeatures() {
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":app").mainSrcDir.parent, "AndroidManifest.xml"),
            "<application>",
            "<dist:module dist:instant=\"true\" /> <application>"
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature1").mainSrcDir.parent, "AndroidManifest.xml"),
            "dist:title=",
            "dist:instant=\"false\" dist:title="
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature2").mainSrcDir.parent, "AndroidManifest.xml"),
            "dist:onDemand=\"true\"",
            "dist:onDemand=\"false\" dist:instant=\"true\""
        )
        val feature1 = project.modelV2().ignoreSyncIssues().fetchModels().container
                .getProject(":feature1").androidProject
        val feature2 = project.modelV2().ignoreSyncIssues().fetchModels().container
                .getProject(":feature2").androidProject
        val app = project.modelV2().ignoreSyncIssues().fetchModels().container
                .getProject(":feature2").androidProject

        for (variant in feature1!!.variants) {
            Truth.assertThat(variant.isInstantAppCompatible)
                    .named("Project :feature1 isInstantAppCompatible property populated from manifest")
                    .isFalse()
        }

        for (variant in feature2!!.variants) {
            Truth.assertThat(variant.isInstantAppCompatible)
                    .named("Project :feature2 isInstantAppCompatible property populated from manifest")
                    .isTrue()
        }

        for (variant in app?.variants!!) {
            Truth.assertThat(variant.isInstantAppCompatible)
                    .named("Project :feature2 isInstantAppCompatible property populated from manifest")
                    .isTrue()
        }
    }

    @Test
    fun testBuildModelForInstantAppWithManifestWithInvalidCharacters() {
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":app").mainSrcDir.parent, "AndroidManifest.xml"),
            "<application>",
            " <dist:module dist:instant=\"true\" /> <application>"
        ) // String with non-breaking space in it.
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature1").mainSrcDir.parent, "AndroidManifest.xml"),
            "dist:title=",
            "dist:instant=\"false\" dist:title="
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature2").mainSrcDir.parent, "AndroidManifest.xml"),
            "dist:onDemand=\"true\"",
            "dist:onDemand=\"false\" dist:instant=\"true\""
        )

        val issues = project.modelV2().ignoreSyncIssues().fetchModels()
            .container.getProject(":app").issues?.syncIssues!!

        Truth.assertThat(issues.size).isAtLeast(1)
        Truth.assertThat(issues.single().message.trim()).startsWith("Failed to parse XML")
        Truth.assertThat(issues.single().message).contains(
            File(
                project.getSubproject(":app").mainSrcDir.parent,
                "/AndroidManifest.xml"
            ).toString()
        )
    }

    @Test
    fun testBuildModelForInstantAppWithCommentsOutsideManifest() {
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":app").mainSrcDir.parent, "/AndroidManifest.xml"),
            "<manifest",
            "<!-- This is a comment. -->\n<manifest"
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":app").mainSrcDir.parent, "/AndroidManifest.xml"),
            "</manifest>",
            "</manifest>\n<!-- This is a comment. -->"
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":app").mainSrcDir.parent, "AndroidManifest.xml"),
            "<application>",
            "<dist:module dist:instant=\"true\" /> <application>"
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature1").mainSrcDir.parent, "AndroidManifest.xml"),
            "dist:title=",
            "dist:instant=\"false\" dist:title="
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature2").mainSrcDir.parent, "AndroidManifest.xml"),
            "dist:onDemand=\"true\"",
            "dist:onDemand=\"false\" dist:instant=\"true\""
        )

        val issues = project.modelV2().ignoreSyncIssues().fetchModels()
            .container.getProject(":app").issues?.syncIssues!!
        Truth.assertThat(issues).isEmpty()

        val models = project.modelV2().ignoreSyncIssues().fetchModels().container.rootInfoMap
        for ((modelName, model) in models) {
            for (variant in model.androidProject?.variants!!) {
                if (modelName == ":feature1") {
                    Truth.assertThat(variant.isInstantAppCompatible)
                        .named("Project $modelName isInstantAppCompatible property populated from manifest")
                        .isFalse()
                } else {
                    Truth.assertThat(variant.isInstantAppCompatible)
                        .named("Project $modelName isInstantAppCompatible property populated from manifest")
                        .isTrue()
                }
            }
        }
    }

    @Test
    fun testBuildModelForInstantAppWithDynamicFeaturesAndBuildTypeManifestOverlay() {
        TestFileUtils.searchAndReplace(
            File(buildTypeProject.mainSrcDir.parent, "/AndroidManifest.xml"),
            "</manifest",
            "<dist:module dist:instant=\"false\" /> </manifest"
        )
        TestFileUtils.searchAndReplace(
            File(buildTypeProject.mainSrcDir.parentFile.parent, "/debug/AndroidManifest.xml"),
            "</manifest",
            "<dist:module dist:instant=\"true\" /> </manifest"
        )

        val models = buildTypeProject.modelV2().ignoreSyncIssues().fetchModels().container.rootInfoMap
        for ((modelName, model) in models) {
            for (variant in model.androidProject?.variants!!) {
                if (variant.name == "debug") {
                    Truth.assertThat(variant.isInstantAppCompatible)
                        .named("Project $modelName isInstantAppCompatible property populated from manifest")
                        .isTrue()
                } else {
                    Truth.assertThat(variant.isInstantAppCompatible)
                        .named("Project $modelName isInstantAppCompatible property populated from manifest")
                        .isFalse()
                }
            }
        }
    }

    @Test
    fun testBuildModelForInstantAppWithDynamicFeaturesAndFlavorManifestOverlay() {
        TestFileUtils.searchAndReplace(
            File(flavorProject.mainSrcDir.parent, "/AndroidManifest.xml"),
            "<application",
            "<dist:module dist:instant=\"false\" /> <application"
        )
        TestFileUtils.searchAndReplace(
            File(flavorProject.mainSrcDir.parentFile.parent, "/free/AndroidManifest.xml"),
            "<manifest",
            "<manifest xmlns:dist=\"http://schemas.android.com/apk/distribution\""
        )
        TestFileUtils.searchAndReplace(
            File(flavorProject.mainSrcDir.parentFile.parent, "/free/AndroidManifest.xml"),
            "<application",
            "<dist:module dist:instant=\"true\" /> <application"
        )

        val models = flavorProject.modelV2().ignoreSyncIssues().fetchModels().container.rootInfoMap
        for ((modelName, model) in models) {
            for (variant in model.androidProject?.variants!!) {
                if (variant.displayName.contains("free")) {
                    Truth.assertThat(variant.isInstantAppCompatible)
                        .named("Project $modelName isInstantAppCompatible property populated from manifest")
                        .isTrue()
                } else {
                    Truth.assertThat(variant.isInstantAppCompatible)
                        .named("Project $modelName isInstantAppCompatible property populated from manifest")
                        .isFalse()
                }
            }
        }
    }

    @Test
    fun testBuildModelForInstantAppWithDynamicFeaturesWithNonstandardNamespace() {
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":app").mainSrcDir.parent, "/AndroidManifest.xml"),
            "xmlns:dist",
            "xmlns:apkd"
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":app").mainSrcDir.parent, "AndroidManifest.xml"),
            "<application>",
            "<apkd:module apkd:instant=\"true\" /> <application>"
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature1").mainSrcDir.parent, "AndroidManifest.xml"),
            "dist:title=",
            "dist:instant=\"false\" dist:title="
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature2").mainSrcDir.parent, "AndroidManifest.xml"),
            "dist:onDemand=\"true\"",
            "dist:onDemand=\"false\" dist:instant=\"true\""
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature1").mainSrcDir.parent, "/AndroidManifest.xml"),
            "xmlns:dist",
            "xmlns:apkd"
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature1").mainSrcDir.parent, "/AndroidManifest.xml"),
            "dist:",
            "apkd:"
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature2").mainSrcDir.parent, "/AndroidManifest.xml"),
            "xmlns:dist",
            "xmlns:apkd"
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature2").mainSrcDir.parent, "/AndroidManifest.xml"),
            "dist:",
            "apkd:"
        )
        val models = project.modelV2().ignoreSyncIssues().fetchModels().container.rootInfoMap
        for ((modelName, model) in models) {
            for (variant in model.androidProject?.variants!!) {
                if (modelName == ":feature1") {
                    Truth.assertThat(variant.isInstantAppCompatible)
                        .named("Project $modelName isInstantAppCompatible property populated from manifest")
                        .isFalse()
                } else {
                    Truth.assertThat(variant.isInstantAppCompatible)
                        .named("Project $modelName isInstantAppCompatible property populated from manifest")
                        .isTrue()
                }
            }
        }
    }

    @Test
    fun testBuildModelForInstantAppWithDynamicFeaturesWithIncorrectNamespace() {
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":app").mainSrcDir.parent, "AndroidManifest.xml"),
            "<application>",
            "<dist:module android:instant=\"true\" /> <application>"
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature1").mainSrcDir.parent, "AndroidManifest.xml"),
            "dist:title=",
            "dist:instant=\"false\" dist:title="
        )
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature2").mainSrcDir.parent, "AndroidManifest.xml"),
            "dist:onDemand=\"true\"",
            "dist:onDemand=\"false\" android:instant=\"true\""
        )

        val models = project.modelV2().ignoreSyncIssues().fetchModels().container.rootInfoMap
        for ((modelName, model) in models) {
            for (variant in model.androidProject?.variants!!) {
                Truth.assertThat(variant.isInstantAppCompatible)
                    .named("Project $modelName isInstantAppCompatible property populated from manifest")
                    .isFalse()
            }
        }

    }

    @Test
    fun testBuildModelForInstantAppWithDynamicFeaturesWithoutInstantTag() {
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":app").mainSrcDir.parent, "AndroidManifest.xml"),
            "<application>",
            "<dist:module dist:instant=\"true\" /> <application>"
        )

        val models = project.modelV2().ignoreSyncIssues().fetchModels().container.rootInfoMap
        for ((modelName, model) in models) {
            for (variant in model.androidProject?.variants!!) {
                if (modelName == ":app") {
                    Truth.assertThat(variant.isInstantAppCompatible)
                        .named("Project $modelName isInstantAppCompatible property populated from manifest")
                        .isTrue()
                } else {
                    Truth.assertThat(variant.isInstantAppCompatible)
                        .named("Project $modelName isInstantAppCompatible property populated from manifest")
                        .isFalse()
                }
            }
        }
    }

    @Test
    fun testBuildModelForInstantAppWithDynamicFeaturesWithInvalidInstantValue() {
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":app").mainSrcDir.parent, "AndroidManifest.xml"),
            "<application>",
            "<dist:module dist:instant=\"blah\" /> <application>"
        )
        val models = project.modelV2().ignoreSyncIssues().fetchModels().container.rootInfoMap
        for ((modelName, model) in models) {
            for (variant in model.androidProject?.variants!!) {
                Truth.assertThat(variant.isInstantAppCompatible)
                    .named("Project $modelName isInstantAppCompatible property populated from manifest")
                    .isFalse()
            }
        }
    }
}
