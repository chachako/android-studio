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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.ConnectedDevice
import com.android.adblib.adbLogger
import com.android.adblib.scope
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.JdwpProcessTracker
import com.android.adblib.tools.debugging.trackJdwpStateFlow
import com.android.adblib.utils.createChildScope
import com.android.adblib.utils.toImmutableList
import com.android.adblib.withPrefix
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class JdwpProcessTrackerImpl(
    override val device: ConnectedDevice
) : JdwpProcessTracker {

    private val logger = adbLogger(device.session)
        .withPrefix("${device.session} - $device - ")

    private val processesMutableFlow = MutableStateFlow<List<JdwpProcess>>(emptyList())

    private val trackProcessesJob: Job by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        scope.launch {
            trackProcesses()
        }
    }

    override val scope = device.scope.createChildScope(isSupervisor = true)

    override val processesFlow = processesMutableFlow.asStateFlow()
        get() {
            // Note: We rely on "lazy" to ensure the tracking coroutine is launched only once
            trackProcessesJob
            return field
        }

    private suspend fun trackProcesses() {
        device.trackJdwpStateFlow().collect { trackJdwpItem ->
            val processIds = trackJdwpItem.processIds.toSet()
            val processMap = device.jdwpProcessManager.addProcesses(processIds)
            processMap.values.toImmutableList().also { jdwpProcessList ->
                logger.verbose { "Emitting new list of JDWP processes: $jdwpProcessList" }
                processesMutableFlow.emit(jdwpProcessList)
            }
        }
    }
}
