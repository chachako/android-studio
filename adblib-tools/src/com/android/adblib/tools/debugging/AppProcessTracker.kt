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
package com.android.adblib.tools.debugging

import com.android.adblib.AdbDeviceServices
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.DeviceState
import com.android.adblib.scope
import com.android.adblib.tools.debugging.impl.AppProcessTrackerImpl
import com.android.adblib.flowWhenOnline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration

/**
 * Tracks the list of active [AppProcess] processes on a given [ConnectedDevice].
 *
 * See the [appProcessFlow] property for the list of [processes][AppProcess] exposed
 * as a [StateFlow].
 *
 * Note: To prevent running multiple [AdbDeviceServices.trackApp] services concurrently,
 * use the [ConnectedDevice.appProcessTracker] or [ConnectedDevice.appProcessFlow]
 * extensions to access the [AppProcessTracker] for a given [ConnectedDevice].
 */
interface AppProcessTracker {

    /**
     * The [ConnectedDevice] this [AppProcessTracker] is attached to.
     */
    val device: ConnectedDevice

    /**
     * A [CoroutineScope] tied to the lifecycle of this [AppProcessTracker], which is typically
     * tied to the lifecycle of the corresponding [device].
     */
    val scope: CoroutineScope

    /**
     * The [StateFlow] of active [AppProcess] for this [device].
     *
     * Every time a process is created or terminated, a new (immutable) [List] is emitted
     * to the [StateFlow]. However, it is guaranteed that [AppProcess] instances contained
     * in emitted lists remain the same for processes that remain active.
     *
     * Note: Once [scope] has completed, this [StateFlow] value is an empty list, and there will
     * be no additional updates to the flow.
     */
    val appProcessFlow: StateFlow<List<AppProcess>>

    companion object {

        /**
         * Returns a [AppProcessTracker] instance that actively tracks "app processes"
         * of a given [device]. Use the [AppProcessTracker.appProcessFlow] property to access
         * or collect the list of active [AppProcess].
         */
        fun create(device: ConnectedDevice): AppProcessTracker {
            return AppProcessTrackerImpl(device)
        }
    }
}

/**
 * Device cache key for [appProcessTracker]
 */
@Suppress("PrivatePropertyName")
private val APP_PROCESS_TRACKER_KEY =
    CoroutineScopeCache.Key<AppProcessTracker>("AppProcessTracker device cache entry")

/**
 * The default [AppProcessTracker] for this device, giving access to the list of [AppProcessTracker]
 * currently active on the device (through a [StateFlow]).
 *
 * See also [ConnectedDevice.appProcessFlow] to access a more user-friendly version of
 * the [StateFlow], i.e. a [Flow] that tracks the lifetime of the [ConnectedDevice].
 */
val ConnectedDevice.appProcessTracker: AppProcessTracker
    get() = this.cache.getOrPut(APP_PROCESS_TRACKER_KEY) {
        AppProcessTracker.create(this)
    }

@Suppress("PrivatePropertyName")
private val APP_PROCESS_TRACKER_RETRY_DELAY = Duration.ofSeconds(2)

/**
 * The [Flow] of [processes][AppProcess] currently active on the device.
 *
 * The [Flow] starts when the device becomes [DeviceState.ONLINE] and ends
 * when the device scope is disconnected.
 */
val ConnectedDevice.appProcessFlow: Flow<List<AppProcess>>
    get() = flowWhenOnline(APP_PROCESS_TRACKER_RETRY_DELAY) {
        it.appProcessTracker.appProcessFlow
    }
