/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.attribution.BuildAttributionService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.TaskCategory
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.attribution.BuildAnalyzerTaskCategoryIssue
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
@BuildAnalyzer(TaskCategory.MISC)
abstract class BuildAnalyzerTask: NonIncrementalTask() {

    @get:Input
    abstract val issuesToReport: ListProperty<BuildAnalyzerTaskCategoryIssue>

    @get:Internal
    abstract val buildAnalyzerService: Property<BuildAttributionService>

    override fun doTaskAction() {
        issuesToReport.get().forEach {
            buildAnalyzerService.get().reportBuildAnalyzerIssue(it)
        }
    }

    class CreationAction(
        val global: GlobalTaskCreationConfig
    ): TaskCreationAction<BuildAnalyzerTask>() {

        override val name: String
            get() = "reportBuildAnalyzerIssues"
        override val type: Class<BuildAnalyzerTask>
            get() = BuildAnalyzerTask::class.java

        override fun configure(task: BuildAnalyzerTask) {
            task.configureVariantProperties("", task.project.gradle.sharedServices)
            task.buildAnalyzerService.setDisallowChanges(
                getBuildService(global.services.buildServiceRegistry)
            )
            task.issuesToReport.setDisallowChanges(
                global.buildAnalyzerIssueReporter!!.issues
            )

            // We need to always report the issues. As the task doesn't have any outputs, this
            // shouldn't affect any other task.
            task.outputs.upToDateWhen { false }
        }
    }
}
