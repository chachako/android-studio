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
package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.ConnectedDevice
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import kotlinx.coroutines.Deferred

internal interface ProcessTrackerHost {

    val device: ConnectedDevice

    val iDevice: IDevice

    suspend fun clientsUpdated(list: List<Client>)

    suspend fun postClientUpdated(
        clientWrapper: AdblibClientWrapper,
        updateKind: ClientUpdateKind
    ): Deferred<Unit>

    enum class ClientUpdateKind {
        NameOrProperties,
        DebuggerConnectionStatus,
        HeapAllocations,
        ProfilingStatus,
    }
}
