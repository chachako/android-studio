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

import com.android.buildanalyzer.common.TaskCategory
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class TaskCategoryTest {

    @Test
    fun `all primary task categories have a description`() {
        TaskCategory.values().filter { it.isPrimary }.forEach {
            assertWithMessage("All primary task categories should have a description as it will be " +
                    "shown to users on the IDE side.")
                .that(it.description)
                .isNotEmpty()
        }
    }
}
