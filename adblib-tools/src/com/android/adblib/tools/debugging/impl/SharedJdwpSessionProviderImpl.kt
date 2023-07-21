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
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.utils.ReferenceCountedFactory
import com.android.adblib.useShutdown
import com.android.adblib.withPrefix
import java.util.concurrent.atomic.AtomicInteger

internal class SharedJdwpSessionProviderImpl(
    private val device: ConnectedDevice,
    override val pid: Int,
    private val sharedJdwpSessionRef: ReferenceCountedFactory<SharedJdwpSessionImpl>,
    private val onClose: (SharedJdwpSessionProvider) -> Unit
) : SharedJdwpSessionProvider {

    private val logger = thisLogger(device.session)
        .withPrefix("${device.session}-${device}-pid=$pid: ")

    /**
     * Note: [refCount] is only used for debugging/diagnostic purposes, it has no other uses.
     */
    private val refCount = AtomicInteger(0)

    /**
     * (**testing only**) Whether the [SharedJdwpSession] is currently in use
     */
    override val isActive: Boolean
        get() = sharedJdwpSessionRef.isRetained

    override suspend fun <R> withSharedJdwpSession(block: suspend (SharedJdwpSession) -> R): R {
        refCount.incrementAndGet().also { refCount ->
            logger.debug { "withSharedJdwpSession(): enter (refCount:${refCount-1}->${refCount})" }
        }
        val session = sharedJdwpSessionRef.retain()
        return try {
            session.openIfNeeded()
            block(session)
        } finally {
            refCount.getAndDecrement().also { refCount ->
                logger.debug { "withSharedJdwpSession(): exit (refCount:${refCount}->${refCount-1})" }
            }
            if (sharedJdwpSessionRef.releaseNoClose() == 0) {
                logger.debug { "withSharedJdwpSession(): shutting down shared JDWP session" }
                // Call "shutdown" then "close"
                session.useShutdown { }
            }
        }
    }

    override fun close() {
        logger.debug { "close()" }
        sharedJdwpSessionRef.close()
        onClose(this)
    }

    override fun toString(): String {
        return "${this::class.simpleName}(${device.session}, $device, pid:$pid)"
    }
}
