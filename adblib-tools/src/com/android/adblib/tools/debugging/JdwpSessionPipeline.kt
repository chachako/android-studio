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

import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.isThreadSafeAndImmutable
import com.android.adblib.tools.debugging.utils.SynchronizedReceiveChannel
import com.android.adblib.tools.debugging.utils.SynchronizedSendChannel
import com.android.adblib.tools.debugging.utils.receiveAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ChannelResult

/**
 * A component that acts as a pipeline for the JDWP session between an Android device and
 * a Java debugger.
 *
 * * "sending" means sending [JdwpPacketView] to the "debugger" side
 * * "receiving" means receiving [JdwpPacketView] from the "debugger" side
 *
 * @see JdwpSessionPipelineFactory
 */
interface JdwpSessionPipeline {

    /**
     * The [CoroutineScope] that is active as long as the underlying JDWP session.
     */
    val scope: CoroutineScope

    /**
     * The [SynchronizedSendChannel] used to send [JdwpPacketView] to the "debugger" side of
     * this pipeline.
     */
    val sendChannel: SynchronizedSendChannel<JdwpPacketView>

    /**
     * The [SynchronizedReceiveChannel] of [JdwpPacketView] coming from the "debugger" of
     * this pipeline.
     */
    val receiveChannel: SynchronizedReceiveChannel<JdwpPacketView>
}

suspend inline fun JdwpSessionPipeline.sendPacket(packet: JdwpPacketView) {
    sendChannel.sendPacket(packet)
}

suspend fun SynchronizedSendChannel<JdwpPacketView>.sendPacket(packet: JdwpPacketView) {
    if (packet.isThreadSafeAndImmutable) {
        sendNoWait(packet)
    } else {
        send(packet)
    }
}

suspend inline fun JdwpSessionPipeline.receiveAllPackets(
    block: (JdwpPacketView) -> Unit
): ChannelResult<JdwpPacketView> {
    return receiveChannel.receiveAll(block)
}
