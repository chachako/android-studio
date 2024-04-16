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
package com.android.adblib.tools.tcpserver.impl

import com.android.adblib.AdbChannel
import com.android.adblib.AdbServerSocket
import com.android.adblib.AdbSession
import com.android.adblib.adbLogger
import com.android.adblib.skipRemaining
import com.android.adblib.tools.debugging.rethrowCancellation
import com.android.adblib.tools.debugging.utils.logIOCompletionErrors
import com.android.adblib.tools.tcpserver.RetryPolicy
import com.android.adblib.tools.tcpserver.TcpServer
import com.android.adblib.tools.tcpserver.TcpServerConnection
import com.android.adblib.utils.closeOnException
import com.android.adblib.utils.createChildScope
import com.android.adblib.utils.runAlongOtherScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.TimeUnit

internal class TcpServerWithFailoverConnection(
    private val session: AdbSession,
    private val tcpServer: TcpServer,
    port: Int,
    private val connectTimeout: Duration,
    private val retryPolicy: RetryPolicy,
) : TcpServerConnection {

    private val logger = adbLogger(session)

    private val scope = session.scope.createChildScope(isSupervisor = true)

    private val serverLocalAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), port)

    private val serverConnection = ServerConnection(serverLocalAddress)

    override suspend fun <R> withClientSocket(
        block: suspend (newServerStarted: Boolean, socket: AdbChannel) -> R
    ): R {
        return runAlongOtherScope(scope) {
            retryWithPolicy(retryPolicy) {
                logger.verbose { "Connecting to server: $serverConnection" }
                serverConnection.connect().use { connectionResult ->
                    val newServerStarted = connectionResult.newServerStarted
                    val socketChannel = connectionResult.socketChannel
                    logger.verbose { "Executing operation after connecting to server at '$socketChannel'" }
                    val result = try {
                        block(newServerStarted, socketChannel)
                    } catch (t: Throwable) {
                        logger.logIOCompletionErrors(
                            t,
                            "An operation involving a TCP server at $socketChannel failed"
                        )
                        throw t
                    }

                    // Close client socket "orderly" so all data is sent to peer. If this fails
                    // then we retry (we assume the server did not get all the data)
                    logger.verbose { "Shutting down socket after successful execution" }
                    socketChannel.shutdownOutput()
                    socketChannel.skipRemaining()
                    socketChannel.close()
                    logger.verbose { "Returning '$result' after successful execution" }
                    result
                }
            }
        }
    }

    override fun close() {
        scope.cancel("${this::class.simpleName} has been closed")
        serverConnection.close()
        tcpServer.close()
    }

    private suspend inline fun <R> retryWithPolicy(retryPolicy: RetryPolicy, block: () -> R): R {
        val exceptions = mutableListOf<Throwable>()
        var retryDelayIterator: Iterator<Duration>? = null
        var failureCount = 0
        while (true) {
            try {
                return block()
            } catch (t: Throwable) {
                currentCoroutineContext().ensureActive()
                exceptions.addWithCap(t, cap = 10) // Keep 10 exceptions max.
                failureCount++

                if (retryDelayIterator == null) {
                    retryDelayIterator = retryPolicy.newDelaySequence().iterator()
                }
                if (!retryDelayIterator.hasNext()) {
                    throw IOException("Operation failed after $failureCount attempt(s)").also {
                        exceptions.forEach { throwable ->
                            it.addSuppressed(throwable)
                        }
                    }.also {
                        logger.verbose(it) { "Aborting after $failureCount failures" }
                    }
                }
                val retryDelay = retryDelayIterator.next()
                logger.debug(t) { "Operation failed $failureCount time(s), retrying in " +
                        "${retryDelay.toMillis()} millis" }
                delay(retryDelay.toMillis())
            }
        }
    }

    private fun <E> MutableList<E>.addWithCap(element: E, cap: Int) {
        if (size >= cap && size > 0) {
            removeAt(0)
        }
        add(element)
    }

    private inner class ServerConnection(private val initialServerAddress: InetSocketAddress) : AutoCloseable {
        private val logger = adbLogger(session)
        private val connectMutex = Mutex()
        private var currentServerJob: Job?  = null
        private var currentServerSocket: AdbServerSocket? = null
        private var currentServerAddress: InetSocketAddress = initialServerAddress

        suspend fun connect(): ConnectionResult {
            return connectMutex.withLock {
                try {
                    ConnectionResult(newServerStarted = false, tryConnect())
                } catch (throwable: Throwable) {
                    // Don't retry if we have been cancelled
                    currentCoroutineContext().ensureActive()
                    logger.info { "Cannot connect to server at address '$currentServerAddress', " +
                            "trying again after starting TCP server (error was ('$throwable')" }
                    logger.debug(throwable) { "Exception from previous entry:" }
                    ConnectionResult(newServerStarted = true, tryStartAndConnect())
                }
            }
        }

        override fun toString(): String {
            return "${this::class.java.simpleName}(currentServerSocket=$currentServerSocket, " +
                    "currentServerJob=$currentServerJob"
        }

        private suspend fun tryConnect(): AdbChannel {
            return session.channelFactory.connectSocket(
                currentServerAddress,
                connectTimeout.toMillis(),
                TimeUnit.MILLISECONDS)
        }

        private suspend fun tryStartAndConnect(): AdbChannel {
            closeCurrentServer()
            tryStartServer()
            return tryConnect()
        }

        private suspend fun tryStartServer() {
            session.channelFactory.createServerSocket().closeOnException { serverSocket ->
                currentServerAddress = tryBind(serverSocket)
                currentServerJob = tcpServer.launch(serverSocket)
                currentServerSocket = serverSocket
                logger.verbose { "Started server at '$currentServerAddress' with server " +
                        "socket '$currentServerSocket'" }
            }
        }

        private suspend fun tryBind(serverSocket: AdbServerSocket): InetSocketAddress {
            val result = kotlin.runCatching { serverSocket.bind(serverLocalAddress) }
            return result.getOrNull() ?: run {
                throw IOException("Error starting server on socket", result.exceptionOrNull())
            }
        }

        private fun closeCurrentServer() {
            currentServerSocket?.close()
            currentServerSocket = null
            currentServerJob?.cancel("${this::class.java.simpleName} has been closed")
            currentServerJob = null
            // Reset address in case the initial address was "unbound", so we support dynamic
            // TCP ports.
            currentServerAddress = initialServerAddress
        }

        override fun close() {
            closeCurrentServer()
        }
    }

    private class ConnectionResult(
        val newServerStarted: Boolean,
        val socketChannel: AdbChannel
    ) : AutoCloseable {

        override fun close() {
            socketChannel.close()
        }

    }
}
