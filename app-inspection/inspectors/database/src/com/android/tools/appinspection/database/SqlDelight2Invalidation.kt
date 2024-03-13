/*
 * Copyright 2024 The Android Open Source Project
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
import androidx.inspection.ArtTooling
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val DRIVER_CLASSNAME: String =
  "app.cash.sqldelight.driver.android.AndroidSqliteDriver"
private const val NOTIFY_METHOD: String = "notifyListeners"
private const val LISTENERS_FIELD: String = "listeners"

/**
 * An [Invalidation] for the SqlDelight 2 library.
 *
 * SqlDelight 2 invalidation API uses an internal "queryKey" to associate queries with listeners.
 * The key is created by the generated code and is typically just the affected table name but can in
 * theory be anything. In fact, a user can register a listener directly using
 * SqlDriver#addListener() and provide their own queryKeys. This will work as long as the user also
 * manages notification using SqlDriver#notifyListeners().
 *
 * The public API that notifies listeners requires this queryKey:
 * <pre>
 * override fun notifyListeners(vararg queryKeys: String)
 * </pre> *
 *
 * There is no public API that works without it and there is no public API that lists the current
 * listeners or queryKey's.
 *
 * Because of this, we need to access the private field AndroidSqliteDriver#listeners and extract
 * the registered queryKeys.
 */
internal class SqlDelight2Invalidation
private constructor(
  private val artTooling: ArtTooling,
  private val driverClass: Class<*>,
  private val notifyListenersMethod: Method,
  private val listenersField: Field,
) : Invalidation {
  override fun triggerInvalidations() {
    artTooling.findInstances(driverClass).forEach { driver ->
      try {
        val listeners = driver.getListeners()
        synchronized(listeners) {
          notifyListenersMethod.invoke(driver, listeners.keys.toTypedArray<String>() as Any)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Error invalidating SqlDriver", e)
      }
    }
  }

  companion object {
    @JvmStatic
    fun create(artTooling: ArtTooling): Invalidation {
      try {
        val classLoader = SqlDelight2Invalidation::class.java.classLoader
        val driverClass = classLoader.loadClass(DRIVER_CLASSNAME)
        val notifyListenersMethod =
          driverClass.getDeclaredMethod(NOTIFY_METHOD, Array<String>::class.java)
        val listenersField = driverClass.getDeclaredField(LISTENERS_FIELD)
        listenersField.isAccessible = true
        return SqlDelight2Invalidation(
          artTooling,
          driverClass,
          notifyListenersMethod,
          listenersField,
        )
      } catch (e: ClassNotFoundException) {
        Log.v(HIDDEN_TAG, "SqlDelight 2 not found", e)
        return Invalidation.NOOP
      } catch (e: Throwable) {
        Log.w(TAG, "Error setting up SqlDelight 2 invalidation", e)
        return Invalidation.NOOP
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun Any.getListeners(): Map<String, Any> = listenersField.get(this) as Map<String, Any>
}
