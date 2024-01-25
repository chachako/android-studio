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
package com.android.adblib.tools

import java.time.Duration
import java.time.Instant

data class InstallMetrics(

    /**
     * The time when the install operation started
     */
    val startTime: Instant,

    /**
     * The [Duration] of the install operation
     */
    val duration: Duration,

    /**
     * The time when the install operation started to push files to package manager
     */
    val uploadStartTime: Instant,

    /**
     * The subset of [druration] spent on uploading APK files to the device.
     */
    val uploadDuration: Duration,
)
