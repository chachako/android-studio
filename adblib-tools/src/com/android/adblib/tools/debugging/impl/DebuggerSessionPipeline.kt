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
import com.android.adblib.ConnectedDevice
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.JdwpSessionPipeline
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.sendPacket
import com.android.adblib.tools.debugging.utils.SynchronizedChannel
import com.android.adblib.tools.debugging.utils.SynchronizedReceiveChannel
import com.android.adblib.tools.debugging.utils.SynchronizedSendChannel
import com.android.adblib.tools.debugging.utils.receiveAllCatching
import com.android.adblib.utils.createChildScope
import com.android.adblib.withPrefix
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.io.EOFException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * The [JdwpSessionPipeline] implementation that wraps the [JdwpSession] directly
 * connected to an external JDWP debugger (e.g. Android Studio or IntelliJ).
 * The purpose is to allow chaining multiple [JdwpSessionPipeline] implementations
 * together, starting from the external debugger side.
 */
internal class DebuggerSessionPipeline(
    session: AdbSession,
    private val debuggerSession: JdwpSession,
    pid: Int
) : JdwpSessionPipeline {

    private val device: ConnectedDevice
        get() = debuggerSession.device

    private val logger = thisLogger(device.session)
        .withPrefix("${device.session} - $device - pid=$pid -")

    private val sendChannelImpl = SynchronizedChannel<JdwpPacketView>()

    private val receiveChannelImpl = SynchronizedChannel<JdwpPacketView>()

    override val scope = debuggerSession.scope.createChildScope(isSupervisor = true)

    override val sendChannel: SynchronizedSendChannel<JdwpPacketView>
        get() = sendChannelImpl

    override val receiveChannel: SynchronizedReceiveChannel<JdwpPacketView>
        get() = receiveChannelImpl

    init {
        scope.launch(session.ioDispatcher) {
            forwardSendChannelToDebuggerSession()
        }
        scope.launch(session.ioDispatcher) {
            forwardDebuggerSessionToReceiveChannel()
        }

        scope.coroutineContext.job.invokeOnCompletion { throwable ->
            logger.debug { "Closing channels on scope completion (throwable=$throwable)" }
            sendChannelImpl.cancel(throwable as? CancellationException)
            receiveChannelImpl.cancel(throwable as? CancellationException)
        }
    }

    override fun toString(): String {
        return "${this::class.simpleName}"
    }

    private suspend fun forwardSendChannelToDebuggerSession() {
        sendChannelImpl.receiveAllCatching { packet ->
            logger.verbose { "Sending packet to debugger: $packet" }
            debuggerSession.sendPacket(packet)
        }.onClosed { throwable ->
            logger.debug(throwable) { "Channel is closed, exiting loop" }
        }.onFailure { throwable ->
            logger.warn(throwable, "Receiving elements from a channel should never fail")
        }
    }

    private suspend fun forwardDebuggerSessionToReceiveChannel() {
        while (true) {
            logger.verbose { "Waiting for next JDWP packet from session" }
            val packet = try {
                debuggerSession.receivePacket()
            } catch (e: EOFException) {
                // Reached EOF, flow terminates
                logger.debug { "JDWP session has ended with EOF" }
                break
            } catch (e: IOException) {
                // I/O exception (e.g. socket closed), flow terminates
                logger.info(e) { "JDWP session has ended with I/O exception" }
                break
            }
            logger.verbose { "Emitting packet from debugger to receive flow: $packet" }
            receiveChannelImpl.sendPacket(packet)
        }
    }
}
