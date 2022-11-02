/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.utils

/**
 * Executes [block] at least once and up to [maxRetries] additional times, retrying if it throws
 * [E].
 */
inline fun <reified E: Throwable> executeWithRetries(maxRetries: Int, block: () -> Unit) {
    executeWithRetries<E, Unit>(maxRetries, block)
}

/**
 * Executes [block] at least once and up to [maxRetries] additional times, returning its result and
 * retrying if it throws [E].
 */
inline fun <reified E: Throwable, T> executeWithRetries(maxRetries: Int, block: () -> T): T {
    var retriesRemaining = maxRetries
    while(true) {
        try {
            return block()
        }
        catch (t: Throwable) {
            if (retriesRemaining == 0 || t !is E) throw t
            --retriesRemaining
        }
    }
}
