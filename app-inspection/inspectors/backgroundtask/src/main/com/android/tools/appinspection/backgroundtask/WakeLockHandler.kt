/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.os.PowerManager.WakeLock
import androidx.annotation.VisibleForTesting
import androidx.inspection.Connection
import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.WakeLockAcquired
import com.android.tools.appinspection.backgroundtask.BackgroundTaskUtil.sendBackgroundTaskEvent
import com.android.tools.appinspection.common.getStackTrace

private const val DEFAULT_TAG = "UNKNOWN"

/** Wake lock levels */
private const val WAKE_LOCK_LEVEL_MASK = 0x0000ffff
private const val PARTIAL_WAKE_LOCK = 0x00000001
private const val SCREEN_DIM_WAKE_LOCK = 0x00000006
private const val SCREEN_BRIGHT_WAKE_LOCK = 0x0000000a
private const val FULL_WAKE_LOCK = 0x0000001a
private const val PROXIMITY_SCREEN_OFF_WAKE_LOCK = 0x00000020

/** Wake lock flags */
private const val ACQUIRE_CAUSES_WAKEUP = 0x10000000
private const val ON_AFTER_RELEASE = 0x20000000

/** Wake lock release flags */
@VisibleForTesting const val RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY = 0x00000001

/** A handler class that adds necessary hooks to track events for wake locks. */
interface WakeLockHandler {

  /**
   * Entry hook for [PowerManager.newWakeLock(int, String)]. Captures the flags and tag parameters.
   */
  fun onNewWakeLockEntry(levelAndFlags: Int, tag: String)

  /**
   * Exit hook for [PowerManager#newWakeLock(int, String)]. Associates wake lock instance with the
   * previously captured flags and myTag parameters.
   */
  fun onNewWakeLockExit(wakeLock: WakeLock): WakeLock

  /**
   * Wrapper method for [WakeLock.acquire].
   *
   * Since [WakeLock.acquire] does not call [WakeLock.acquire] (vice versa), this will not cause
   * double-instrumentation.
   *
   * @param wakeLock the wrapped [WakeLock] instance.
   * @param timeout the timeout parameter passed to the original method.
   */
  fun onWakeLockAcquired(wakeLock: WakeLock, timeout: Long)

  /**
   * Entry hook for [WakeLock.release(int)]. Capture the flags passed to the method and the "this"
   * instance so the exit hook can retrieve them back.
   */
  fun onWakeLockReleasedEntry(wakeLock: WakeLock, flag: Int)

  /**
   * Add exit hook for [WakeLock.release(int)]. [WakeLock.isHeld()] may be updated in the method, so
   * we should retrieve the value in an exit hook. Then we send the held state along with the flags
   * from the entry hook to Studio Profiler.
   */
  fun onWakeLockReleasedExit()
}

class WakeLockHandlerImpl(private val connection: Connection) : WakeLockHandler {

  /** Data structure for wake lock creation parameters. */
  private data class CreationParams(val levelAndFlags: Int, val tag: String)

  /** Data structure for wake lock release parameters. */
  private data class ReleaseParams(val wakeLock: WakeLock, val flag: Int)

  /**
   * Use a thread-local variable for wake lock creation parameters, so a value can be temporarily
   * stored when we enter a wakelock's constructor and retrieved when we exit it. Using a
   * ThreadLocal protects against the situation when multiple threads create wake locks at the same
   * time.
   */
  private val newWakeLockData = ThreadLocal<CreationParams>()

  /**
   * Use a thread-local variable for wake lock release parameters, so a value can be temporarily
   * stored when we enter the release method and retrieved when we exit it.
   */
  private val releaseWakeLockData = ThreadLocal<ReleaseParams>()

  /** Used by acquire and release hooks to look up the generated ID by wake lock instance. */
  private val eventIdMap = mutableMapOf<WakeLock, Long>()

  /** Used by acquire hooks to retrieve wake lock creation parameters. */
  private val wakeLockCreationParamsMap = mutableMapOf<WakeLock, CreationParams>()

  override fun onNewWakeLockEntry(levelAndFlags: Int, tag: String) {
    newWakeLockData.set(CreationParams(levelAndFlags, tag))
  }

  override fun onNewWakeLockExit(wakeLock: WakeLock): WakeLock {
    wakeLockCreationParamsMap[wakeLock] = newWakeLockData.get()
    return wakeLock
  }

  override fun onWakeLockAcquired(wakeLock: WakeLock, timeout: Long) {
    val eventId = eventIdMap.getOrPut(wakeLock) { BackgroundTaskUtil.nextId() }
    val creationParams =
      wakeLockCreationParamsMap.getOrElse(wakeLock) {
        CreationParams(wakeLock.getFlags(), wakeLock.getTag())
      }
    connection.sendBackgroundTaskEvent(eventId) {
      stacktrace = getStackTrace(1)
      wakeLockAcquiredBuilder.apply {
        level =
          when (creationParams.levelAndFlags and WAKE_LOCK_LEVEL_MASK) {
            PARTIAL_WAKE_LOCK -> WakeLockAcquired.Level.PARTIAL_WAKE_LOCK
            SCREEN_DIM_WAKE_LOCK -> WakeLockAcquired.Level.SCREEN_DIM_WAKE_LOCK
            SCREEN_BRIGHT_WAKE_LOCK -> WakeLockAcquired.Level.SCREEN_BRIGHT_WAKE_LOCK
            FULL_WAKE_LOCK -> WakeLockAcquired.Level.FULL_WAKE_LOCK
            PROXIMITY_SCREEN_OFF_WAKE_LOCK -> WakeLockAcquired.Level.PROXIMITY_SCREEN_OFF_WAKE_LOCK
            else -> WakeLockAcquired.Level.UNDEFINED_WAKE_LOCK_LEVEL
          }
        if (creationParams.levelAndFlags and ACQUIRE_CAUSES_WAKEUP != 0) {
          addFlags(WakeLockAcquired.CreationFlag.ACQUIRE_CAUSES_WAKEUP)
        }
        if (creationParams.levelAndFlags and ON_AFTER_RELEASE != 0) {
          addFlags(WakeLockAcquired.CreationFlag.ON_AFTER_RELEASE)
        }
        tag = creationParams.tag
        this.timeout = timeout
      }
    }
  }

  override fun onWakeLockReleasedEntry(wakeLock: WakeLock, flag: Int) {
    releaseWakeLockData.set(ReleaseParams(wakeLock, flag))
  }

  override fun onWakeLockReleasedExit() {
    val releaseParams = releaseWakeLockData.get()
    val eventId = eventIdMap.getOrPut(releaseParams.wakeLock) { BackgroundTaskUtil.nextId() }
    connection.sendBackgroundTaskEvent(eventId) {
      stacktrace = getStackTrace(2)
      wakeLockReleasedBuilder.apply {
        if (releaseParams.flag and RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY != 0) {
          addFlags(
            BackgroundTaskInspectorProtocol.WakeLockReleased.ReleaseFlag
              .RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY
          )
        }
        isHeld = releaseParams.wakeLock.isHeld
      }
    }
  }
}

private fun WakeLock.getFlags() = getFieldValue("mFlags", 1)

private fun WakeLock.getTag() = getFieldValue("mTag", DEFAULT_TAG)
