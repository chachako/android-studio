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
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.getDebugGenerateSourcesCommands
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.*
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
class GenFolderApiTest {

    @get:Rule
    val project: GradleTestProject = builder().fromTestProject("genFolderApi").create()

    private lateinit var ideSetupTasks: List<String>

    private lateinit var container: ModelContainerV2

    @Before
    @Throws(Exception::class)
    fun setUp() {
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
            ApkSubject.assertThat(apk).contains("res/xml/generated.xml")
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
            .run("clean")
        project.executor()
            .withArgument("-P" + "inject_enable_generate_values_res=true")
            .run(ideSetupTasks)

        val mainArtifact =
            container.getProject().androidProject
                ?.getVariantByName("debug")
                ?.mainArtifact!!

        val customCode =
            mainArtifact.generatedSourceFolders
                .single { it: File -> it.absolutePath.startsWith(sourceFolderStart) }
        PathSubject.assertThat(customCode).isDirectory()

        val customResources =
            mainArtifact.generatedResourceFolders
                .single { it: File -> it.absolutePath.startsWith(customResPath) }

        PathSubject.assertThat(customResources).isDirectory()

        val customResources2 =
            mainArtifact.generatedResourceFolders.single { it: File ->
                it.absolutePath.startsWith(
                    customRes2Path
                )
            }
        PathSubject.assertThat(customResources2).isDirectory()
    }


    @Test
    @Throws(Exception::class)
    fun checkAddingAndRemovingGeneratingTasks() {
        project.executor()
            .withArgument("-P" + "inject_enable_generate_values_res=false")
            .run("assembleDebug")

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

            val genSourceFolder = mainInfo.generatedSourceFolders

            // We're looking for a custom folder
            val sourceFolderStart = sourceFolderStart
            var found = false
            for (f in genSourceFolder) {
                if (f.absolutePath.startsWith(sourceFolderStart)) {
                    found = true
                    break
                }
            }

            Assert.assertTrue("custom generated source folder check", found)
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
            Truth.assertThat(genResFolders)
                .containsAtLeast(
                    customResPath + variant.name,
                    customRes2Path + variant.name
                )
        }
    }

    @Test
    @Throws(Exception::class)
    fun backwardsCompatible() {
        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        Truth.assertThat(TestFileUtils.sha1NormalizedLineEndings(project.file("build.gradle")))
            .isEqualTo("384acd749b7c400845fb96eace7b0def85cade2e")
    }

    private val customResPath: String
        get() = (FileUtils.join(project.projectDir.absolutePath, "build", "customRes")
                + File.separatorChar)

    private val customRes2Path: String
        get() = (FileUtils.join(project.projectDir.absolutePath, "build", "customRes2")
                + File.separatorChar)

    private val sourceFolderStart: String
        get() = (FileUtils.join(project.projectDir.absolutePath, "build", "customCode")
                + File.separatorChar)
}
