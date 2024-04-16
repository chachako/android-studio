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
package com.android.adblib.tools.debugging.processinventory.impl

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.processinventory.server.ProcessInventoryServer
import com.android.adblib.tools.debugging.processinventory.server.ProcessInventoryServerConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A connection to a remote [ProcessInventoryServer] to help with tracking and
 * updating [devices][ConnectedDevice] and the [JdwpProcessProperties] of their
 * JDWP processes.
 */
internal interface ProcessInventoryServerConnection : AutoCloseable {

    /**
     * Invokes [block] with the [ConnectionForDevice] corresponding to [device].
     * [block] can be a long-running coroutine, but it will be cancelled when the
     * [ConnectedDevice.cache] is cancelled.
     */
    suspend fun <R> withConnectionForDevice(
        device: ConnectedDevice,
        block: suspend ConnectionForDevice.() -> R
    ): R

    interface ConnectionForDevice {

        /**
         * The [ConnectedDevice] this connection applies to
         */
        val device: ConnectedDevice

        /**
         * Asks the underlying [ProcessInventoryServer] to send notifications about [JdwpProcessProperties]
         * updates of all processes of a given [device]. The returned [Flow] remains active as long as the
         * [ConnectedDevice.scope][com.android.adblib.scope].
         */
        val processListStateFlow: StateFlow<List<JdwpProcessProperties>>

        /**
         * Sends the given [JdwpProcessProperties] of a given [process] to the underlying [ProcessInventoryServer]
         */
        suspend fun sendProcessProperties(properties: JdwpProcessProperties)

        /**
         * Notify the underlying [ProcessInventoryServer] that the given process has exited.
         */
        suspend fun notifyProcessExit(pid: Int)
    }

    companion object {

        fun create(
            session: AdbSession,
            config: ProcessInventoryServerConfiguration
        ): ProcessInventoryServerConnection {
            return ProcessInventoryServerConnectionImpl(session, config)
        }
    }
}
