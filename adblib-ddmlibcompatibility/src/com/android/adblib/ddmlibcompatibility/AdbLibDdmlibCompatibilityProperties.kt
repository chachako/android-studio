/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.adblib.ddmlibcompatibility

import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import com.android.adblib.ddmlibcompatibility.debugging.AdbLibDeviceClientManager
import com.android.adblib.ddmlibcompatibility.debugging.AdblibClientWrapper
import com.android.adblib.trackDevices
import com.android.ddmlib.Client
import java.time.Duration

/**
 * By convention, all [properties][AdbSessionHost.Property] of this module are defined in
 * this singleton.
 */
object AdbLibDdmlibCompatibilityProperties {
    private const val NAME_PREFIX = "com.android.adblib.ddmlibcompatibility"

    /**
     * Maximum amount of time to wait for a device to show up in [AdbSession.trackDevices]
     * after an [AdbLibDeviceClientManager] instance is created.
     */
    val DEVICE_TRACKER_WAIT_TIMEOUT = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.device.tracker.wait.timeout",
        defaultValue = Duration.ofSeconds(2)
    )

    /**
     * Maximum amount of time a thread should be blocked when calling a method
     * of the [Client] interface implemented by [AdblibClientWrapper].
     */
    val RUN_BLOCKING_LEGACY_DEFAULT_TIMEOUT = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.run.blocking.legacy.default.timeout",
        defaultValue = Duration.ofSeconds(5)
    )
}
