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

package android.util

object Log {

  @JvmOverloads @JvmStatic fun v(tag: String, msg: String, tr: Throwable? = null) = 0

  @JvmOverloads @JvmStatic fun d(tag: String, msg: String, tr: Throwable? = null) = 0

  @JvmOverloads @JvmStatic fun i(tag: String, msg: String, tr: Throwable? = null) = 0

  @JvmOverloads @JvmStatic fun w(tag: String, msg: String, tr: Throwable? = null) = 0

  @JvmOverloads @JvmStatic fun e(tag: String, msg: String, tr: Throwable? = null) = 0
}
