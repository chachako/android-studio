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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.ConnectedDevice
import com.android.adblib.property
import com.android.adblib.tools.AdbLibToolsProperties
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.utils.ReferenceCountedFactory

/**
 * Provides thread-safe and concurrent access to [SharedJdwpSession]
 */
internal interface SharedJdwpSessionProvider : AutoCloseable {
    /**
     * (**testing only**) Whether the [SharedJdwpSession] is currently in use
     */
    val isActive: Boolean

    /**
     * The process ID
     */
    val pid: Int

    /**
     * Invokes [block] with a [SharedJdwpSession] instance that is guaranteed to be open
     * and active.
     *
     * This method is thread-safe and the same [SharedJdwpSession] instance may be shared across
     * multiple concurrent threads, but should never be used outside the scope of [block].
     */
    suspend fun <R> withSharedJdwpSession(block: suspend (SharedJdwpSession) -> R): R

    companion object {
        fun create(device: ConnectedDevice, pid: Int): SharedJdwpSessionProvider {
            val refCounted = ReferenceCountedFactory {
                val jdwpSessionFactory: suspend (ConnectedDevice) -> JdwpSession = { device ->
                    JdwpSession.openJdwpSession(
                        device,
                        pid,
                        device.session.property(AdbLibToolsProperties.JDWP_SESSION_FIRST_PACKET_ID)
                    )
                }
                SharedJdwpSession.create(device, pid, jdwpSessionFactory)
            }
            return SharedJdwpSessionProviderImpl(device, pid, refCounted)
        }
    }
}
