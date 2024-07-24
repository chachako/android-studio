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

import com.android.adblib.AdbDeviceFailResponseException
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbFeatures
import com.android.adblib.AdbSession
import com.android.adblib.AppProcessEntry
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.adbLogger
import com.android.adblib.getOrPutSynchronized
import com.android.adblib.hasAvailableFeature
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
 * A thread-safe [StateFlow] of active [app process entries][TrackAppItem.entries] for
 * a given [ConnectedDevice].
 *
 * The implementation uses a single underlying [AdbDeviceServices.trackApp] invocation
 * that is shared by all collectors of the flow.
 *
 * Note: The caller is responsible for calling [ConnectedDevice.isTrackAppSupported]
 * to make sure this operation is supported by the device. If the operation is not
 * supported, this function throws [AdbDeviceFailResponseException].
 */
suspend fun ConnectedDevice.trackAppStateFlow(): StateFlow<TrackAppItem> {
    return cache.getOrPutSynchronized(TrackAppKey) {
        TrackApp(this)
    }.stateFlow()
}

/**
 * An entry of the [trackAppStateFlow], containing the [list][entries] of [AppProcessEntry].
 *
 * [isEndOfFlow] is emitted at the very last entry if the flow is inactive, i.e. the device
 * is not connected anymore.
 */
class TrackAppItem(val entries: List<AppProcessEntry>) {

    /**
     * Whether this is the very first entry of the flow, emitted before the actual list
     * of processes has been retrieved for the first time.
     */
    val isStartOfFlow: Boolean
        get() = (this === TrackApp.StartOfFlow)

    /**
     * Whether the flow is still active, typically `true` when the device has disconnected.
     */
    val isEndOfFlow: Boolean
        get() = (this === TrackApp.EndOfFlow)

    override fun toString(): String {
        val desc = when {
            isStartOfFlow -> "StartOfFlow"
            isEndOfFlow -> "EndOfFlow"
            else -> "$entries"
        }
        return "${this::class.simpleName}($desc)"
    }
}

private val TrackAppKey = CoroutineScopeCache.Key<TrackApp>("TrackApp")

private class TrackApp(private val device: ConnectedDevice) {

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

    suspend fun stateFlow(): StateFlow<TrackAppItem> {
        if (!device.isTrackAppSupported()) {
            // Emit a single "error" entry since operation is not supported
            throw AdbDeviceFailResponseException(
                device.selector,
                "track-app",
                "track-app command is not supported by the device"
            )
        } else {
            // Note: We rely on "lazy" to ensure the tracking coroutine is launched only once
            trackProcessesJob
            return stateFlowField
        }
    }

    private suspend fun trackProcesses() {
        device.serviceFlowToMutableStateFlow(
            serviceInvocation = { device ->
                device.session.deviceServices.trackApp(device.selector).map { list ->
                    TrackAppItem(list)
                }
            },
            destinationStateFlow = mutableFlow,
            lastValue = EndOfFlow,
            retryValue = Empty,
            retryDelay = session.property(AdbLibToolsProperties.TRACK_APP_RETRY_DELAY),
        )
    }

    companion object {
        val Empty = TrackAppItem(emptyList())
        val StartOfFlow = TrackAppItem(emptyList())
        val EndOfFlow = TrackAppItem(emptyList())
    }
}

/**
 * Whether [trackAppStateFlow] is supported
 */
suspend fun ConnectedDevice.isTrackAppSupported(): Boolean {
    // Note: "track-app" is only supported on API 31+ (Android "S"), but there
    // is an official feature for it.
    return hasAvailableFeature(AdbFeatures.TRACK_APP)
}
