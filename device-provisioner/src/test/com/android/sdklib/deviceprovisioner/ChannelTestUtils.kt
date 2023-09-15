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
package com.android.sdklib.deviceprovisioner

import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

/**
 * Receives messages on this channel until one is received that does not cause an [AssertionError]
 * in the supplied block. This should be used within a withTimeout() block. If timeout occurs,
 * throws a new AssertionError with the last received error as a cause, if one was received.
 */
suspend fun <T, R> Channel<T>.receiveUntilPassing(block: (T) -> R): R {
  var lastError: AssertionError? = null
  while (true) {
    try {
      return block(receive())
    } catch (e: AssertionError) {
      lastError = e
    } catch (e: CancellationException) {
      when (lastError) {
        null -> throw e
        else -> throw AssertionError("Expected message not received within timeout", lastError)
      }
    }
  }
}

/**
 * Receives messages from the channel for the given time [duration]. Returns the number of received
 * messages.
 */
suspend fun <T> Channel<T>.drainFor(duration: Duration): Int {
  var count = 0
  try {
    withTimeout(duration) {
      while (true) {
        receive()
        count++
      }
    }
  } catch (_: TimeoutCancellationException) {}
  return count
}
