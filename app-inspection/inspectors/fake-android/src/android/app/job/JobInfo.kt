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

package android.app.job

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle

class JobInfo(
  val id: Int,
  val service: ComponentName,
  val backoffPolicy: Int = 0,
  val initialBackoffMillis: Long = 0,
  val isPeriodic: Boolean = false,
  val flexMillis: Long = 0,
  val triggerContentUris: Array<TriggerContentUri> = emptyArray(),
  val intervalMillis: Long = 0,
  val minLatencyMillis: Long = 0,
  val maxExecutionDelayMillis: Long = 0,
  val networkType: Int = 0,
  val triggerContentMaxDelay: Long = 0,
  val triggerContentUpdateDelay: Long = 0,
  val isPersisted: Boolean = false,
  val isRequireBatteryNotLow: Boolean = false,
  val isRequireCharging: Boolean = false,
  val isRequireDeviceIdle: Boolean = false,
  val isRequireStorageNotLow: Boolean = false,
  val extras: PersistableBundle = PersistableBundle(),
  val transientExtras: Bundle = Bundle(),
) {

  class Builder(private val id: Int, private val service: ComponentName) {

    private var backoffPolicy = 0
    private var initialBackoffMillis = 0L
    private var isPeriodic = false
    private var flexMillis = 0L
    private var triggerContentUris = mutableListOf<TriggerContentUri>()
    private var intervalMillis = 0L
    private var minLatencyMillis = 0L
    private var maxExecutionDelayMillis = 0L
    private var networkType = 0
    private var triggerContentMaxDelay = 0L
    private var triggerContentUpdateDelay = 0L
    private var isPersisted = false
    private var isRequireBatteryNotLow = false
    private var isRequireCharging = false
    private var isRequireDeviceIdle = false
    private var isRequireStorageNotLow = false
    private var extras = PersistableBundle()
    private var transientExtras = Bundle()

    fun build() =
      JobInfo(
        id,
        service,
        backoffPolicy,
        initialBackoffMillis,
        isPeriodic,
        flexMillis,
        triggerContentUris.toTypedArray(),
        intervalMillis,
        minLatencyMillis,
        maxExecutionDelayMillis,
        networkType,
        triggerContentMaxDelay,
        triggerContentUpdateDelay,
        isPersisted,
        isRequireBatteryNotLow,
        isRequireCharging,
        isRequireDeviceIdle,
        isRequireStorageNotLow,
        extras,
        transientExtras,
      )

    fun setBackoffCriteria(initialBackoffMillis: Long, backoffPolicy: Int): Builder {
      this.initialBackoffMillis = initialBackoffMillis
      this.backoffPolicy = backoffPolicy
      return this
    }

    fun setPeriodic(intervalMillis: Long, flexMillis: Long): Builder {
      this.isPeriodic = true
      this.intervalMillis = intervalMillis
      this.flexMillis = flexMillis
      return this
    }

    fun setMinimumLatency(minLatencyMillis: Long): Builder {
      this.minLatencyMillis = minLatencyMillis
      return this
    }

    fun setOverrideDeadline(maxExecutionDelayMillis: Long): Builder {
      this.maxExecutionDelayMillis = maxExecutionDelayMillis
      return this
    }

    fun setRequiredNetworkType(networkType: Int): Builder {
      this.networkType = networkType
      return this
    }

    fun addTriggerContentUri(uri: TriggerContentUri): Builder {
      this.triggerContentUris.add(uri)
      return this
    }

    fun setTriggerContentMaxDelay(durationMs: Long): Builder {
      this.triggerContentMaxDelay = durationMs
      return this
    }

    fun setTriggerContentUpdateDelay(durationMs: Long): Builder {
      this.triggerContentUpdateDelay = durationMs
      return this
    }

    fun setPersisted(isPersisted: Boolean): Builder {
      this.isPersisted = isPersisted
      return this
    }

    fun setRequiresBatteryNotLow(batteryNotLow: Boolean): Builder {
      this.isRequireBatteryNotLow = batteryNotLow
      return this
    }

    fun setRequiresCharging(requiresCharging: Boolean): Builder {
      this.isRequireCharging = requiresCharging
      return this
    }

    fun setRequiresDeviceIdle(requiresDeviceIdle: Boolean): Builder {
      this.isRequireDeviceIdle = requiresDeviceIdle
      return this
    }

    fun setRequiresStorageNotLow(storageNotLow: Boolean): Builder {
      this.isRequireStorageNotLow = storageNotLow
      return this
    }

    fun setExtras(extras: PersistableBundle): Builder {
      this.extras = extras
      return this
    }

    fun setTransientExtras(extras: Bundle): Builder {
      this.transientExtras = extras
      return this
    }
  }

  class TriggerContentUri(val uri: Uri)

  companion object {

    const val BACKOFF_POLICY_EXPONENTIAL: Int = 1
    const val BACKOFF_POLICY_LINEAR: Int = 0
    const val NETWORK_TYPE_ANY: Int = 1
    const val NETWORK_TYPE_METERED: Int = 4
    const val NETWORK_TYPE_NONE: Int = 0
    const val NETWORK_TYPE_NOT_ROAMING: Int = 3
    const val NETWORK_TYPE_UNMETERED: Int = 2
  }
}
