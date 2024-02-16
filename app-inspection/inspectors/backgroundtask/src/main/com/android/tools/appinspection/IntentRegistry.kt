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

package com.android.tools.appinspection

import android.app.PendingIntent
import android.content.Intent
import com.android.tools.appinspection.IntentRegistry.IntentWrapper.Companion.wrap
import java.util.concurrent.ConcurrentHashMap

/** A registry that keeps track of [Intent]/[PendingIntent] relationships */
class IntentRegistry {

  /**
   * A mapping from [Intent] to [PendingIntent] so when an Intent is sent we can trace back to its
   * PendingIntent (if any) for event tracking.
   */
  private val intentMap = ConcurrentHashMap<IntentWrapper, PendingIntent>()

  /* Intent shared between an entry hook and an exit hook for the same method. */
  private val activeIntent = ThreadLocal<Intent>()

  fun setIntentData(intent: Intent) {
    activeIntent.set(intent)
  }

  fun setPendingIntentForActiveIntent(pendingIntent: PendingIntent) {
    intentMap[activeIntent.get().wrap()] = pendingIntent
  }

  fun getPendingIntent(intent: Intent): PendingIntent? {
    return intentMap[intent.wrap()]
  }

  /**
   * Wraps an [Intent] and overrides its `equals` and `hashCode` methods, so we can use it as a
   * HashMap key. Two intents are considered equal iff [Intent.filterEquals] returns true.
   */
  private class IntentWrapper private constructor(private val mIntent: Intent) {

    override fun equals(other: Any?): Boolean {
      return (other is IntentWrapper && mIntent.filterEquals(other.mIntent))
    }

    override fun hashCode() = mIntent.filterHashCode()

    companion object {

      fun Intent.wrap() = IntentWrapper(this)
    }
  }
}
