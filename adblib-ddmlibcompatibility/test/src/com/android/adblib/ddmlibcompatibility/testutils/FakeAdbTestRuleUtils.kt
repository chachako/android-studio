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
package com.android.adblib.ddmlibcompatibility.testutils

import com.android.adblib.AdbServerChannelProvider
import com.android.adblib.AdbSession
import com.android.adblib.ddmlibcompatibility.AdbLibIDeviceManagerFactory
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.ddmlib.idevicemanager.IDeviceManagerFactory

fun createIDeviceManagerFactoryFactory(getAdbServerPort: () -> Int, closeables: CloseablesRule):
            () -> IDeviceManagerFactory = {
    AdbLibIDeviceManagerFactory(createAdbSession(getAdbServerPort(), closeables))
}

private fun createAdbSession(adbServerPort: Int, closeables: CloseablesRule): AdbSession {
    val host = TestingAdbSessionHost().also { closeables.register(it) }
    val channelProvider = AdbServerChannelProvider.createOpenLocalHost(host) { adbServerPort }
    return closeables.register(AdbSession.create(host, channelProvider))
}
