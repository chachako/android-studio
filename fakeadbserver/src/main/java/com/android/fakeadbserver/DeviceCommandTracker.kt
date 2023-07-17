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
package com.android.fakeadbserver

import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks currently running commands (see [DeviceCommandHandler]) on the given
 * [device][deviceSerial] so that they can be cancelled if the device is disconnected.
 */
internal class DeviceCommandTracker(private val deviceSerial: String): AutoCloseable {
    private val nextId = AtomicInteger(0)
    private val activeCommands = ConcurrentHashMap<Int, ActiveCommand>()

    @Volatile
    private var closed = false

    inline fun <R> trackCommand(
        command: String,
        scope: CoroutineScope,
        socket: Socket,
        block: () -> R
    ): R {
        throwIfClosed()

        val id = add(command, scope, socket)
        return try {
            block()
        } finally {
            remove(id)
        }
    }

    private fun add(command: String, scope: CoroutineScope, socket: Socket): Int {
        val id = nextId.incrementAndGet()
        activeCommands[id] = ActiveCommand(deviceSerial, command, scope, socket)
        return id
    }

    private fun remove(commandId: Int) {
        activeCommands.remove(commandId).also { activeCommand ->
            check(activeCommand != null) { "Why is the command '$commandId' not in the map?" }
            activeCommand.close()
        }
    }

    override fun close() {
        if (closed)
            return
        closed = true
        activeCommands.values.forEach {
            it.close()
        }
        activeCommands.clear()
    }

    private fun throwIfClosed() {
        check(!closed) { "${this::class.simpleName} has been closed" }
    }

    private class ActiveCommand(
      val deviceSerial: String,
      val command: String,
      private val scope: CoroutineScope,
      private val socket: Socket
    ) : AutoCloseable {

        override fun close() {
            socket.close()
            scope.cancel("Device '$deviceSerial': cancelling command '$command'")
        }
    }
}
