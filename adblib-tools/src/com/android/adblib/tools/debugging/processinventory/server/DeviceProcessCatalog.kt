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
package com.android.adblib.tools.debugging.processinventory.server

import com.android.adblib.AdbSession
import com.android.adblib.IsThreadSafe
import com.android.adblib.adbLogger
import com.android.adblib.tools.debugging.AtomicStateFlow
import com.android.adblib.tools.debugging.processinventory.protos.ProcessInventoryServerProto
import com.android.adblib.tools.debugging.processinventory.protos.ProcessInventoryServerProto.*
import com.android.adblib.withPrefix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Keeps track of the [List] of [processes][JdwpProcessInfo] of a given device [DeviceId]
 */
@IsThreadSafe
internal class DeviceProcessCatalog(session: AdbSession, val deviceId: DeviceId) {

    private val logger = adbLogger(session)
        .withPrefix("adbSessionId=${deviceId.adbSessionId}, " +
                            "serialNumber=${deviceId.serialNumber} - ")

    private val processListAtomicStateFlow = AtomicStateFlow(MutableStateFlow(ProcessList()))

    private val processListFlow = processListAtomicStateFlow.asStateFlow()

    /**
     * Returns a [Flow] of [ProcessInventoryServerProto.ProcessUpdates] that emits a new item
     * everytime anything changes in the [list of processes][ProcessList] tracked by
     * this [DeviceProcessCatalog].
     */
    fun trackProcesses(): Flow<ProcessInventoryServerProto.ProcessUpdates> = flow {
        val collector = this
        logger.debug { "trackProcesses(): entering flow" }

        // Capture known list (snapshot) so that we can compute deltas over time
        var currentList = processListFlow.value

        // Send known list so that
        logger.debug { "trackProcesses(): emitting initial list of processes" }
        collector.emitInitialProcessList(currentList)

        // Note: We rely on any change to any process properties results in a new list
        // in the flow.
        processListFlow.collect {
            val newList = it
            val updates = computeProcessListUpdates(currentList, newList)
            logger.verbose { "Computed updates to processes: isEmpty=${updates.isEmpty()}" }

            // Don't send anything if there are no updates
            if (!updates.isEmpty()) {
                collector.emitProcessListUpdates(updates)
                currentList = newList
            }
        }
    }

    /**
     * Updates the [list of processes][ProcessList] tracked by this [DeviceProcessCatalog].
     *
     * Updates are reflected in the [Flow] returned by [trackProcesses].
     */
    fun updateProcessList(processUpdates: ProcessInventoryServerProto.ProcessUpdates) {
        updateProcessListStateFlow { oldProcessList ->
            // Create a new process list from the updates we are receiving
            // * Remove "deleted" processes
            // * Merge info for existing processes
            // * Add new processes "as-is"
            val map = oldProcessList.processes.associateBy { it.pid }.toMutableMap()
            processUpdates.processUpdateList.forEach { processUpdate ->
                when {
                    processUpdate.hasProcessTerminatedPid() -> {
                        val pid = processUpdate.processTerminatedPid
                        logger.debug { "Process $pid has exited" }
                        map.remove(pid)
                    }

                    processUpdate.hasProcessUpdated() -> {
                        val newJdwpProcessInfo = processUpdate.processUpdated
                        logger.debug { "Process ${newJdwpProcessInfo.pid} has been updated: $newJdwpProcessInfo" }
                        val currentJdwpProcessInfo =
                            map.computeIfAbsent(newJdwpProcessInfo.pid) { newJdwpProcessInfo }
                        map[newJdwpProcessInfo.pid] =
                            currentJdwpProcessInfo.mergeWith(newJdwpProcessInfo)
                    }
                }
            }
            ProcessList(map.values.sortedBy { it.pid })
        }
    }

    private suspend fun FlowCollector<ProcessInventoryServerProto.ProcessUpdates>.emitInitialProcessList(
        processList: ProcessList
    ) {
        val addProcesses = ProcessUpdates(
            added = processList.processes,
            removed = emptyList(),
            updated = emptyList()
        )
        emitProcessListUpdates(addProcesses)
    }

    private suspend fun FlowCollector<ProcessInventoryServerProto.ProcessUpdates>.emitProcessListUpdates(
        updates: ProcessUpdates
    ) {
        logger.debug {
            "emitProcessListUpdates: emitting updates (" +
                    "added count=${updates.added.size}, " +
                    "updated count=${updates.updated.size}, " +
                    "removed count=${updates.removed.size})"
        }
        logger.verbose { "emitProcessListUpdates: added=${updates.added}" }
        logger.verbose { "emitProcessListUpdates: updated=${updates.updated}" }
        logger.verbose { "emitProcessListUpdates: removed=${updates.removed}" }

        val response = ProcessInventoryServerProto.ProcessUpdates
            .newBuilder()
            .addAllProcessUpdate(
                updates.added.map { processInfo ->
                    ProcessUpdate
                        .newBuilder()
                        .setProcessUpdated(processInfo)
                        .build()
                } + updates.removed.map { processInfo ->
                    ProcessUpdate
                        .newBuilder()
                        .setProcessTerminatedPid(processInfo.pid)
                        .build()
                } + updates.updated.map { processInfo ->
                    ProcessUpdate
                        .newBuilder()
                        .setProcessUpdated(processInfo)
                        .build()
                }
            )
            .build()

        emit(response)
    }

    private class ProcessUpdates(
        val added: List<JdwpProcessInfo>,
        val removed: List<JdwpProcessInfo>,
        val updated: List<JdwpProcessInfo>,
    ) {

        fun isEmpty(): Boolean {
            return added.isEmpty() && removed.isEmpty() && updated.isEmpty()
        }
    }

    private fun computeProcessListUpdates(
        currentList: ProcessList,
        newList: ProcessList
    ): ProcessUpdates {
        val oldMap = currentList.processes.associateBy { it.pid }
        val newMap = newList.processes.associateBy { it.pid }
        val addedPids = newMap.keys subtract oldMap.keys
        val removedPids = oldMap.keys subtract newMap.keys
        val updatedPids = (oldMap.keys intersect newMap.keys).filter { pid ->
            val oldInfo = oldMap[pid]
            val newInfo = newMap[pid]
            oldInfo != newInfo
        }
        return ProcessUpdates(
            added = newMap.filter { addedPids.contains(it.key) }.values.toList(),
            removed = oldMap.filter { removedPids.contains(it.key) }.values.toList(),
            updated = newMap.filter { updatedPids.contains(it.key) }.values.toList(),
        )
    }

    private fun updateProcessListStateFlow(update: (ProcessList) -> ProcessList) {
        processListAtomicStateFlow.update { currentList ->
            update(currentList).also { newList ->
                logger.debug {
                    "Updating process list from ${currentList.processes.size} element(s) " +
                            "to ${newList.processes.size} element(s)"
                }
            }
        }
    }

    /**
     * A simple wrapper around a [List] of [JdwpProcessInfo]
     */
    private data class ProcessList(
        val processes: List<JdwpProcessInfo> = emptyList()
    )

    /**
     * Returns a [JdwpProcessInfo] instances resulting from the merging of properties of this
     * [JdwpProcessInfo] with [other].
     */
    private fun JdwpProcessInfo.mergeWith(other: JdwpProcessInfo): JdwpProcessInfo {
        return JdwpProcessInfo.newBuilder(this)
            .also { proto ->
                proto.pid = other.pid
                if (other.hasProcessName()) proto.processName = other.processName
                if (other.hasProcessName()) proto.packageName = other.packageName
                if (other.hasUserId()) proto.userId = other.userId
                if (other.hasAbi()) proto.abi = other.abi
                if (other.hasVmIdentifier()) proto.vmIdentifier = other.vmIdentifier
                if (other.hasJvmFlags()) proto.jvmFlags = other.jvmFlags
                if (other.hasNativeDebuggable()) proto.nativeDebuggable = other.nativeDebuggable
                if (other.hasWaitPacketReceived()) proto.waitPacketReceived = other.waitPacketReceived
                if (other.hasFeatures()) proto.features = other.features
                if (other.hasCollectionStatus()) proto.collectionStatus = proto.collectionStatus.mergeWith(other.collectionStatus)
                if (other.hasExternalDebuggerStatus()) proto.externalDebuggerStatus = proto.externalDebuggerStatus.mergeWith(other.externalDebuggerStatus)
            }
            .build()
    }

    private fun JdwpProcessInfo.CollectionStatus.mergeWith(other: JdwpProcessInfo.CollectionStatus): JdwpProcessInfo.CollectionStatus {
        return JdwpProcessInfo.CollectionStatus.newBuilder(this)
            .also { proto ->
                proto.completed = other.completed
                if (other.hasException()) proto.exception = other.exception
            }
            .build()
    }

    private fun JdwpProcessInfo.JavaDebuggerStatus.mergeWith(other: JdwpProcessInfo.JavaDebuggerStatus): JdwpProcessInfo.JavaDebuggerStatus {
        return JdwpProcessInfo.JavaDebuggerStatus.newBuilder(this)
            .also { proto ->
                proto.waitingForDebugger = other.waitingForDebugger
                proto.debuggerIsAttached = other.debuggerIsAttached
                if (other.hasSocketAddress()) proto.socketAddress = other.socketAddress
            }
            .build()
    }
}
