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
package com.android.fakeadbserver

import com.android.fakeadbserver.hostcommandhandlers.HostCommandHandler
import java.util.Optional

/**
 * Provides on-demand access to a [DeviceState] for a [HostCommandHandler].
 *
 * The reason an "on-demand" interface is needed is that some [HostCommandHandler]
 * implementations allow for device selector (in the query string) corresponding to devices
 * that are either not yet connected or already disconnected.
 *
 * On the other hand, most [HostCommandHandler] implementations call [invoke] with `reportError`
 * set to `true` and bail out if [DeviceResult] is not [DeviceResult.One].
 */
fun interface DeviceStateSelector {

    /**
     * Invoke this [DeviceStateSelector] and returns a [DeviceResult] corresponding
     * to the [DeviceState] specified in a [HostCommandHandler] command.
     *
     * The caller sets [reportError] to `true` to indicate if a `FAIL` reply should be written
     * to the socket in case the return value is `null`.
     */
    fun invoke(reportError: Boolean): DeviceResult

    sealed class DeviceResult {
        /**
         * No device corresponding to the device selector was found.
         * This is typically an error case.
         */
        object None : DeviceResult()

        /**
         * A single device corresponding to the device selector was found.
         * This is typically a successful case.
         */
        class One(val deviceState: DeviceState) : DeviceResult()

        /**
         * More than one device corresponding to the device selector was found.
         * This is typically an error case.
         */
        object Ambiguous : DeviceResult()
    }

    companion object {

        /**
         * Returns a [DeviceStateSelector] that lazily analyses a list of [DeviceState]
         * provided by [deviceListProvider] to make sure there is one (and only one)
         * [DeviceState] in the list. This is useful for device selector in command that
         * can be ambiguous (e.g. "any USB device").
         */
        fun forCollection(
            deviceListProvider: () -> List<DeviceState>,
            errorReporter: (List<DeviceState>) -> Unit,
            successBlock: (DeviceState) -> Unit = { }
        ): DeviceStateSelector {
            return DeviceStateSelector { reportError ->
                val deviceList = deviceListProvider()
                if (deviceList.isEmpty()) {
                    if (reportError) {
                        errorReporter(deviceList)
                    }
                    DeviceResult.None
                } else if (deviceList.size == 1) {
                    DeviceResult.One(deviceList[0]).also { one ->
                        successBlock(one.deviceState)
                    }
                } else {
                    if (reportError) {
                        errorReporter(deviceList)
                    }
                    DeviceResult.Ambiguous
                }
            }
        }

        /**
         * Returns a [DeviceStateSelector] that lazily calls [deviceProvider] and makes sure there
         * is a [DeviceState]. This is useful for device selector in command that can identify a
         * device not currently connected (e.g. "device with serial #xxx").
         */
        fun forOptional(
            deviceProvider: () -> Optional<DeviceState>,
            errorReporter: (Optional<DeviceState>) -> Unit,
            successBlock: (DeviceState) -> Unit = { }
        ): DeviceStateSelector {
            return DeviceStateSelector { reportError ->
                val deviceList = deviceProvider()
                if (deviceList.isEmpty) {
                    if (reportError) {
                        errorReporter(deviceList)
                    }
                    DeviceResult.None
                } else {
                    DeviceResult.One(deviceList.get()).also {
                        successBlock(it.deviceState)
                    }
                }
            }
        }

        /**
         * Returns a [DeviceStateSelector] that always reports an error. This is useful for device
         * selector that are not supported or invalid.
         */
        fun forError(errorReporter: () -> Unit): DeviceStateSelector {
            return DeviceStateSelector { reportError ->
                if (reportError) {
                    errorReporter()
                }
                DeviceResult.None
            }
        }
    }
}
