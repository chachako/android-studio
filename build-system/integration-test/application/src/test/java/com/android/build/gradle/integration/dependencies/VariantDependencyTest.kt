/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.getSingleOutputFile
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.models.AndroidProject
import com.android.testutils.truth.PathSubject
import com.android.testutils.truth.ZipFileSubject
import com.google.common.collect.Sets
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class VariantDependencyTest : ModelComparator() {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.noBuildFile())
        .create()

     lateinit var androidProject: AndroidProject

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                apply plugin: "com.android.application"
                android {
                    configurations {
                        freeLollipopDebugImplementation
                        paidIcsImplementation
                    }
                    namespace '${HelloWorldApp.NAMESPACE}'
                    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                    buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
                    flavorDimensions "model", "api"
                    productFlavors {
                        Lollipop {
                            dimension "api"
                            minSdkVersion 21
                        }
                        ics {
                            dimension "api"
                            minSdkVersion 15
                        }
                        free {
                            dimension "model"
                        }
                        paid {
                            dimension "model"
                        }
                    }
                }
                dependencies {
                    freeLollipopDebugImplementation "com.android.support:leanback-v17:${SUPPORT_LIB_VERSION}"
                    paidIcsImplementation "com.android.support:appcompat-v7:${SUPPORT_LIB_VERSION}"
                }
            """.trimIndent())

        project.execute("clean", "assemble")
        androidProject = project.modelV2().fetchModels().container.getProject().androidProject!!
    }

    @Test
    fun `test lollipop VariantDependencies model`() {
        val result = project.modelV2().ignoreSyncIssues()

        with(result.fetchModels(variantName = "freeLollipopDebug")).compareVariantDependencies(goldenFile = "freeLollipopDebug_VariantDependencies")
        with(result.fetchModels(variantName = "freeLollipopRelease")).compareVariantDependencies(goldenFile = "freeLollipopRelease_VariantDependencies")
        with(result.fetchModels(variantName = "paidLollipopRelease")).compareVariantDependencies(goldenFile = "paidLollipopRelease_VariantDependencies")
        with(result.fetchModels(variantName = "freeLollipopRelease")).compareVariantDependencies(goldenFile = "freeLollipopRelease_VariantDependencies")
    }

    @Test
    fun `test ics VariantDependencies model`() {
        val result = project.modelV2().ignoreSyncIssues()

        with(result.fetchModels(variantName = "paidIcsDebug")).compareVariantDependencies(goldenFile = "paidIcsDebug_VariantDependencies")
        with(result.fetchModels(variantName = "paidIcsRelease")).compareVariantDependencies(goldenFile = "paidIcsRelease_VariantDependencies")
        with(result.fetchModels(variantName = "freeIcsDebug")).compareVariantDependencies(goldenFile = "freeIcsDebug_VariantDependencies")
        with(result.fetchModels(variantName = "freeIcsRelease")).compareVariantDependencies(goldenFile = "freeIcsRelease_VariantDependencies")
    }

    @Test
    fun buildVariantSpecificDependency() {
        // check that the dependency was added by looking for a res file coming from the
        // dependency.
        checkApkForContent("freeLollipopDebug", "res/drawable/lb_background.xml")
    }

    @Test
    fun buildMultiFlavorDependency() {
        // check that the dependency was added by looking for a res file coming from the
        // dependency.
        val fullResPath = "res/anim/abc_fade_in.xml"
        checkApkForContent("paidIcsDebug", fullResPath)
        if (project.getIntermediateFile(InternalArtifactType.OPTIMIZED_PROCESSED_RES.getFolderName()).exists()) {
            checkApkForContent("paidIcsRelease", "res/y4.xml")
        } else {
            checkApkForContent("paidIcsRelease", fullResPath)
        }
    }

    @Test
    fun buildDefaultDependency() {
        // make sure that the other variants do not include any file from the variant-specific
        // and multi-flavor dependencies.
        val paths: Set<String?> = Sets.newHashSet(
            "res/anim/abc_fade_in.xml",
            "res/drawable/lb_background.xml"
        )
        checkApkForMissingContent("paidLollipopDebug", paths)
        checkApkForMissingContent("paidLollipopRelease", paths)
        checkApkForMissingContent("freeLollipopRelease", paths)
        checkApkForMissingContent("freeIcsDebug", paths)
        checkApkForMissingContent("freeIcsRelease", paths)
    }

    @Test
    fun modelVariantCount() {
        TruthHelper.assertThat(androidProject.variants.size).named("variants").isEqualTo(8)
    }

    private fun checkApkForContent(
        variantName: String, checkFilePath: String
    ) {
        // use the model to get the output APK!
        val variant: Variant = androidProject.getVariantByName(variantName)
        val apk = File(variant.getSingleOutputFile())
        PathSubject.assertThat(apk).isFile()
        ZipFileSubject.assertThat(apk) { it: ZipFileSubject ->
            it.contains(
                checkFilePath
            )
        }
    }

    private fun checkApkForMissingContent(
        variantName: String, checkFilePath: Set<String?>
    ) {
        // use the model to get the output APK!
        val variant: Variant = androidProject.getVariantByName(variantName)
        val apk = File(variant.getSingleOutputFile())
        PathSubject.assertThat(apk).isFile()
        ZipFileSubject.assertThat(
            apk
        ) { it: ZipFileSubject ->
            it.entries(".*")
                .containsNoneIn(checkFilePath)
        }
    }
}
