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

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.JdwpPacketReceiver
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.withPrefix
import java.util.concurrent.atomic.AtomicInteger

internal class SharedJdwpSessionProviderDelegate(
    /**
     * The [ConnectedDevice] this provider applies to
     */
    private val device: ConnectedDevice,
    /**
     * The process ID this provider applies to
     */
    override val pid: Int,
    /**
     * The [SharedJdwpSessionProvider] (from another [AdbSession]) used to acquire
     * [SharedJdwpSession] instances.
     */
    private val suspendingDelegate: suspend () -> SharedJdwpSessionProvider
) : SharedJdwpSessionProvider {

    private val logger = thisLogger(device.session)
        .withPrefix("${device.session}-${device}-pid=$pid: ")

    /**
     * Note: [refCount] is only used for logging and testing (i.e. [isActive]), it has no
     * impact on the lifetime of the underlying [SharedJdwpSession] from [suspendingDelegate].
     */
    private val refCount = AtomicInteger(0)

    override val isActive: Boolean
        get() = refCount.get() > 0

    override suspend fun <R> withSharedJdwpSession(block: suspend (SharedJdwpSession) -> R): R {
        refCount.incrementAndGet().also { refCount ->
            logger.debug { "withSharedJdwpSession(): enter (refCount:${refCount-1}->${refCount})" }
        }
        try {
            return suspendingDelegate().withSharedJdwpSession { sharedJdwpSession ->
                val jdwpSessionDelegate = SharedJdwpSessionDelegate(device, sharedJdwpSession)
                block(jdwpSessionDelegate)
            }
        } finally {
            refCount.getAndDecrement().also { refCount ->
                logger.debug { "withSharedJdwpSession(): exit (refCount:${refCount}->${refCount-1})" }
            }
        }
    }

    override fun close() {
        // Nothing to do, we don't own the provider
        logger.debug { "close()" }
    }

    /**
     * Delegates [SharedJdwpSession] methods while exposing a custom [device] property passed
     * as constructor parameter.
     */
    private class SharedJdwpSessionDelegate(
        override val device: ConnectedDevice,
        private val delegate: SharedJdwpSession,
    ) : SharedJdwpSession {

        override val pid: Int
            get() = delegate.pid

        override suspend fun sendPacket(packet: JdwpPacketView) {
            delegate.sendPacket(packet)
        }

        override suspend fun newPacketReceiver(): JdwpPacketReceiver {
            return delegate.newPacketReceiver()
        }

        override fun nextPacketId(): Int {
            return delegate.nextPacketId()
        }

        override suspend fun addReplayPacket(packet: JdwpPacketView) {
            delegate.addReplayPacket(packet)
        }
    }
}
