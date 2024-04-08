/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adblib.tools.debugging.processinventory.server

import com.android.adblib.AdbServerSocket
import com.android.adblib.AdbSession
import com.android.adblib.tools.debugging.processinventory.protos.ProcessInventoryServerProto
import com.android.adblib.tools.tcpserver.TcpServer
import com.android.adblib.utils.createChildScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

/**
 * A [TcpServer] that run on the local host, and serves as a (volatile) inventory
 * of processes running on various Android device.
 *
 * See the [ProcessInventoryServerProto] class for the [requests][ProcessInventoryServerProto.Request]
 * and [responses][ProcessInventoryServerProto.Response] it supports.
 */
internal class ProcessInventoryServer(
    private val session: AdbSession,
    private val config: ProcessInventoryServerConfiguration
) : TcpServer {

    /**
     * [CoroutineScope] shared by all [ProcessInventoryServerInstance] so we can ensure [close]
     * cancels everything (even potentially pending server instances).
     */
    private val scope = session.scope.createChildScope(isSupervisor = true)

    override fun launch(serverSocket: AdbServerSocket): Job {
        // Starts a server instance in our scope
        // Note that we don't need to close the server socket, it is handled by the callee.
        return ProcessInventoryServerInstance(session, config, scope, serverSocket).runAsync()
    }

    override fun close() {
        scope.cancel("${this::class.java.simpleName} has been closed")
    }
}
