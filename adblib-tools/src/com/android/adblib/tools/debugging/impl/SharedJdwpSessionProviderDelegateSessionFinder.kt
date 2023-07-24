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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.AdbSession
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.tools.debugging.SharedJdwpSession
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Extension point to allow finding the [AdbSession] an [AdbSession] should delegate to handle
 * [SharedJdwpSession] JDWP connections.
 */
interface SharedJdwpSessionProviderDelegateSessionFinder {

    fun findDelegateSession(forSession: AdbSession): AdbSession
}

private val SharedJdwpSessionProviderDelegateSessionFinderListKey =
    CoroutineScopeCache.Key<CopyOnWriteArrayList<SharedJdwpSessionProviderDelegateSessionFinder>>(
        SharedJdwpSessionProviderDelegateSessionFinder::class.simpleName!!
    )

internal val AdbSession.sharedJdwpSessionProviderDelegateSessionFinderList:
        CopyOnWriteArrayList<SharedJdwpSessionProviderDelegateSessionFinder>
    get() {
        return this.cache.getOrPut(SharedJdwpSessionProviderDelegateSessionFinderListKey) {
            CopyOnWriteArrayList()
        }
    }

fun AdbSession.addSharedJdwpSessionProviderDelegateSessionFinder(
    delegateSessionFinder: SharedJdwpSessionProviderDelegateSessionFinder
) {
    sharedJdwpSessionProviderDelegateSessionFinderList.add(delegateSessionFinder)
}

fun AdbSession.removeSharedJdwpSessionProviderDelegateSessionFinder(
    delegateSessionFinder: SharedJdwpSessionProviderDelegateSessionFinder
) {
    sharedJdwpSessionProviderDelegateSessionFinderList.remove(delegateSessionFinder)
}
