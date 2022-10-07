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
import com.android.adblib.DeviceSelector
import com.android.adblib.tools.debugging.impl.JdwpSessionProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.net.InetSocketAddress

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
