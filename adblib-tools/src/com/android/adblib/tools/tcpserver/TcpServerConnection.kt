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
package com.android.adblib.tools.tcpserver

import com.android.adblib.AdbChannel
import com.android.adblib.AdbSession
import com.android.adblib.tools.tcpserver.impl.TcpServerWithFailoverConnection
import kotlinx.coroutines.CoroutineScope
import java.time.Duration

/**
 * Allows connecting to a TCP server that may be volatile, i.e. implementations may retry
 * the socket connection, or even restart the TCP server if needed.
 */
internal interface TcpServerConnection : AutoCloseable {

    /**
     * Executes [block] passing a socket [AdbChannel] connected to the underlying
     * TCP server.
     *
     * See [createWithFailoverConnection] for specific details about retry policy and server
     * execution behavior.
     */
    suspend fun <R> withClientSocket(
        block: suspend (newServerStarted: Boolean, socket: AdbChannel) -> R
    ): R

    companion object {

        /**
         * Creates a new instance of a [TcpServerConnection] that can dynamically connect to
         * an existing server or start a new one as needed.
         *
         * For each call to [TcpServerConnection.withClientSocket], the returned
         * [TcpServerConnection] either connects to an existing TCP server running at a given
         * local host [port], or starts a new [TcpServer] (if the connection failed). The given
         * [retryPolicy] is used whenever there is a failure, so that starting TCP servers
         * concurrently is supported, i.e. a failure to connect/start a server, leads
         * to a retry.
         *
         * @param session The [AdbSession] to use for logging and [CoroutineScope]
         * @param tcpServer The [TcpServer] implementation to run if there is no
         * already active server at [port].
         * @param [port] The TCP port to connect to. This should be a "well known" port
         * of this particular [TcpServer] implementation, so that multiple instances of
         * this [TcpServerConnection] running on separate processes can connect to the
         * same port, i.e. running [TcpServer] implementation.
         * @param connectTimeout Timeout when trying to connect to an existing server
         * @param retryPolicy The [RetryPolicy] to use when executing a
         * [TcpServerConnection.withClientSocket] operation, i.e. the "retry" behavior
         * when a server (or operation) failure occurs during a call to
         * [TcpServerConnection.withClientSocket]
         */
        fun createWithFailoverConnection(
            session: AdbSession,
            tcpServer: TcpServer,
            port: Int,
            connectTimeout: Duration,
            retryPolicy: RetryPolicy,
        ): TcpServerConnection {
            return TcpServerWithFailoverConnection(
                session,
                tcpServer,
                port,
                connectTimeout,
                retryPolicy
            )
        }
    }
}
