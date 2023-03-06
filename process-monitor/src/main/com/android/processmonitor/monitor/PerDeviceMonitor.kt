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

import com.android.adblib.AdbLogger
import com.android.adblib.withPrefix
import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.common.ProcessTracker
import com.android.processmonitor.utils.RetainingMap
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * Monitors process names on a devices.
 *
 * If the merged trackers have conflicting data, it will prefer data that has a non-null
 * applicationId over a null applicationId.
 */
internal class PerDeviceMonitor(
    parentScope: CoroutineScope,
    logger: AdbLogger,
    maxProcessRetention: Int,
    vararg processTrackers: ProcessTracker,
) : Closeable {

    private val logger = logger.withPrefix("${this::class.simpleName}: ")

    @VisibleForTesting
    val processTracker = when (processTrackers.size) {
        1 -> processTrackers[0]
        else -> MergedTracker(processTrackers.asList())
    }

    private val scope: CoroutineScope =
        CoroutineScope(parentScope.coroutineContext + SupervisorJob())

    private val processes = RetainingMap<Int, ProcessNames>(maxProcessRetention)

    fun start() {
        scope.launch {
            processTracker.trackProcesses().collect {
                when (it) {
                    is ProcessRemoved -> handleProcessRemoved(it)
                    is ProcessAdded -> handleProcessAdded(it)
                }
            }
        }
    }

    private fun handleProcessRemoved(it: ProcessRemoved) {
        logger.debug { "Removing ${it.pid}" }
        processes.remove(it.pid)
    }

    private fun handleProcessAdded(e: ProcessAdded) {
        val pid = e.pid
        val names = e.toProcessNames()
        val processNames = processes[pid]

        val newNames = when {
            processNames?.processName == e.processName && e.applicationId == null -> processNames
            else -> names
        }
        if (logger.minLevel <= AdbLogger.Level.VERBOSE) {
            // This is just for debugging purposes, so we can understand when things go wrong
            when {
                // Trivial case, not preexisting match for pid.
                processNames == null -> logger.verbose { "New process added" }

                // Preexisting match but, it's a different process with the same pid
                e.processName != processNames.processName ->
                    logger.verbose { "New process with same pid" }

                // Preexisting match is the same process but current result is doesn't have an
                // app id, so we ignore it.
                e.applicationId == null ->
                    logger.verbose { "Existing process without applicationId" }

                // Preexisting match is the same process and the current result does have an
                // app id, so we ignore replace the existing result with the new one
                else -> logger.verbose { "Existing process with an applicationId" }
            }
        }
        logger.debug { "Adding $newNames" }
        // We always update the map with something, just in case the process dies and happened to
        // respawn with the same pid (highly unlikely)
        processes[pid] = newNames
    }

    fun getProcessNames(pid: Int): ProcessNames? = processes[pid]

    override fun close() {
        scope.cancel()
    }

    private class MergedTracker(private val trackers: List<ProcessTracker>) : ProcessTracker {

        override suspend fun trackProcesses(): Flow<ProcessEvent> =
            merge(*trackers.map { it.trackProcesses() }.toTypedArray())
    }
}
