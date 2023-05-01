/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.fakeadbserver.devicecommandhandlers

import com.android.fakeadbserver.CommandHandler
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import kotlinx.coroutines.CoroutineScope
import java.net.Socket

/**
 * DeviceCommandHandlers handle commands directed at a device. This includes host-prefix: commands
 * as per the protocol doc, as well as device commands after calling host:transport[-*].
 */
open class DeviceCommandHandler(@JvmField protected val command: String) : CommandHandler() {

    /**
     * Processes the command and arguments. If this handler accepts it, it will execute it and
     * return true.
     */
    open fun accept(
        server: FakeAdbServer,
        socketScope: CoroutineScope,
        socket: Socket,
        device: DeviceState,
        command: String,
        args: String
    ): Boolean {
        return if (this.command == command) {
            try {
                invoke(server, socketScope, socket, device, args)
                true
            } catch (e: NextHandlerException) {
                // The handler does not want to handle this command
                false
            }
        } else false
    }

    /**
     * Invokes this command. This method is only called if the handler accepts the command with its
     * arguments.
     */
    open operator fun invoke(
        server: FakeAdbServer,
        socketScope: CoroutineScope,
        socket: Socket,
        device: DeviceState,
        args: String
    ) {
    }

    /**
     * Exception thrown by [DeviceCommandHandler] implementations that want to pass the
     * command to the next handler in line.
     */
    class NextHandlerException : UnsupportedOperationException()
}
