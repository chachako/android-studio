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
import com.android.adblib.property
import com.android.adblib.tools.AdbLibToolsProperties
import com.android.adblib.tools.AdbLibToolsProperties.APP_PROCESS_RETRIEVE_PROCESS_NAME_RETRY_COUNT
import com.android.adblib.tools.AdbLibToolsProperties.APP_PROCESS_RETRIEVE_PROCESS_NAME_RETRY_DELAY
import com.android.adblib.tools.debugging.impl.AppProcessNameRetriever
import kotlinx.coroutines.CoroutineScope
import java.time.Duration

/**
 * A process tracked by [AppProcessTracker]. Each instance has a [pid] and a few additional
 * properties returned by the [AdbDeviceServices.trackApp]
 *
 * A [AppProcess] instance becomes invalid when the corresponding process on the device
 * is terminated, or when the device is disconnected.
 */
interface AppProcess {

    /**
     * The [ConnectedDevice] this process runs on.
     */
    val device: ConnectedDevice

    /**
     * Returns a [CoroutineScopeCache] associated to this [AppProcess]. The cache
     * is cleared when the process exits.
     */
    val cache: CoroutineScopeCache

    /**
     * The process ID
     */
    val pid: Int

    /**
     * Whether the process is `debuggable`, meaning a `JDWP` connection is available.
     * See [JdwpProcess].
     */
    val debuggable: Boolean

    /**
     * Whether the process is `profileable`, meaning a profiler tool can attach to the process
     * and collect profiling data using custom agent/simpleperf calls.
     */
    val profileable: Boolean

    /**
     * The process architecture (or "abi") such as `x86`, `arm64`, etc.
     */
    val architecture: String

    /**
     * The [JdwpProcess] associated to this [AppProcess] if it is [debuggable],
     * `null` otherwise.
     */
    val jdwpProcess: JdwpProcess?
}

/**
 * The [CoroutineScope] whose lifetime matches the lifetime of the process on the device.
 * This is a shortcut for [AppProcess.cache].[scope][CoroutineScopeCache.scope]
 */
val AppProcess.scope: CoroutineScope
    get() = cache.scope

/**
 * Utility function to retrieve the process name of this [AppProcess].
 * * For an [AppProcess.debuggable], use the `JDWP` connection to that process.
 * * For an [AppProcess.profileable], execute a `cat /proc/pid/cmdline` shell command.
 */
suspend fun AppProcess.retrieveProcessName(
    retryCount: Int = device.session.property(APP_PROCESS_RETRIEVE_PROCESS_NAME_RETRY_COUNT),
    retryDelay: Duration = device.session.property(APP_PROCESS_RETRIEVE_PROCESS_NAME_RETRY_DELAY)
): String {
    return AppProcessNameRetriever(this).retrieve(retryCount, retryDelay)
}
