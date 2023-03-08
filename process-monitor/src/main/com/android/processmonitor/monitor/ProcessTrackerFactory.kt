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
package com.android.processmonitor.monitor

import com.android.processmonitor.common.ProcessTracker

/**
 * Creates a [ProcessTracker] for a device.
 *
 * This makes it possible to test a monitor without having to simulate the underlying trackers it
 * uses.
 *
 * We use a generic device type so that we can later reuse it when we introduce a monitor that uses
 * Adblib with a ConnectedDevice.
 */
internal fun interface ProcessTrackerFactory<T> {
    suspend fun createProcessTracker(device: T) : ProcessTracker
}
