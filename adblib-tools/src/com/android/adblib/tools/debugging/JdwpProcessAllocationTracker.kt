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
package com.android.adblib.tools.debugging

import com.android.adblib.AdbInputChannel

/**
 * Handles collecting memory allocations for a [JdwpProcess].
 *
 * While [isEnabled] is `true`, a [SharedJdwpSession] is opened and the Android VM collects
 * allocation tracking info.
 *
 * [fetchAllocationDetails] should only be called when [isEnabled] is `true` and provides
 * a summary of the memory allocations since the last call to [enable].
 */
interface JdwpProcessAllocationTracker {

    suspend fun isEnabled(progress: JdwpCommandProgress? = null): Boolean

    suspend fun enable(enabled: Boolean, progress: JdwpCommandProgress? = null)

    suspend fun <R> fetchAllocationDetails(
        progress: JdwpCommandProgress? = null,
        replyHandler: suspend (data: AdbInputChannel, length: Int) -> R
    ): R
}
