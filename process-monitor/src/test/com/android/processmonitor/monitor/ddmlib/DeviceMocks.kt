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
package com.android.processmonitor.monitor.ddmlib

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.DeviceState
import com.android.ddmlib.IDevice.DeviceState.OFFLINE
import com.android.sdklib.AndroidVersion
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Mocks for [IDevice] and [Client]
 */
internal fun mockDevice(
    serialNumber: String,
    state: DeviceState = OFFLINE,
    apiLevel: Int = 33,
    abi: String = "abi",
): IDevice = mock {
    on { getSerialNumber() } doReturn serialNumber
    on { toString() } doReturn serialNumber
    on { getState() } doReturn state
    on { isOnline } doReturn (state == DeviceState.ONLINE)
    on { isOffline } doReturn (state != DeviceState.ONLINE)
    on { clients } doReturn emptyArray()
    on { version } doReturn AndroidVersion(apiLevel)
    on { abis } doReturn listOf(abi)
}

internal fun IDevice.setState(state: DeviceState): IDevice {
    whenever(this.state).thenReturn(state)
    return this
}

internal fun IDevice.withClients(vararg clients: Client): IDevice {
    whenever(this.clients).thenReturn(clients)
    clients.forEach { whenever(it.device).thenReturn(this) }
    return this
}

internal fun mockClient(pid: Int, packageName: String?, processName: String?): Client {
  val mockClientData = mockClientData(pid, packageName, processName)
    return mock {
        on { clientData } doReturn mockClientData
        on { toString() } doReturn "pid=$pid packageName=$packageName"
    }
}

private fun mockClientData(pid: Int, packageName: String?, processName: String?): ClientData = mock {
    on { getPid() } doReturn pid
    on { getPackageName() } doReturn packageName
    on { getProcessName() } doReturn processName
}
