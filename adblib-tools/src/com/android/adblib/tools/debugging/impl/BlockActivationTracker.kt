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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Track the # of concurrent activations of a given function in a [StateFlow] of [Int].
 *
 * Note: This class is **thread-safe**
 */
internal class BlockActivationTracker {

    private val activationCountStateFlow = MutableStateFlow(0)

    val activationCount = activationCountStateFlow.asStateFlow()

    inline fun <R> track(block: () -> R): R {
        activationCountStateFlow.update { it + 1 }
        return try {
            block()
        } finally {
            activationCountStateFlow.update { it - 1 }
        }
    }

    suspend fun waitWhileActive() {
        activationCountStateFlow.first { it == 0 }
    }
}
