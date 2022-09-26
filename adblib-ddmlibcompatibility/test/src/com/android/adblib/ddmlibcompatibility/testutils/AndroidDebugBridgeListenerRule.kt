/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.ddmlibcompatibility.testutils

import com.android.ddmlib.AndroidDebugBridge
import org.junit.rules.ExternalResource

/**
 * [ExternalResource] test rule that allows removes listeners added to [AndroidDebugBridge]
 */
class AndroidDebugBridgeListenerRule : ExternalResource() {

    private val deviceChangeListeners = mutableListOf<AndroidDebugBridge.IDeviceChangeListener>()

    override fun after() {
        try {
            // Close list in reverse order of registration to match intuitive behavior that
            // items registered first are closed last.
            deviceChangeListeners.asReversed().forEach {
              AndroidDebugBridge.removeDeviceChangeListener(it)
            }
            deviceChangeListeners.clear()
        } finally {
            super.after()
        }
    }

    fun addDeviceChangeListener(listener: AndroidDebugBridge.IDeviceChangeListener): AndroidDebugBridge.IDeviceChangeListener {
        deviceChangeListeners.add(listener)
      AndroidDebugBridge.addDeviceChangeListener(listener)
        return listener
    }
}
