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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.AppProcessEntry
import com.android.adblib.ConnectedDevice
import com.android.adblib.adbLogger
import com.android.adblib.scope
import com.android.adblib.tools.debugging.AppProcess
import com.android.adblib.tools.debugging.AppProcessTracker
import com.android.adblib.tools.debugging.trackAppStateFlow
import com.android.adblib.tools.debugging.utils.logIOCompletionErrors
import com.android.adblib.utils.createChildScope
import com.android.adblib.withPrefix
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class AppProcessTrackerImpl(
    override val device: ConnectedDevice
) : AppProcessTracker {

    private val logger = adbLogger(device.session)
        .withPrefix("${device.session} - $device -")

    private val processesMutableFlow = MutableStateFlow<List<AppProcess>>(emptyList())

    private val trackProcessesJob: Job by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        scope.launch {
            runCatching {
                trackProcesses()
            }.onFailure { throwable ->
                logger.logIOCompletionErrors(throwable)
            }
        }
    }

    override val scope = device.scope.createChildScope(isSupervisor = true)

    override val appProcessFlow = processesMutableFlow.asStateFlow()
        get() {
            // Note: We rely on "lazy" to ensure the tracking coroutine is launched only once
            trackProcessesJob
            return field
        }

    private suspend fun trackProcesses() {
        val processMap = ProcessMap<AppProcessImpl>()
        device.trackAppStateFlow().collect { trackAppItem ->
            updateProcessMap(processMap, trackAppItem.entries)
            processesMutableFlow.emit(processMap.values.toList())
        }
    }

    private fun updateProcessMap(
        appProcessMap: ProcessMap<AppProcessImpl>,
        newAppProcessEntryList: List<AppProcessEntry>
    ) {
        // Ask the jdwp process manager for the jdwp process instances so that we can use them
        // in the corresponding "AppProcess" instances
        val jdwpPids = newAppProcessEntryList.filter { it.debuggable }.map { it.pid }.toSet()
        val jdwpProcessMap = device.jdwpProcessManager.addProcesses(jdwpPids)

        val appProcessPids = newAppProcessEntryList.map { it.pid }
        appProcessMap.update(appProcessPids, valueFactory = { pid ->
            logger.debug { "Adding process $pid to process map" }

            val entry = newAppProcessEntryList.first { it.pid == pid }
            AppProcessImpl(device, entry, jdwpProcessMap[pid])
        })
    }
}
