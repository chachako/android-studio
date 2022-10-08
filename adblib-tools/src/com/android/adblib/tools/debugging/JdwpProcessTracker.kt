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

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.scope
import com.android.adblib.tools.debugging.impl.JdwpProcessTrackerImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
        fun create(session: AdbSession, device: ConnectedDevice): JdwpProcessTracker {
            return JdwpProcessTrackerImpl(session, device)
        }
    }
}

fun Throwable.rethrowCancellation() {
    if (this is CancellationException) {
        throw this
    }
}
