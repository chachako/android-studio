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
import androidx.inspection.ArtTooling
import java.lang.reflect.Method

private const val SQLDELIGHT_QUERY_CLASS_NAME = "com.squareup.sqldelight.Query"
private const val SQLDELIGHT_NOTIFY_METHOD_NAME = "notifyDataChanged"

internal class SqlDelightInvalidation
private constructor(
  private val artTooling: ArtTooling,
  private val queryClass: Class<*>,
  private val notifyDataChangeMethod: Method,
) : Invalidation {
  override fun triggerInvalidations() {
    // invalidating all queries because we can't say which ones were actually affected.
    artTooling.findInstances(queryClass).forEach {
      try {
        notifyDataChangeMethod.invoke(it)
      } catch (e: Throwable) {
        Log.w(TAG, "Error calling notifyDataChanged", e)
      }
    }
  }

  companion object {
    @JvmStatic
    fun create(artTooling: ArtTooling): Invalidation {
      try {
        val classLoader = SqlDelightInvalidation::class.java.classLoader
        val queryClass = classLoader.loadClass(SQLDELIGHT_QUERY_CLASS_NAME)
        val notifyMethod = queryClass.getMethod(SQLDELIGHT_NOTIFY_METHOD_NAME)
        return SqlDelightInvalidation(artTooling, queryClass, notifyMethod)
      } catch (e: ClassNotFoundException) {
        Log.v(HIDDEN_TAG, "SqlDelight not found", e)
        return Invalidation.NOOP
      } catch (e: Throwable) {
        Log.w(TAG, "Error setting up SqlDelight invalidation", e)
        return Invalidation.NOOP
      }
    }
  }
}
