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

package com.android.tools.appinspection.backgroundtask

import android.app.PendingIntent
import android.content.Intent
import com.android.tools.appinspection.backgroundtask.IntentRegistry.IntentWrapper.Companion.wrap
import java.util.concurrent.ConcurrentHashMap

/** A registry that keeps track of [Intent]/[PendingIntent] relationships */
class IntentRegistry {

  /**
   * A mapping from [Intent] to [PendingIntent] so when an Intent is sent we can trace back to its
   * PendingIntent (if any) for event tracking.
   */
  private val intentToPendingIntentMap = ConcurrentHashMap<IntentWrapper, PendingIntent>()

  private val pendingIntentToInfoMap = ConcurrentHashMap<PendingIntent, PendingIntentInfo>()

  /* Intent shared between an entry hook and an exit hook for the same method. */
  private val currentInfo = ThreadLocal<PendingIntentInfo>()

  fun setCurrentInfo(type: PendingIntentType, requestCode: Int, intent: Intent, flags: Int) {
    currentInfo.set(PendingIntentInfo(type, requestCode, intent, flags))
  }

  fun setPendingIntentForActiveIntent(pendingIntent: PendingIntent) {
    val info = currentInfo.get()
    intentToPendingIntentMap[info.intent.wrap()] = pendingIntent
    pendingIntentToInfoMap[pendingIntent] = info
  }

  fun getPendingIntent(intent: Intent): PendingIntent? {
    return intentToPendingIntentMap[intent.wrap()]
  }

  fun getPendingIntentInfo(pendingIntent: PendingIntent): PendingIntentInfo? {
    return pendingIntentToInfoMap[pendingIntent]
  }

  /**
   * Wraps an [Intent] and overrides its `equals` and `hashCode` methods, so we can use it as a
   * HashMap key. Two intents are considered equal iff [Intent.filterEquals] returns true.
   */
  class IntentWrapper private constructor(private val intent: Intent) {

    override fun equals(other: Any?): Boolean {
      return (other is IntentWrapper && intent.filterEquals(other.intent))
    }

    override fun hashCode() = intent.filterHashCode()

    companion object {

      fun Intent.wrap() = IntentWrapper(this)
    }
  }
}
