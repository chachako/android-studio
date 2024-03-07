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

import android.os.Bundle
import android.os.PersistableBundle

internal fun <T : Any> Bundle.put(key: String, value: T): Bundle {
  when (value) {
    is Int -> putInt(key, value)
    is String -> putString(key, value)
    is Bundle -> putBundle(key, value)
    else -> throw UnsupportedOperationException("Unsupported bundle type: ${value::class.java}")
  }
  return this
}

internal fun <T : Any> PersistableBundle.put(key: String, value: T): PersistableBundle {
  when (value) {
    is Int -> putInt(key, value)
    is String -> putString(key, value)
    else -> throw UnsupportedOperationException("Unsupported bundle type: ${value::class.java}")
  }
  return this
}
