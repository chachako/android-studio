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
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.inject.Inject

internal class ExpandArtProfileWildcardsTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: ExpandArtProfileWildcardsTask
    private lateinit var expandedArtProfile: File
    private lateinit var project: Project
    private lateinit var projectClasses: ConfigurableFileCollection
    private lateinit var mergedArtProfile: RegularFileProperty

    abstract class ExpandArtProfileWildcardsTaskForTest @Inject constructor(
        testWorkerExecutor: WorkerExecutor,
    ) : ExpandArtProfileWildcardsTask() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        projectClasses = project.files()
        mergedArtProfile =
            project.objects.fileProperty().also { it.set(File("/does/not/exist")) }
        task = project.tasks.register(
            "expandArtProfileWildcardsTask",
            ExpandArtProfileWildcardsTaskForTest::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder()),
        ).get()
        task.projectClasses.from(projectClasses)
        expandedArtProfile = temporaryFolder.newFile()
        task.expandedArtProfile.set(expandedArtProfile)
        task.analyticsService.set(FakeNoOpAnalyticsService())
    }

    @Test
    fun `test with no files`() {
        task.projectClasses.from(project.files())
        task.taskAction()
        PathSubject.assertThat(expandedArtProfile).doesNotExist()
    }

    @Test
    fun `test single file`() {
        val profile = temporaryFolder.newFile("baseline-prof.txt")
        profile.writeText("L*;")
        task.mergedArtProfile.set(profile)
        val classFile = temporaryFolder.newFile("Hello.class")
        projectClasses.from(File(classFile.parent))
        task.taskAction()
        assertThat(task.expandedArtProfile.get().asFile.readText()).isEqualTo("LHello;\n")
    }
}
