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
package com.android.adblib.tools.debugging

import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.DeviceState
import com.android.adblib.flowWhenOnline
import com.android.adblib.property
import com.android.adblib.scope
import com.android.adblib.tools.AdbLibToolsProperties.JDWP_PROCESS_TRACKER_RETRY_DELAY
import com.android.adblib.tools.debugging.impl.JdwpProcessTrackerImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks the list of active [JdwpProcess] processes on a given [ConnectedDevice].
 *
 * See the [processesFlow] property for the list of [processes][JdwpProcess] exposed
 * as a [StateFlow].
 */
interface JdwpProcessTracker {

    /**
     * The [ConnectedDevice] this [JdwpProcessTracker] is attached to.
     */
    val device: ConnectedDevice

    /**
     * A [CoroutineScope] tied to the lifecycle of this [JdwpProcessTracker], which is typically
     * tied to the lifecycle of the corresponding [device].
     */
    val scope: CoroutineScope

    /**
     * The [StateFlow] of active [JdwpProcess] for this [device].
     *
     * Every time a process is created or terminated, a new (immutable) [List] is emitted
     * to the [StateFlow]. However, it is guaranteed that [JdwpProcess] instances contained
     * in emitted lists remain the same for processes that remain active.
     *
     * Note: Once [scope] has completed, this [StateFlow] value is an empty list, and there will
     * be no additional updates to the flow.
     */
    val processesFlow: StateFlow<List<JdwpProcess>>

    companion object {

        /**
         * Returns a [JdwpProcessTracker] instance that actively tracks JDWP processes
         * of a given [device]. Use the [JdwpProcessTracker.processesFlow] property to access
         * or collect the list of active [JdwpProcess].
         */
        fun create(device: ConnectedDevice): JdwpProcessTracker {
            return JdwpProcessTrackerImpl(device)
        }
    }
}

fun Throwable.rethrowCancellation() {
    if (this is CancellationException) {
        throw this
    }
}

/**
 * Device cache key for [jdwpProcessTracker]
 */
@Suppress("PrivatePropertyName")
private val JDWP_PROCESS_TRACKER_KEY = CoroutineScopeCache.Key<JdwpProcessTracker>("JdwpProcessTracker device cache entry")

/**
 * The default [JdwpProcessTracker] for this device, giving access to the list of [JdwpProcess]
 * currently active on the device (through a [StateFlow]).
 *
 * See also [ConnectedDevice.jdwpProcessFlow] to access a more user-friendly version of
 * the [StateFlow], i.e. a [Flow] that tracks the lifetime of the [ConnectedDevice].
 */
val ConnectedDevice.jdwpProcessTracker: JdwpProcessTracker
    get() {
        return this.cache.getOrPut(JDWP_PROCESS_TRACKER_KEY) {
            JdwpProcessTracker.create(this)
        }
    }

/**
 * The [Flow] of [processes][JdwpProcess] currently active on the device.
 *
 * The [Flow] starts when the device becomes [DeviceState.ONLINE] and ends
 * when the device is disconnected [DeviceState.DISCONNECTED].
 */
val ConnectedDevice.jdwpProcessFlow : Flow<List<JdwpProcess>>
    get() = flowWhenOnline(session.property(JDWP_PROCESS_TRACKER_RETRY_DELAY)) {
        it.jdwpProcessTracker.processesFlow
    }
