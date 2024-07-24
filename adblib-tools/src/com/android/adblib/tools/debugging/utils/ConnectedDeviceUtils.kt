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
package com.android.adblib.tools.debugging.utils

import com.android.adblib.ConnectedDevice
import com.android.adblib.adbLogger
import com.android.adblib.withPrefix
import com.android.adblib.withScopeContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.retryWhen
import java.time.Duration

/**
 * Collect the values of a [Flow] returned by [serviceInvocation] and store them in a
 * [MutableStateFlow] ([destinationStateFlow]), retrying the [serviceInvocation] for as
 * long as the [ConnectedDevice.scope] is active.
 *
 * @param lastValue The value emitted to [destinationStateFlow] when the device scope is cancelled.
 * @param retryValue The value emitted to [destinationStateFlow] between a failure of
 * [serviceInvocation] and a retry attempt to invoke it.
 * @param retryDelay The [Duration] to wait between each attempt to retry [serviceInvocation].
 */
internal suspend fun <T: Any> ConnectedDevice.serviceFlowToMutableStateFlow(
    serviceInvocation: (ConnectedDevice) -> Flow<T>,
    destinationStateFlow: MutableStateFlow<T>,
    lastValue: T,
    retryValue: T,
    retryDelay: Duration
) {
    val device = this
    val logger = adbLogger(this.session).withPrefix("$session - $device - ")
    device.withScopeContext {
        serviceInvocation(device)
            .retryWhen { throwable, _ ->
                logger.logIOCompletionErrors(throwable)
                // Retry after emitting "retryValue"
                emit(retryValue)
                delay(retryDelay.toMillis())
                true // Retry
            }.collect { newValue ->
                logger.debug { "Received a new value from service flow: $newValue" }
                destinationStateFlow.value = newValue
            }
    }.withFinally {
        logger.debug { "Device scope has been closed, emitting last value: $lastValue" }
        destinationStateFlow.value = lastValue
    }.execute()
}
