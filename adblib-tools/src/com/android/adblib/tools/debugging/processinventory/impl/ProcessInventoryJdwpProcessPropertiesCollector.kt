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
import com.android.adblib.adbLogger
import com.android.adblib.scope
import com.android.adblib.tools.debugging.ExternalJdwpProcessPropertiesCollector
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.utils.logIOCompletionErrors
import com.android.adblib.withPrefix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class ProcessInventoryJdwpProcessPropertiesCollector(
    private val serverConnection: ProcessInventoryServerConnection,
    override val process: JdwpProcess
) : ExternalJdwpProcessPropertiesCollector {

    private val session: AdbSession
        get() = process.device.session

    private val logger = adbLogger(session)
        .withPrefix("${process.device.session} - ${process.device} - pid=${process.pid} - ")

    init {
        process.scope.launch {
            kotlin.runCatching {
                process.propertiesFlow.collect { properties ->
                    logger.debug { "Process properties changed to $properties" }
                    serverConnection.withConnectionForDevice(process.device) {
                        sendProcessProperties(properties)
                    }
                }
            }.onFailure { throwable ->
                logger.logIOCompletionErrors(throwable)
            }
        }.invokeOnCompletion {
            // If the process has exited, notify the server (using the device scope,
            // as the process scope has been closed)
            if (!process.scope.isActive) {
                logger.debug { "Process scope has terminated, notifying server that process has terminated" }
                process.device.scope.launch {
                    kotlin.runCatching {
                        serverConnection.withConnectionForDevice(process.device) {
                            notifyProcessExit(process.pid)
                        }
                    }.onFailure { throwable ->
                        logger.logIOCompletionErrors(throwable, "Notify process exit")
                    }
                }
            }
        }
    }

    override fun trackProperties(): Flow<JdwpProcessProperties> = flow {
        // Use the device flow, and filter to this process only
        serverConnection.withConnectionForDevice(process.device) {
            processListStateFlow.collect { list ->
                logger.verbose { "Collected new list of properties from device inventory: $list" }
                list.firstOrNull { it.pid == process.pid }?.also {
                    logger.verbose { "Emitting process properties: $it" }
                    emit(it)
                }
            }
        }
    }

    override fun toString(): String {
        return "${this::class.java.simpleName}(process=$process)"
    }
}
