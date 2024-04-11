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

import android.util.Log
import androidx.inspection.InspectorEnvironment
import java.lang.ref.WeakReference
import java.lang.reflect.Method

private const val INVALIDATION_TRACKER_QNAME = "androidx.room.InvalidationTracker"

/**
 * Tracks instances of Room's InvalidationTracker so that we can trigger them to re-check database
 * for changes in case there are observed tables in the application UI.
 *
 * The list of instances of InvalidationTrackers are cached to avoid re-finding them after each
 * query. Make sure to call [.invalidateCache] after a new database connection is detected.
 *
 * TODO(aalbert): THis seems overly complicated. It should be able to use a similar pattern to the
 *   SqlDelight invalidators.
 */
internal class RoomInvalidationRegistry(private val environment: InspectorEnvironment) :
  Invalidation {
  /** Might be null if application does not ship with Room. */
  private val invoker = findInvalidationTrackerClass()

  /** The list of InvalidationTracker instances. */
  private var invalidationInstances: List<WeakReference<*>>? = null

  /**
   * Calls all the InvalidationTrackers to check their database for updated tables.
   *
   * If the list of InvalidationTracker instances are not cached, this will do a lookup.
   */
  override fun triggerInvalidations() {
    if (invoker == null) {
      return
    }
    val instances = getInvalidationTrackerInstances()
    for (reference in instances) {
      val instance = reference.get()
      if (instance != null) {
        invoker.trigger(instance)
      }
    }
  }

  /** Invalidates the list of InvalidationTracker instances. */
  fun invalidateCache() {
    invalidationInstances = null
  }

  private fun getInvalidationTrackerInstances(): List<WeakReference<*>> {
    var cached = invalidationInstances
    cached =
      when {
        cached != null -> return cached
        invoker == null -> emptyList()
        else -> {
          val instances = environment.artTooling().findInstances(invoker.invalidationTrackerClass)
          cached = instances.map { WeakReference(it) }
          cached
        }
      }
    invalidationInstances = cached
    return cached
  }

  private fun findInvalidationTrackerClass(): InvalidationTrackerInvoker? {
    try {
      val classLoader = RoomInvalidationRegistry::class.java.classLoader
      val klass = classLoader.loadClass(INVALIDATION_TRACKER_QNAME)
      return InvalidationTrackerInvoker(klass)
    } catch (e: ClassNotFoundException) {
      Log.v(
        HIDDEN_TAG,
        "Room InvalidationTracker not found. Either app is not using it or Proguard has renamed it.",
      )
    } catch (e: Throwable) {
      Log.w(TAG, "Error setting up Room invalidation", e)
    }
    return null
  }

  /** Helper class to invoke methods on Room's InvalidationTracker class. */
  internal class InvalidationTrackerInvoker(val invalidationTrackerClass: Class<*>) {
    private val refreshMethod: Method?

    init {
      refreshMethod = safeGetRefreshMethod(invalidationTrackerClass)
    }

    private fun safeGetRefreshMethod(invalidationTrackerClass: Class<*>): Method? {
      return try {
        invalidationTrackerClass.getMethod("refreshVersionsAsync")
      } catch (ex: NoSuchMethodException) {
        null
      }
    }

    fun trigger(instance: Any?) {
      if (refreshMethod != null) {
        try {
          refreshMethod.invoke(instance)
        } catch (t: Throwable) {
          Log.e(TAG, "Failed to invoke invalidation tracker", t)
        }
      }
    }
  }
}
