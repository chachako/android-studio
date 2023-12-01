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
import com.android.adblib.adbLogger
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.JdwpSessionPipeline
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.sendPacket
import com.android.adblib.tools.debugging.utils.SynchronizedChannel
import com.android.adblib.tools.debugging.utils.SynchronizedReceiveChannel
import com.android.adblib.tools.debugging.utils.SynchronizedSendChannel
import com.android.adblib.tools.debugging.utils.logIOCompletionErrors
import com.android.adblib.tools.debugging.utils.receiveAll
import com.android.adblib.utils.createChildScope
import com.android.adblib.withPrefix
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import java.io.EOFException
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

    private val logger = adbLogger(device.session)
        .withPrefix("${device.session} - $device - pid=$pid - ")

    private val sendChannelImpl = SynchronizedChannel<JdwpPacketView>()

    private val receiveChannelImpl = SynchronizedChannel<JdwpPacketView>()

    override val scope = debuggerSession.scope.createChildScope(isSupervisor = true)

    override val sendChannel: SynchronizedSendChannel<JdwpPacketView>
        get() = sendChannelImpl

    override val receiveChannel: SynchronizedReceiveChannel<JdwpPacketView>
        get() = receiveChannelImpl

    init {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            logger.logIOCompletionErrors(throwable)
        }

        // Note: We use a custom exception handler because we handle exceptions, and we don't
        // want them to go to the parent scope handler as "unhandled" exceptions in a `launch` job.
        // Note: We cancel both channels on completion so that we never leave one of the two
        // coroutine running if the other completed.
        scope.launch(session.ioDispatcher + exceptionHandler) {
            forwardSendChannelToDebuggerSession()
        }.invokeOnCompletion {
            cancelChannels(it)
        }

        scope.launch(session.ioDispatcher + exceptionHandler) {
            forwardDebuggerSessionToReceiveChannel()
        }.invokeOnCompletion {
            cancelChannels(it)
        }
    }

    override fun toString(): String {
        return "${this::class.simpleName}"
    }

    private suspend fun forwardSendChannelToDebuggerSession() {
        // Note: 'receiveAll' is a terminal operator, throws exception when completed or cancelled
        sendChannelImpl.receiveAll { packet ->
            logger.verbose { "Sending packet to debugger: $packet" }
            debuggerSession.sendPacket(packet)
        }
    }

    private suspend fun forwardDebuggerSessionToReceiveChannel() {
        // Note: Throws exception when completed or cancelled
        while (true) {
            logger.verbose { "Waiting for next JDWP packet from session" }
            val packet = try {
                debuggerSession.receivePacket()
            } catch (e: EOFException) {
                // Reached EOF, close the "receive" channel "normally"
                receiveChannelImpl.close(e)
                throw e
            }
            logger.verbose { "Emitting packet from debugger to receive flow: $packet" }
            receiveChannelImpl.sendPacket(packet)
        }
    }

    private fun cancelChannels(throwable: Throwable?) {
        // Ensure exception is propagated to channels so that
        // 1) callers (i.e. consumer of 'send' and 'receive' channels) get notified of errors
        // 2) both forwarding coroutines always complete together
        val cancellationException = (throwable as? CancellationException)
            ?: CancellationException("Debugger pipeline for JDWP session has completed", throwable)
        sendChannelImpl.cancel(cancellationException)
        receiveChannelImpl.cancel(cancellationException)
        debuggerSession.close()
    }
}
