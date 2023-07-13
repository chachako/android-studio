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
package com.android.build.api.component.impl

import com.google.common.truth.Truth
import org.gradle.api.Task
import org.junit.Test
import org.mockito.Mockito

class LifecycleTasksImplTest {

    @Test
    fun testUnused() {
        val anchorTasks = LifecycleTasksImpl()
        Truth.assertThat(anchorTasks.hasPreBuildActions()).isFalse()
    }

    @Test
    fun singleUsage() {
        val anchorTasks = LifecycleTasksImpl()
        val task = Mockito.mock(Task::class.java)
        val preBuildTask = Mockito.mock(Task::class.java)
        anchorTasks.registerPreBuild(task)

        Truth.assertThat(anchorTasks.hasPreBuildActions()).isTrue()
        anchorTasks.invokePreBuildActions(preBuildTask)

        Mockito.verify(preBuildTask).dependsOn(task)
    }

    @Test
    fun multipleUsage() {
        val anchorTasks = LifecycleTasksImpl()
        val taskOne = Mockito.mock(Task::class.java)
        val taskTwo = Mockito.mock(Task::class.java)
        val preBuildTask = Mockito.mock(Task::class.java)
        anchorTasks.registerPreBuild(taskOne, taskTwo)

        Truth.assertThat(anchorTasks.hasPreBuildActions()).isTrue()
        anchorTasks.invokePreBuildActions(preBuildTask)

        val array = ArrayList<Any>().also {
            it.add(taskOne)
            it.add(taskTwo)
        }
        Mockito.verify(preBuildTask).dependsOn(taskOne, taskTwo)
    }

    @Test
    fun multipleSingleUsage() {
        val anchorTasks = LifecycleTasksImpl()
        val taskOne = Mockito.mock(Task::class.java)
        val taskTwo = Mockito.mock(Task::class.java)
        val preBuildTask = Mockito.mock(Task::class.java)

        anchorTasks.registerPreBuild(taskOne)
        Truth.assertThat(anchorTasks.hasPreBuildActions()).isTrue()

        anchorTasks.registerPreBuild(taskTwo)
        Truth.assertThat(anchorTasks.hasPreBuildActions()).isTrue()

        anchorTasks.invokePreBuildActions(preBuildTask)

        Mockito.verify(preBuildTask).dependsOn(taskOne, taskTwo)
    }
}

