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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.inject.Inject

/**
 * Unit tests for [ExtractVersionControlInfoTask]
 */
internal class ExtractVersionControlInfoTaskTest {
    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: ExtractVersionControlInfoTask
    private lateinit var vcInfoFile: File

    abstract class ExtractVersionControlInfoTaskForTest @Inject constructor(
        testWorkerExecutor: WorkerExecutor): ExtractVersionControlInfoTask() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
            "extractVersionControlInfoTask",
            ExtractVersionControlInfoTaskForTest::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder())
        ).get()
        task.analyticsService.set(FakeNoOpAnalyticsService())
        vcInfoFile = temporaryFolder.newFile()
    }

    @Test
    fun gitHeadContainsRef() {
        val sha = "40cde1bb54e7895b717a55931e4421cbab41234e"
        task.vcInfoFile.set(vcInfoFile)

        val gitFolder = temporaryFolder.newFolder(".git")
        val headFile = FileUtils.join(gitFolder, "/HEAD")
        FileUtils.createFile(headFile, "ref: refs/heads/branchName")

        val headsFolder = temporaryFolder.newFolder(".git/refs/heads")
        val branchFile = FileUtils.join(headsFolder, "/branchName")
        FileUtils.createFile(branchFile, sha)

        task.gitHeadFile.set(headFile)
        task.gitHeadFile.disallowChanges()
        task.gitRefsDir.set(branchFile.parentFile)
        task.gitRefsDir.disallowChanges()
        task.taskAction()

        PathSubject.assertThat(vcInfoFile).exists()
        Truth.assertThat(vcInfoFile.readText()).isEqualTo(
            """
                repositories {
                  system: GIT
                  local_root_path: "${'$'}PROJECT_DIR"
                  revision: "$sha"
                }

            """.trimIndent())
    }

    @Test
    fun gitHeadContainsSha() {
        val sha = "40cde1bb54e7895b717a55931e4421cbab41234e"
        task.vcInfoFile.set(vcInfoFile)

        val gitFolder = temporaryFolder.newFolder(".git")
        val headFile = FileUtils.join(gitFolder, "/HEAD")
        FileUtils.createFile(headFile, sha)

        task.gitHeadFile.set(headFile)
        task.gitHeadFile.disallowChanges()
        task.taskAction()

        PathSubject.assertThat(vcInfoFile).exists()
        Truth.assertThat(vcInfoFile.readText()).isEqualTo(
            """
                repositories {
                  system: GIT
                  local_root_path: "${'$'}PROJECT_DIR"
                  revision: "$sha"
                }

            """.trimIndent())
    }
}
