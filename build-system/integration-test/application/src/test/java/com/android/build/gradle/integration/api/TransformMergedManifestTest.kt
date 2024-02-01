/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class TransformMergedManifestTest {

    @get:Rule
    val project1: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("dynamicApp")
        .create()

    @get:Rule
    val project2: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("libsTest")
        .create()

    private val updateManifestTask =
        """
             import org.apache.commons.io.FileUtils
             import com.android.build.api.artifact.SingleArtifact

             abstract class ManifestUpdaterTask extends DefaultTask {
             @InputFile
             abstract RegularFileProperty getMergedManifest()

             @OutputFile
             abstract RegularFileProperty getUpdatedManifest()

             @TaskAction
             void taskAction() {
                try {
                    FileUtils.copyFile(getMergedManifest().get().asFile, getUpdatedManifest().get().asFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("Updated merged manifest")
             }
             }
            """.trimIndent()

    private val updateManifestTaskRegistration = """

            androidComponents {
               onVariants(selector().all(), { variant ->
                  TaskProvider manifestUpdater =
                      project.tasks.register(variant.name + "ManifestUpdater", ManifestUpdaterTask)
                  variant.artifacts.use(manifestUpdater).wiredWithFiles(
                      ManifestUpdaterTask::getMergedManifest,
                      ManifestUpdaterTask::getUpdatedManifest
                  ).toTransform(SingleArtifact.MERGED_MANIFEST.INSTANCE)
                })
             }
    """.trimIndent()

    /** Regression test for http://b/321141574.*/
    @Test
    fun testTransformMergedManifestForDynamicFeature() {
        project1.getSubproject("feature1").also { feature ->
            feature.buildFile.appendText(updateManifestTask)
            feature.buildFile.appendText(updateManifestTaskRegistration)
        }
        val result = project1.executor().run(":feature1:assemble")
        // MERGED_MANIFEST transformers
        assertThat(result.didWorkTasks).contains(":feature1:debugManifestUpdater")
        assertThat(result.didWorkTasks).contains(":feature1:releaseManifestUpdater")
        // MERGED_MANIFEST consumers
        assertThat(result.didWorkTasks).contains(":feature1:copyDebugMergedManifest")
        assertThat(result.didWorkTasks).contains(":feature1:copyReleaseMergedManifest")
        assertThat(
            result.stdout.findAll(
                "Updated merged manifest"
            ).count()
        ).isEqualTo(2)
    }

    @Test
    fun testTransformMergedManifestForLibrary() {
        project2.getSubproject("lib1").also { feature ->
            feature.buildFile.appendText(updateManifestTask)
            feature.buildFile.appendText(updateManifestTaskRegistration)
        }
        val result = project2.executor().run(":lib1:assemble")
        assertThat(result.didWorkTasks).contains(":lib1:debugManifestUpdater")
        assertThat(result.didWorkTasks).contains(":lib1:releaseManifestUpdater")
        assertThat(result.didWorkTasks).contains(":lib1:bundleReleaseAar")
        assertThat(result.didWorkTasks).contains(":lib1:bundleDebugAar")
        assertThat(
            result.stdout.findAll(
                "Updated merged manifest"
            ).count()
        ).isEqualTo(2)
    }

    @Test
    fun testTransformMergedManifestForApplication() {
        project1.getSubproject("app").also { feature ->
            feature.buildFile.appendText(updateManifestTask)
            feature.buildFile.appendText(updateManifestTaskRegistration)
        }
        val result = project1.executor().run(":app:assemble")
        assertThat(result.didWorkTasks).contains(":app:debugManifestUpdater")
        assertThat(result.didWorkTasks).contains(":app:releaseManifestUpdater")
        assertThat(result.didWorkTasks).contains(":app:processDebugManifest")
        assertThat(result.didWorkTasks).contains(":app:processReleaseManifest")
        assertThat(
            result.stdout.findAll(
                "Updated merged manifest"
            ).count()
        ).isEqualTo(2)
    }

}
