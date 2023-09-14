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

import com.android.adblib.AdbInputChannel
import com.android.adblib.ConnectedDevice
import com.android.adblib.adbLogger
import com.android.adblib.tools.debugging.JdwpCommandProgress
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.JdwpProcessAllocationTracker
import com.android.adblib.tools.debugging.handleDdmsREAE
import com.android.adblib.tools.debugging.handleDdmsREAL
import com.android.adblib.tools.debugging.handleDdmsREAQ
import com.android.adblib.withPrefix

internal class JdwpProcessAllocationTrackerImpl(
    private val jdwpProcess: JdwpProcess
) : JdwpProcessAllocationTracker {

    private val device: ConnectedDevice
        get() = jdwpProcess.device

    private val logger = adbLogger(device.session)
        .withPrefix("${device.session} - $device - pid=${jdwpProcess.pid} - ")

    override suspend fun isEnabled(progress: JdwpCommandProgress?): Boolean {
        return jdwpProcess.withJdwpSession {
            handleDdmsREAQ(progress)
        }.also {
            logger.debug { "Allocation tracker status query: enabled=$it" }
        }
    }

    override suspend fun enable(enabled: Boolean, progress: JdwpCommandProgress?) {
        jdwpProcess.withJdwpSession {
            handleDdmsREAE(enabled, progress)
        }.also {
            logger.debug { "Allocation tracker update: enabled=$enabled" }
        }
    }

    override suspend fun <R> fetchAllocationDetails(
        progress: JdwpCommandProgress?,
        replyHandler: suspend (data: AdbInputChannel, length: Int) -> R
    ): R {
        return jdwpProcess.withJdwpSession {
            handleDdmsREAL(progress, replyHandler)
        }
    }
}
