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
package com.android.adblib.tools.debugging

import com.android.adblib.AdbSession
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A component that gets notified of [JdwpPacketView] packets activity from a given
 * [SharedJdwpSession].
 *
 * @see SharedJdwpSessionMonitorFactory
 */
interface SharedJdwpSessionMonitor : AutoCloseable {

    /**
     * Invoked before a [JdwpPacketView] is sent to the underlying JDWP session
     */
    suspend fun onSendPacket(packet: JdwpPacketView)

    /**
     * Invoked when a [JdwpPacketView] is received from the underlying JDWP session
     */
    suspend fun onReceivePacket(packet: JdwpPacketView)
}

/**
 * A component that creates instances of [SharedJdwpSessionMonitor] each time a new JDWP session
 * is established. [SharedJdwpSessionMonitorFactory] instances are registered with
 * [AdbSession.addSharedJdwpSessionMonitorFactory].
 *
 * @see AdbSession.addSharedJdwpSessionMonitorFactory
 * @see AdbSession.removeSharedJdwpSessionMonitorFactory
 */
interface SharedJdwpSessionMonitorFactory {

    /**
     * Creates a [SharedJdwpSessionMonitor] for the given [SharedJdwpSession].
     * Returns `null` if the factory decides the JDWP session is not interesting,
     */
    fun create(session: SharedJdwpSession): SharedJdwpSessionMonitor?
}

/**
 * The [CoroutineScopeCache.Key] for the list of [SharedJdwpSessionMonitorFactory]
 */
private val SharedJdwpSessionMonitorFactoryListKey =
    CoroutineScopeCache.Key<CopyOnWriteArrayList<SharedJdwpSessionMonitorFactory>>("SharedJdwpSessionMonitorFactoryListKey")

/**
 * The list of [SharedJdwpSessionMonitorFactory] associated to this [AdbSession]
 */
internal val AdbSession.sharedJdwpSessionMonitorFactoryList: CopyOnWriteArrayList<SharedJdwpSessionMonitorFactory>
    get() = this.cache.getOrPut(SharedJdwpSessionMonitorFactoryListKey) {
        CopyOnWriteArrayList<SharedJdwpSessionMonitorFactory>()
    }

/**
 * Adds a [SharedJdwpSessionMonitorFactory] for [SharedJdwpSession] of this [AdbSession]
 */
fun AdbSession.addSharedJdwpSessionMonitorFactory(factory: SharedJdwpSessionMonitorFactory) {
    sharedJdwpSessionMonitorFactoryList.add(factory)
}

/**
 * Removes a [SharedJdwpSessionMonitorFactory] for [SharedJdwpSession] of this [AdbSession]
 */
fun AdbSession.removeSharedJdwpSessionMonitorFactory(factory: SharedJdwpSessionMonitorFactory) {
    sharedJdwpSessionMonitorFactoryList.remove(factory)
}
