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
import com.android.adblib.tools.debugging.impl.JdwpProcessAllocationTrackerImpl
import com.android.adblib.tools.debugging.impl.JdwpProcessProfilerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * A JDWP process tracked by [JdwpProcessTracker]. Each instance has a [pid] and a [StateFlow]
 * of [JdwpProcessProperties], corresponding to the changes made to the process during a
 * JDWP session (e.g. [JdwpProcessProperties.packageName]).
 *
 * A [JdwpProcess] instance becomes invalid when the corresponding process on the device
 * is terminated, or when the device is disconnected.
 */
interface JdwpProcess {

    /**
     * The [ConnectedDevice] this process runs on.
     */
    val device: ConnectedDevice

    /**
     * The process ID
     */
    val pid: Int

    /**
     * The [CoroutineScope] whose lifetime matches the lifetime of the process on the device.
     * This [scope] can be used for example when collecting the [propertiesFlow].
     */
    val scope: CoroutineScope
        get() = cache.scope

    /**
     * Returns a [CoroutineScopeCache] associated to this [JdwpProcess]. The cache
     * is cleared when the process exits.
     */
    val cache: CoroutineScopeCache

    /**
     * A [StateFlow] that describes the current process information.
     *
     * Note: once [scope] has completed, the flow stops being updated.
     */
    val propertiesFlow: StateFlow<JdwpProcessProperties>

    /**
     * Invokes [block] on the [SharedJdwpSession] corresponding to this process.
     * The [SharedJdwpSession] is opened if needed before [block] is invoked, and closed
     * after [block] exits (if needed, i.e. if there are no other active blocks).
     *
     * Note: This method and [SharedJdwpSession] are both thread-safe, and there
     * can be an arbitrary number of concurrently active [block], they all share
     * the same underlying [SharedJdwpSession].
     *
     * Note: Given Android is limited to a single JDWP session per process per device
     * at any point in time, [block] should exit as soon as the [SharedJdwpSession] is
     * not needed anymore.
     */
    suspend fun <T> withJdwpSession(block: suspend SharedJdwpSession.() -> T): T
}

/**
 * Returns a snapshot of the current [JdwpProcessProperties] for this process.
 *
 * Note: This is a shortcut for [processPropertiesFlow.value][JdwpProcess.propertiesFlow].
 *
 * @see JdwpProcess.propertiesFlow
 */
val JdwpProcess.properties: JdwpProcessProperties
    get() = this.propertiesFlow.value

/**
 * Sends a DDMS command to the AndroidVM to run the garbage collector in this [JdwpProcess]
 * and waits for the confirmation from the AndroidVM the GC was successfully performed.
 *
 * TODO(b/266699981): Add unit test
 */
suspend fun JdwpProcess.executeGarbageCollector(progress: JdwpCommandProgress? = null) {
    withJdwpSession {
        handleDdmsHPGC(progress)
    }
}

private val jdwpProcessAllocationTrackerKey =
    CoroutineScopeCache.Key<JdwpProcessAllocationTracker>("JdwpProcessAllocationTracker")

/**
 * Returns the [JdwpProcessAllocationTracker] for this [JdwpProcess]. This API is deprecated
 * and should only be used for "legacy" devices (API <= 25 "Android N")
 */
val JdwpProcess.allocationTracker: JdwpProcessAllocationTracker
    get() = this.cache.getOrPut(jdwpProcessAllocationTrackerKey) {
        JdwpProcessAllocationTrackerImpl(this)
    }

private val jdwpProcessProfilerKey =
    CoroutineScopeCache.Key<JdwpProcessProfiler>("JdwpProcessProfiler")

/**
 * Returns the [JdwpProcessProfiler] for this [JdwpProcess]. This API is deprecated
 * and should only be used for "legacy" devices (API <= 25 "Android N")
 */
val JdwpProcess.profiler: JdwpProcessProfiler
    get() = this.cache.getOrPut(jdwpProcessProfilerKey) {
        JdwpProcessProfilerImpl(this)
    }
