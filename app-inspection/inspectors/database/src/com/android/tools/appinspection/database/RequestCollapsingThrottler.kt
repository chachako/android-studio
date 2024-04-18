/*
 * Copyright 2020 The Android Open Source Project
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
package com.android.tools.appinspection.database

import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach

/**
 * Throttler implementation ensuring that events are run not more frequently that specified
 * interval. Events submitted during the interval period are collapsed into one (i.e. only one is
 * executed).
 *
 * Thread safe.
 */
internal class RequestCollapsingThrottler(
  private val minInterval: Duration,
  private val action: Runnable,
  coroutineContext: CoroutineContext,
) {
  private val channel = Channel<Unit>(Channel.CONFLATED)
  private val supervisor = SupervisorJob()

  init {
    CoroutineScope(coroutineContext + supervisor).launch {
      channel.consumeEach {
        action.run()
        delay(minInterval)
      }
    }
  }

  fun submitRequest() {
    channel.trySend(Unit)
  }

  fun dispose() {
    channel.close()
    supervisor.cancel()
  }
}
