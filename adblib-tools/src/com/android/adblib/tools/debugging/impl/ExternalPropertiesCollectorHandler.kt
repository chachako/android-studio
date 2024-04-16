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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.adbLogger
import com.android.adblib.tools.debugging.AtomicStateFlow
import com.android.adblib.tools.debugging.ExternalJdwpProcessPropertiesCollector
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.JdwpSessionProxyStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow

/**
 * Collect changes from an [ExternalJdwpProcessPropertiesCollector], merging them into
 * the current value of the [localPropertiesStateFlow].
 */
internal class ExternalPropertiesCollectorHandler(
    private val externalCollector: ExternalJdwpProcessPropertiesCollector,
    private val localCollectorJob: Job,
    private val localPropertiesStateFlow: AtomicStateFlow<JdwpProcessProperties>,
    private val localProxyStatusStateFlow: StateFlow<JdwpSessionProxyStatus>
) {
    private val session = externalCollector.process.device.session
    private val logger = adbLogger(session)

    suspend fun execute() {
        // Collect properties from external collector and merge them into our local properties
        // state flow.
        externalCollector.trackProperties().collect { externalProperties ->
            logger.debug { "Process ${externalProperties.pid} properties updated: $externalProperties" }

            localPropertiesStateFlow.update { localProperties ->
                // merge external properties with local properties
                val newProperties = localProperties.mergeWith(externalProperties)

                // Stop local property collector so that it does not hog a JDWP session
                if (newProperties.completed) {
                    logger.debug { "Cancelling local collector because collection is completed" }
                    localCollectorJob.cancel("Cancellation due to external collector completion")
                }

                logger.debug { "Updating local JDWP properties: $newProperties" }
                newProperties
            }
        }
    }

    /**
     * Copy the values of all fields [other] to the fields of this [JdwpProcessProperties], but
     * only if the destination field value is the "default" value of that field, i.e. this method
     * does not overwrite fields of [this] that are already initialized.
     */
    private fun JdwpProcessProperties.mergeWith(other: JdwpProcessProperties): JdwpProcessProperties {
        // Special case: If the external collector tells us the process is waiting for
        // a debugger to connect, we need to use the socket address/port of that collector
        // locally, because that is the only valid address/port that can be used to
        // resume the process.
        val isWaitingForDebugger: Boolean
        val jdwpSessionProxyStatus: JdwpSessionProxyStatus
        if (other.isWaitingForDebugger) {
            logger.debug { "Overriding local proxy status because external collector says `isWaitingForDebugger` == true" }
            // If an external collector says the process is waiting for a debugger,
            // that takes precedence over our jdwp proxy value
            if (other.jdwpSessionProxyStatus != this.jdwpSessionProxyStatus)
                logger.info {
                    "Using JDWP session proxy " +
                            "'${other.jdwpSessionProxyStatus}' " +
                            "from external collector '${this@ExternalPropertiesCollectorHandler}' instead of " +
                            "local JDWP proxy' " +
                            "${this.jdwpSessionProxyStatus.socketAddress}'"
                }
            isWaitingForDebugger = true
            jdwpSessionProxyStatus = other.jdwpSessionProxyStatus
        } else {
            logger.debug { "Using local proxy status because external collector says `isWaitingForDebugger` == false" }
            isWaitingForDebugger = false
            jdwpSessionProxyStatus = localProxyStatusStateFlow.value
        }

        @Suppress("DEPRECATION")
        return copy(
            processName = other.processName.mergeWith(processName),
            userId = other.userId.mergeWith(userId),
            packageName = other.packageName.mergeWith(packageName),
            vmIdentifier = other.vmIdentifier.mergeWith(vmIdentifier),
            abi = other.abi.mergeWith(abi),
            jvmFlags = other.jvmFlags.mergeWith(jvmFlags),
            isNativeDebuggable = other.isNativeDebuggable.mergeWith(isNativeDebuggable),
            waitCommandReceived = other.waitCommandReceived.mergeWith(waitCommandReceived),
            features = other.features.mergeWith(features),
            completed = other.completed.mergeWith(completed),
            exception = other.exception.mergeWith(exception),
            isWaitingForDebugger = isWaitingForDebugger,
            jdwpSessionProxyStatus = jdwpSessionProxyStatus,
        )
    }

    private fun String?.mergeWith(other: String?): String? {
        return this ?: other
    }

    private fun Throwable?.mergeWith(other: Throwable?): Throwable? {
        return this ?: other
    }

    private fun Int?.mergeWith(other: Int?): Int? {
        return this ?: other
    }

    private fun Boolean.mergeWith(other: Boolean): Boolean {
        return if (this) true else other
    }

    private fun List<String>.mergeWith(other: List<String>): List<String> {
        return this.ifEmpty { other }
    }
}
