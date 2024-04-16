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
package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.build.gradle.integration.common.fixture.VariantApiTestType
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.getDebugGenerateSourcesCommands
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Tests for generated source registration APIs.
 *
 *
 * Includes the following APIs:
 *
 *
 *  * registerJavaGeneratingTask
 *  * registerResGeneratingTask
 *  * registerGeneratedResFolders
 *
 */
@RunWith(Parameterized::class)
class GenFolderApiTest(private val variantApiTestType: VariantApiTestType) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variantAPI() = VariantApiTestType.values()
    }

    @get:Rule
    val project: GradleTestProject = builder().fromTestProject("genFolderApi").create()

    private lateinit var ideSetupTasks: List<String>

    private lateinit var container: ModelContainerV2

    @Before
    @Throws(Exception::class)
    fun setUp() {

        File(project.projectDir, "${variantApiTestType.name.lowercase()}_variant_api.build.gradle")
            .copyTo(project.buildFile)

        project.executor()
            .withArgument("-P" + "inject_enable_generate_values_res=true")
            .run("assembleDebug")
        container =
            project.modelV2()
                .withArgument("-P" + "inject_enable_generate_values_res=true")
                .fetchModels()
                .container

        ideSetupTasks = container.getDebugGenerateSourcesCommands()
    }

    @Test
    @Throws(Exception::class)
    fun checkTheCustomJavaGenerationTaskRan() {
        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            ApkSubject.assertThat(apk).containsClass("Lcom/custom/Foo;")
            ApkSubject.assertThat(apk).containsClass("Lcom/custom/Bar;")
        }
    }

    @Test
    @Throws(Exception::class)
    fun checkTheCustomResGenerationTaskRan() {
        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            if (variantApiTestType == VariantApiTestType.OLD) {
                ApkSubject.assertThat(apk).contains("res/xml/generated.xml")
            }
            ApkSubject.assertThat(apk)
                .hasClass("Lcom/android/tests/basic/R\$string;")
                .that()
                .hasField("generated_string")
        }
    }

    /** Regression test for b/120750247  */
    @Test
    @Throws(Exception::class)
    fun checkCustomGenerationRunAtSync() {
        project.executor()
            .withArgument("-P" + "inject_enable_generate_values_res=true")
            .run(listOf("clean").plus(ideSetupTasks))

        val mainArtifact =
            container.getProject().androidProject
                ?.getVariantByName("debug")
                ?.mainArtifact!!

        val javaSources = mainArtifact.generatedSourceFolders
            .filter { it: File -> it.absolutePath.startsWith(
                getCustomPath("java", "debug")
            ) }
        Truth.assertThat(javaSources).isNotEmpty()
        javaSources.forEach {
            PathSubject.assertThat(it).isDirectory()
        }

        val resSources = mainArtifact.generatedResourceFolders
            .filter { it: File -> it.absolutePath.startsWith(
                getCustomPath("res", "debug")
            ) }
        Truth.assertThat(resSources).isNotEmpty()
        resSources.forEach {
            PathSubject.assertThat(it).isDirectory()
        }

        if (variantApiTestType == VariantApiTestType.OLD) {
            val customResources2 =
                mainArtifact.generatedResourceFolders.single { it: File ->
                    it.absolutePath.startsWith(
                        customRes2Path
                    )
                }
            PathSubject.assertThat(customResources2).isDirectory()
        }
    }


    @Test
    @Throws(Exception::class)
    fun checkAddingAndRemovingGeneratingTasks() {
        project.executor()
            .withArgument("-P" + "inject_enable_generate_values_res=false")
            .run("clean", "assembleDebug")

        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            ApkSubject.assertThat(apk)
                .hasClass("Lcom/android/tests/basic/R\$string;")
                .that()
                .doesNotHaveField("generated_string")
        }
        project.executor()
            .withArgument("-P" + "inject_enable_generate_values_res=true")
            .run("assembleDebug")
        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            ApkSubject.assertThat(apk)
                .hasClass("Lcom/android/tests/basic/R\$string;")
                .that()
                .hasField("generated_string")
        }
    }

    @Test
    fun checkJavaFolderInModel() {
        container.getProject().androidProject?.variants?.forEach { variant ->
            val mainInfo = variant.mainArtifact
            Assert.assertNotNull(
                "Null-check on mainArtifactInfo for " + variant.displayName, mainInfo
            )

            Truth.assertThat(mainInfo.generatedSourceFolders.map(File::getAbsolutePath)).containsAtLeast(
                getCustomPath("java", variant.name),
                getCustomPath("java", variant.name, "2")
            )
        }
    }

    @Test
    fun checkResFolderInModel() {
        container.getProject().androidProject?.variants?.forEach {  variant ->
            val mainInfo = variant.mainArtifact
            Assert.assertNotNull(
                "Null-check on mainArtifactInfo for " + variant.displayName, mainInfo
            )

            val genResFolders = mainInfo.generatedResourceFolders.map(File::getAbsolutePath)
            Truth.assertThat(genResFolders).containsNoDuplicates()
            Truth.assertThat(genResFolders).contains(getCustomPath("res", variant.name))
            if (variantApiTestType == VariantApiTestType.OLD) {
                Truth.assertThat(genResFolders).contains(
                    customRes2Path + variant.name
                )
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun backwardsCompatible() {
        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        Truth.assertThat(TestFileUtils.sha1NormalizedLineEndings(project.file("old_variant_api.build.gradle")))
            .isEqualTo("6f5bcddd76198403d48e8ea1acf0a50f4c78762b")
        Truth.assertThat(TestFileUtils.sha1NormalizedLineEndings(project.file("new_variant_api.build.gradle")))
            .isEqualTo("3e32c2339d7a88e0840e3fc7a304c57c99b2cd80")
    }

    private fun getCustomPath(sourceType: String, variantName: String, index: String = ""): String =
        when(variantApiTestType) {
            VariantApiTestType.OLD ->
                FileUtils.join(
                    project.projectDir.absolutePath,
                    "build", "custom" + sourceType.capitalize() + index,
                    variantName)

            VariantApiTestType.NEW ->
                FileUtils.join(
                    project.projectDir.absolutePath,
                    "build",
                    "generated",
                    sourceType,
                    "generate${sourceType.capitalize()}For${variantName.capitalize()}$index"
                )
        }

    private val customRes2Path: String
        get() = (FileUtils.join(project.projectDir.absolutePath, "build", "customRes2")
                + File.separatorChar)
}
