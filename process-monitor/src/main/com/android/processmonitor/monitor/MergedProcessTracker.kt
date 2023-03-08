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
package com.android.processmonitor.monitor

import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.common.ProcessTracker
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/** A [ProcessTracker] that merges multiple trackers into one flow */
internal class MergedProcessTracker(
    @VisibleForTesting
    vararg val trackers: ProcessTracker,
) : ProcessTracker {

    override suspend fun trackProcesses(): Flow<ProcessEvent> =
        merge(*trackers.map { it.trackProcesses() }.toTypedArray())
}
