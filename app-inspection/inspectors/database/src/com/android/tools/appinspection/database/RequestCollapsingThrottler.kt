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

import androidx.annotation.GuardedBy

private const val NEVER: Long = -1

/**
 * Throttler implementation ensuring that events are run not more frequently that specified
 * interval. Events submitted during the interval period are collapsed into one (i.e. only one is
 * executed).
 *
 * Thread safe.
 *
 * TODO(aalbert): This can probably be eliminated by using coroutines with a channel/flow
 */
internal class RequestCollapsingThrottler(
  private val minIntervalMs: Long,
  private val action: Runnable,
  private val executor: DeferredExecutor,
) {
  private val lock = Any()

  @GuardedBy("lock") private var pendingDispatch = false

  @GuardedBy("lock") private var lastSubmitted = NEVER

  fun submitRequest() {
    synchronized(lock) {
      if (pendingDispatch) {
        return
      }
      pendingDispatch = true // about to schedule
    }
    val delayMs = minIntervalMs - sinceLast() // delayMs < 0 is OK
    scheduleDispatch(delayMs)
  }

  // TODO: switch to ListenableFuture to react on failures
  private fun scheduleDispatch(delayMs: Long) {
    executor.schedule(
      {
        try {
          action.run()
        } finally {
          synchronized(lock) {
            lastSubmitted = now()
            pendingDispatch = false
          }
        }
      },
      delayMs,
    )
  }

  private fun sinceLast(): Long {
    synchronized(lock) {
      val lastSubmitted: Long = lastSubmitted
      return if (lastSubmitted == NEVER) (minIntervalMs + 1) // more than minIntervalMs
      else (now() - lastSubmitted)
    }
  }

  internal fun interface DeferredExecutor {
    fun schedule(command: Runnable, delayMs: Long)
  }
}

private fun now(): Long {
  return System.currentTimeMillis()
}
