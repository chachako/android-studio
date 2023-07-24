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
import com.android.adblib.tools.debugging.JdwpProcess
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Extension point an [AdbSession] should use to find the [AdbSession] that is responsible
 * for opening JDWP connections through [JdwpProcess] instances.
 */
interface JdwpProcessSessionFinder {

    /**
     * Returns the [AdbSession] that [forSession] should delegate [JdwpProcess]
     * instances to. If no delegation should occur, returns [forSession].
     */
    fun findDelegateSession(forSession: AdbSession): AdbSession
}

private val JdwpProcessSessionFinderListKey =
    CoroutineScopeCache.Key<CopyOnWriteArrayList<JdwpProcessSessionFinder>>(
        JdwpProcessSessionFinder::class.simpleName!!
    )

internal val AdbSession.jdwpProcessSessionFinderList: CopyOnWriteArrayList<JdwpProcessSessionFinder>
    get() {
        return this.cache.getOrPut(JdwpProcessSessionFinderListKey) {
            CopyOnWriteArrayList()
        }
    }

fun AdbSession.addJdwpProcessSessionFinder(sessionFinder: JdwpProcessSessionFinder) {
    jdwpProcessSessionFinderList.add(sessionFinder)
}

fun AdbSession.removeJdwpProcessSessionFinder(sessionFinder: JdwpProcessSessionFinder) {
    jdwpProcessSessionFinderList.remove(sessionFinder)
}
