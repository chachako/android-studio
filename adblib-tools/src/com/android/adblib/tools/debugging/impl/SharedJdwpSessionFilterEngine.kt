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

import com.android.adblib.tools.debugging.SharedJdwpSessionFilter
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.sharedJdwpSessionFilterFactoryList
import com.android.adblib.tools.debugging.packets.JdwpPacketView

internal class SharedJdwpSessionFilterEngine(
    private val jdwpSession: SharedJdwpSession
) : AutoCloseable {

    private val filters = jdwpSession.device.session.sharedJdwpSessionFilterFactoryList
        .mapNotNull { factory ->
            factory.create(jdwpSession)
        }

    /**
     * Forwards [packet] to all registered [SharedJdwpSessionFilter] when a packet is sent to
     * the Android Device.
     */
    suspend fun beforeSendPacket(packet: JdwpPacketView) {
        filters.forEach { it.beforeSendPacket(packet) }
    }

    /**
     * Forwards [packet] to all registered [SharedJdwpSessionFilter] when a packet is sent to
     * the Android Device.
     */
    suspend fun afterReceivePacket(packet: JdwpPacketView) {
        filters.forEach { it.afterReceivePacket(packet) }
    }

    /**
     * Returns `true` if [packet] should be included in the flow of received packets
     * for the given [filterId].
     */
    suspend fun filterReceivedPacket(
        filterId: SharedJdwpSessionFilter.FilterId?,
        packet: JdwpPacketView
    ): Boolean {
        return if (filterId == null) {
            true // keep packet
        } else {
            // Keep packet only if all filters decide to keep it
            filters.all {
                if (it.id == filterId) {
                    it.filter(packet) // keep or exclude packet
                } else {
                    true // keep packet
                }
            }
        }
    }

    override fun close() {
        filters.forEach { it.close() }
    }
}
