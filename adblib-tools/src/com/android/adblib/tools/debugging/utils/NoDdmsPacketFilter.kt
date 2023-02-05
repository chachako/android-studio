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
package com.android.adblib.tools.debugging.utils

import com.android.adblib.serialNumber
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.SharedJdwpSessionFilterFactory
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSessionFilter
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.ddms.isDdmsCommand
import com.android.adblib.withPrefix
import java.util.concurrent.ConcurrentHashMap

/**
 * A [SharedJdwpSessionFilter] that filter out DDMS command packets and their replies.
 */
internal class NoDdmsPacketFilter(session: SharedJdwpSession) : SharedJdwpSessionFilter {

    private val logger = thisLogger(session.device.session)
        .withPrefix("device=${session.device.serialNumber}, pid=${session.pid}: ")

    private val activeDdmsCommands = ConcurrentHashMap.newKeySet<Int>()

    override val id: SharedJdwpSessionFilter.FilterId
        get() = NoDdmsPacketFilterFactory.filterId

    /**
     * A packet is sent to the device, store its ID in our list of active
     * requests, so we can skip it later
     */
    override suspend fun beforeSendPacket(packet: JdwpPacketView) {
        if (packet.isDdmsCommand) {
            logger.verbose { "Adding packet id=${packet.id}" }
            activeDdmsCommands.add(packet.id)
        }
    }

    override suspend fun afterReceivePacket(packet: JdwpPacketView) {
        if (packet.isReply && activeDdmsCommands.isNotEmpty()) {
            activeDdmsCommands.remove(packet.id).also { removed ->
                if (removed) {
                    logger.verbose { "Removing packet id=${packet.id}" }
                }
            }
        }
    }

    override suspend fun filter(packet: JdwpPacketView): Boolean {
        if (packet.isReply && activeDdmsCommands.isNotEmpty()) {
            if (activeDdmsCommands.contains(packet.id)) {
                logger.verbose { "Skipping packet id=${packet.id} because it is the reply to a DDMS command" }
                return false // exclude
            }
        }

        if (packet.isDdmsCommand) {
            logger.verbose { "Skipping packet id=${packet.id} because it is a DDMS command from the device" }
            return false // exclude
        }

        return true // keep
    }

    override fun close() {
        logger.verbose { "Closing with ${activeDdmsCommands.size} entries left" }
    }
}

class NoDdmsPacketFilterFactory : SharedJdwpSessionFilterFactory {

    override fun create(session: SharedJdwpSession): SharedJdwpSessionFilter {
        return NoDdmsPacketFilter(session)
    }

    companion object {

        val filterId = SharedJdwpSessionFilter.FilterId("NoDdmsPacketFilter")
    }
}
