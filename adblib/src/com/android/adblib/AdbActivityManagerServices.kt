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
package com.android.adblib

interface AdbActivityManagerServices {
    /**
     * Uses `adb shell am force-stop` to terminate an app.
     */
    suspend fun forceStop(device: DeviceSelector, packageName: String)

    /**
     * Uses `adb shell am crash` to crash an app.
     *
     * Note that `am crash` command is available on API level > 26.
     */
    suspend fun crash(device: DeviceSelector, packageName: String)
}
