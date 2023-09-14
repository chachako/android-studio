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

import com.android.adblib.adbLogger
import com.android.adblib.tools.debugging.AppProcess
import com.android.adblib.tools.debugging.retrieveProcessName
import com.android.adblib.tools.debugging.scope
import com.android.ddmlib.ProfileableClient
import com.android.ddmlib.ProfileableClientData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Implementation of the ddmlib [ProfileableClient] interface based on a [AppProcess] instance.
 */
internal class AdblibProfileableClientWrapper(
    private val trackerHost: ProcessTrackerHost,
    private val appProcess: AppProcess
) : ProfileableClient {

    private val logger = adbLogger(trackerHost.device.session)

    private val data = ProfileableClientData(appProcess.pid, "", appProcess.architecture)

    /**
     * [AdblibClientWrapper] instance if this process is [AppProcess.debuggable], `null` otherwise
     */
    val clientWrapper: AdblibClientWrapper? = appProcess.jdwpProcess?.let { AdblibClientWrapper(trackerHost, it) }

    val debuggable: Boolean
        get() = appProcess.debuggable

    val profileable: Boolean
        get() = appProcess.profileable

    override fun getProfileableClientData(): ProfileableClientData {
        return data
    }

    fun startTracking() {
        // Fetch process name for "AppProcess"
        appProcess.scope.launch {
            retrieveAppProcessName()
        }

        // Fetch JDWP process properties if this is a JDWP process
        clientWrapper?.startTracking()
    }

    private suspend fun retrieveAppProcessName() {
        runCatching {
            appProcess.retrieveProcessName()
        }.onFailure { throwable ->
            if (throwable !is CancellationException) {
                logger.warn(throwable, "Error retrieving process name for $appProcess: ${throwable.message}")
            }
        }.onSuccess { processName ->
            data.processName = processName
            trackerHost.postProfileableClientUpdated(this)
        }
    }
}
