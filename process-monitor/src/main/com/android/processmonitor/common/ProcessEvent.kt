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
package com.android.processmonitor.common

import com.android.processmonitor.monitor.ProcessNames

/**
 * An event representing processes being added and removed.
 */
internal sealed class ProcessEvent(open val pid: Int) {

    data class ProcessAdded(
        override val pid: Int,
        val applicationId: String?,
        val processName: String
    ) : ProcessEvent(pid) {

        /**
         * Creates a [ProcessNames] from a [ProcessAdded] event.
         *
         * If packageName is null, use processName to guess what it might be.
         *
         *  Based on ClientData::getPackageName(). Specifically, if processName contains a ':',
         *  we assume that the conventions of `"$applicationId:$processName"` is being observed.
         *  Otherwise, use the processName as the applicationId.
         */
        fun toProcessNames(): ProcessNames {
            return ProcessNames(applicationId ?: processName.toPackageName(), processName)
        }
    }

    data class ProcessRemoved(override val pid: Int) : ProcessEvent(pid)
}

private fun String.toPackageName(): String {
    val i = indexOf(':')
    return if (i < 0) this else substring(0, i)
}
