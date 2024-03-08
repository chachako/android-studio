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

import android.util.Log

/** Try to get the value of a potentially private field of an object */
fun <T> Any.getFieldValue(name: String, defaultValue: T): T {
  return try {
    val field = javaClass.getDeclaredField(name)
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    field.get(this) as? T ?: defaultValue
  } catch (e: Throwable) {
    Log.e("BackgroundTaskInspector", "Failed to retrieve wake lock parameters", e)
    defaultValue
  }
}
