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

import com.android.processmonitor.common.FakeProcessTracker
import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.common.ProcessTracker
import java.lang.IllegalArgumentException

internal abstract class FakeProcessTrackerFactory<T> : ProcessTrackerFactory<T> {

    private val devices = mutableMapOf<String, FakeProcessTracker>()

    override fun createProcessTracker(device: T): ProcessTracker {
        val tracker = FakeProcessTracker()
        devices[getSerialNumber(device)] = tracker
        return tracker
    }

    abstract fun getSerialNumber(device: T): String

    suspend fun send(serial: String, event: ProcessEvent) {
        val tracker = devices[serial] ?: throw IllegalArgumentException("No tracker for $serial")
        tracker.send(event)
    }
}
