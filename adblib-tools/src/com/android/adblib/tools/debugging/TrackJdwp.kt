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
package com.android.adblib.tools.debugging

import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbFailResponseException
import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.ProcessIdList
import com.android.adblib.adbLogger
import com.android.adblib.emptyProcessIdList
import com.android.adblib.getOrPutSynchronized
import com.android.adblib.property
import com.android.adblib.scope
import com.android.adblib.selector
import com.android.adblib.tools.AdbLibToolsProperties
import com.android.adblib.tools.debugging.utils.logIOCompletionErrors
import com.android.adblib.tools.debugging.utils.serviceFlowToMutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A thread-safe [StateFlow] of active [JDWP process IDs][TrackJdwpItem.processIds] for
 * a given [ConnectedDevice].
 *
 * The implementation uses a single underlying [AdbDeviceServices.trackJdwp] invocation
 * that is shared by all collectors of the flow.
 *
 * This function throws [AdbFailResponseException] is [ConnectedDevice.isTrackAppSupported]
 * is `false`.
 */
suspend fun ConnectedDevice.trackJdwpStateFlow(): StateFlow<TrackJdwpItem> {
    return cache.getOrPutSynchronized(TrackJdwpKey) {
        TrackJdwp(this)
    }.stateFlow()
}

/**
 * An entry of the [trackJdwpStateFlow], containing the list of JDWP process IDs [processIds].
 *
 * [isEndOfFlow] is emitted at the very last entry if the flow is inactive, i.e. the device
 * is not connected anymore.
 */
class TrackJdwpItem(val processIds: ProcessIdList) {

    /**
     * Whether this is the very first entry of the flow, emitted once before the actual list
     * of processes has been retrieved for the first time.
     */
    val isStartOfFlow: Boolean
        get() = (this === TrackJdwp.StartOfFlow)

    /**
     * Whether the flow is still active, typically `true` when the device has disconnected.
     */
    val isEndOfFlow: Boolean
        get() = (this === TrackJdwp.EndOfFlow)

    override fun toString(): String {
        val desc = when {
            isStartOfFlow -> "StartOfFlow"
            isEndOfFlow -> "EndOfFlow"
            else -> "$processIds"
        }
        return "${this::class.simpleName}($desc)"
    }
}

private val TrackJdwpKey = CoroutineScopeCache.Key<TrackJdwp>("TrackJdwp")

private class TrackJdwp(private val device: ConnectedDevice) {
    private val session: AdbSession
        get() = device.session

    private val scope: CoroutineScope
        get() = device.scope

    private val logger = adbLogger(session)

    private val mutableFlow = MutableStateFlow(StartOfFlow)

    private val stateFlowField = mutableFlow.asStateFlow()

    private val trackProcessesJob: Job by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        scope.launch {
            runCatching {
                trackProcesses()
            }.onFailure { throwable ->
                logger.logIOCompletionErrors(throwable)
            }
        }
    }

    /**
     * Returns the [StateFlow] instance
     *
     * Note: `suspend` for future proofing and consistency with [TrackApp.stateFlow]
     */
    @Suppress("RedundantSuspendModifier")
    suspend fun stateFlow(): StateFlow<TrackJdwpItem> {
        // Note: We rely on "lazy" to ensure the tracking coroutine is launched only once
        trackProcessesJob
        return stateFlowField
    }

    private suspend fun trackProcesses() {
        device.serviceFlowToMutableStateFlow(
            serviceInvocation = { device ->
                device.session.deviceServices.trackJdwp(device.selector).map { list ->
                    TrackJdwpItem(list)
                }
            },
            destinationStateFlow = mutableFlow,
            lastValue = EndOfFlow,
            retryValue = Empty,
            retryDelay = session.property(AdbLibToolsProperties.TRACK_JDWP_RETRY_DELAY)
        )
    }

    companion object {
        val StartOfFlow = TrackJdwpItem(emptyProcessIdList())
        val EndOfFlow = TrackJdwpItem(emptyProcessIdList())
        val Empty = TrackJdwpItem(emptyProcessIdList())
    }

}
