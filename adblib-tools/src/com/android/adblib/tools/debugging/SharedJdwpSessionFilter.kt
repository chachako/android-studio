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
 * [SharedJdwpSession], and also has the ability to filter packets received.
 *
 * Callers activate a specific [SharedJdwpSessionFilter] by specifying their [FilterId]
 * when invoking [JdwpPacketReceiver.withFilter].
 *
 * @see SharedJdwpSessionFilterFactory
 */
interface SharedJdwpSessionFilter : AutoCloseable {

    /**
     * The [FilterId] of this instance. This id is used to determine if a filter applies
     * to a specific [JdwpPacketReceiver] instance (see [JdwpPacketReceiver.withFilter]).
     */
    val id: FilterId

    /**
     * Invoked when a packet is about to be sent to the Android Device.
     *
     * Note: This method is called for all packets sent to a [SharedJdwpSession], even
     * if the filter is not active for a specific [JdwpPacketReceiver]
     */
    suspend fun beforeSendPacket(packet: JdwpPacketView)

    /**
     * Invoked when a packet received from the Android Device has been dispatched to all
     * [JdwpPacketReceiver], i.e. just before moving on to the next packet.
     *
     * Note: This method is called for all packets received from a [SharedJdwpSession], even
     * if the filter is not active for a specific [JdwpPacketReceiver]
     */
    suspend fun afterReceivePacket(packet: JdwpPacketView)

    /**
     * Returns `true` if [packet] should be included in the flow of received [JdwpPacketView]
     * (of a specific [JdwpPacketReceiver]).
     *
     * Note: This method is called **only if the filter is active** for a specific
     * [JdwpPacketReceiver] instance and is always called before [afterReceivePacket]
     * for a given packet.
     */
    suspend fun filter(packet: JdwpPacketView): Boolean

    open class FilterId(
        /**
         * A short description of a [SharedJdwpSessionFilter], used for debugging purposes only
         * (i.e. this is **not** an identifier).
         */
        val name: String
    )
}


/**
 * A component that creates instances of [SharedJdwpSessionFilter] each time a new
 * [SharedJdwpSession] is established. [SharedJdwpSessionFilterFactory] instances are
 * registered with [AdbSession.addSharedJdwpSessionFilterFactory].
 *
 * @see AdbSession.addSharedJdwpSessionFilterFactory
 * @see AdbSession.removeSharedJdwpSessionFilterFactory
 */
interface SharedJdwpSessionFilterFactory {

    /**
     * Creates a [SharedJdwpSessionFilter] for the given [SharedJdwpSession].
     * Returns `null` if the factory decides the [session] should not be filtered.
     */
    fun create(session: SharedJdwpSession): SharedJdwpSessionFilter?
}

/**
 * The [CoroutineScopeCache.Key] for the list of [SharedJdwpSessionFilterFactory]
 */
private val sharedJdwpSessionFilterFactoryListKey =
    CoroutineScopeCache.Key<CopyOnWriteArrayList<SharedJdwpSessionFilterFactory>>(
        "sharedJdwpSessionFilterFactoryListKey"
    )

/**
 * The list of [SharedJdwpSessionFilterFactory] associated to this [AdbSession]
 */
internal val AdbSession.sharedJdwpSessionFilterFactoryList: CopyOnWriteArrayList<SharedJdwpSessionFilterFactory>
    get() = this.cache.getOrPut(sharedJdwpSessionFilterFactoryListKey) {
        CopyOnWriteArrayList<SharedJdwpSessionFilterFactory>()
    }

/**
 * Adds a [SharedJdwpSessionFilterFactory] for [SharedJdwpSession] of this [AdbSession]
 */
fun AdbSession.addSharedJdwpSessionFilterFactory(factory: SharedJdwpSessionFilterFactory) {
    sharedJdwpSessionFilterFactoryList.add(factory)
}

/**
 * Removes a [SharedJdwpSessionFilterFactory] for [SharedJdwpSession] of this [AdbSession]
 */
fun AdbSession.removeSharedJdwpSessionFilterFactory(factory: SharedJdwpSessionFilterFactory) {
    sharedJdwpSessionFilterFactoryList.remove(factory)
}
