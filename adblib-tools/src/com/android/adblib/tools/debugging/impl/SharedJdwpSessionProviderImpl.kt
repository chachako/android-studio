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
import com.android.adblib.adbLogger
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.utils.ReferenceCountedFactory
import com.android.adblib.withPrefix
import kotlinx.coroutines.flow.StateFlow

internal class SharedJdwpSessionProviderImpl(
    private val device: ConnectedDevice,
    override val pid: Int,
    private val sharedJdwpSessionRef: ReferenceCountedFactory<SharedJdwpSessionImpl>
) : SharedJdwpSessionProvider {

    private val logger = adbLogger(device.session)
        .withPrefix("${device.session} - $device - pid=$pid - ")

    private val withSharedJdwpSessionTracker = BlockActivationTracker()

    override val activationCount: StateFlow<Int>
        get() = withSharedJdwpSessionTracker.activationCount

    override suspend fun <R> withSharedJdwpSession(block: suspend (SharedJdwpSession) -> R): R {
        return withSharedJdwpSessionTracker.track {
            logger.verbose { "withSharedJdwpSession(): enter" }
            // TODO(b/324474436): Handle `retain()` called concurrently with `session.shutdown()`
            val session = sharedJdwpSessionRef.retain()
            try {
                session.openIfNeeded()
                block(session)
            } finally {
                logger.verbose { "withSharedJdwpSession(): exit" }
                if (sharedJdwpSessionRef.releaseNoClose() == 0) {
                    logger.debug { "withSharedJdwpSession(): shutting down shared JDWP session" }
                    session.use { it.shutdown() }
                }
            }
        }
    }

    override fun close() {
        logger.debug { "close()" }
        sharedJdwpSessionRef.close()
    }

    override fun toString(): String {
        return "${this::class.simpleName}(${device.session}, $device, pid:$pid)"
    }
}
