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
import com.android.adblib.AdbServerSocket
import com.android.adblib.AdbSession
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.CoroutineTestUtils
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.createChildScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TcpServerTest {
    @JvmField
    @Rule
    val closeables = CloseablesRule()

    @Test
    fun serverIsNotStartedRightAway(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(FakeAdbSession())
        val tcpServer = MyTestTcpServer(session)

        // Act
        registerCloseable(TcpServerConnection.createWithFailoverConnection(
            session,
            tcpServer,
            0 /* "0" to automatically bind to a new port */,
            Duration.ofSeconds(1),
            RetryPolicy.none(),
        ))

        // Assert
        assertEquals(0, tcpServer.launchedCallCount.get())
        assertEquals(false, tcpServer.closed.get())
    }

    @Test
    fun serverIsClosedOnClose(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(FakeAdbSession())
        val tcpServer = MyTestTcpServer(session)
        val server = registerCloseable(TcpServerConnection.createWithFailoverConnection(
            session,
            tcpServer,
            0 /* "0" to automatically bind to a new port */,
            Duration.ofSeconds(1),
            RetryPolicy.none(),
        ))

        // Act
        server.close()

        // Assert
        assertEquals(0, tcpServer.launchedCallCount.get())
        assertEquals(true, tcpServer.closed.get())
    }

    @Test
    fun serverIsLaunchedOnlyOnceOnConnection(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(FakeAdbSession())
        val tcpServer = MyTestTcpServer(session)
        val server = registerCloseable(TcpServerConnection.createWithFailoverConnection(
            session,
            tcpServer,
            0 /* "0" to automatically bind to a new port */,
            Duration.ofSeconds(1),
            RetryPolicy.none(),
        ))

        // Act
        val response = server.withClientSocket { _, socketChannel ->
            val socket = MyTestTcpServer.wrapClientSocket(socketChannel)
            socket.writeHello()
            socket.readString()
        }

        // Assert
        assertEquals("Hello", response)
        assertEquals(1, tcpServer.launchedCallCount.get())
        assertEquals(false, tcpServer.closed.get())
    }

    @Test
    fun serverIsReusedIfAvailable(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val requestCount = 5
        val session = registerCloseable(FakeAdbSession())
        val tcpServer = MyTestTcpServer(session)
        val server = registerCloseable(TcpServerConnection.createWithFailoverConnection(
            session,
            tcpServer,
            0 /* "0" to automatically bind to a new port */,
            Duration.ofSeconds(1),
            RetryPolicy.none(),
        ))

        // Act
        val responses = (1..requestCount).map {
            server.withClientSocket { _, socketChannel ->
                val socket = MyTestTcpServer.wrapClientSocket(socketChannel)
                socket.writeHello()
                socket.readString()
            }
        }

        // Assert
        assertEquals(generateSequence { "Hello" }.take(requestCount).toList(), responses)
        assertEquals(1, tcpServer.launchedCallCount.get())
        assertEquals(requestCount, tcpServer.requestCount.get())
        assertEquals(false, tcpServer.closed.get())
    }

    @Test
    fun serverIsTransparentToException(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(FakeAdbSession())
        val tcpServer = MyTestTcpServer(session)
        val server = registerCloseable(TcpServerConnection.createWithFailoverConnection(
            session,
            tcpServer,
            0 /* "0" to automatically bind to a new port */,
            Duration.ofSeconds(1),
            RetryPolicy.none(),
        ))

        // Act
        val result = runCatching {
            server.withClientSocket<Nothing> { _, _ ->
                throw Exception("Foo")
            }
        }

        // Assert
        assertEquals(1, tcpServer.launchedCallCount.get())
        assertEquals(false, tcpServer.closed.get())
        assertTcpServerException(result.exceptionOrNull(), Exception::class.java, "Foo")
    }

    @Test
    fun serverAllowsCancellationFromCustomBlock(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(FakeAdbSession())
        val tcpServer = MyTestTcpServer(session)
        val server = registerCloseable(TcpServerConnection.createWithFailoverConnection(
            session,
            tcpServer,
            0 /* "0" to automatically bind to a new port */,
            Duration.ofSeconds(1),
            RetryPolicy.none(),
        ))

        // Act
        val deferredStart = CompletableDeferred<Unit>()
        val deferred = async {
            server.withClientSocket { _, _ ->
                deferredStart.complete(Unit)
                delay(5_000)
            }
        }
        deferredStart.await()
        deferred.cancel("Cancellation from test")
        val result = runCatching { deferred.await() }

        // Assert
        assertEquals(1, tcpServer.launchedCallCount.get())
        assertEquals(false, tcpServer.closed.get())
        assertEquals(CancellationException::class.java, result.exceptionOrNull()?.javaClass)
        assertEquals("Cancellation from test", result.exceptionOrNull()?.message)
    }

    @Test
    fun serverRetryPolicyIsUsedIfServerFails(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val retryCount = 3
        val session = registerCloseable(FakeAdbSession())
        val tcpServer = object: MyTestTcpServer(session) {
            var runCount = 0
            override suspend fun runServer(serverSocket: AdbServerSocket) {
                runCount++
                if (runCount == retryCount) {
                    super.runServer(serverSocket)
                } else {
                    // Simulate a "crashing" server
                    serverSocket.close()
                }
            }
        }
        val retryPolicy = object : RetryPolicy {
            override fun newDelaySequence(): Sequence<Duration> {
                return generateSequence { Duration.ofMillis(10) }.take(retryCount)
            }

        }
        val server = registerCloseable(TcpServerConnection.createWithFailoverConnection(
            session,
            tcpServer,
            0 /* "0" to automatically bind to a new port */,
            Duration.ofSeconds(1),
            retryPolicy,
        ))

        // Act
        val response = server.withClientSocket { _, socketChannel ->
            val socket = MyTestTcpServer.wrapClientSocket(socketChannel)
            socket.writeHello()
            socket.readString()
        }

        // Assert
        assertEquals("Hello", response)
        assertEquals(3, tcpServer.launchedCallCount.get())
        assertEquals(false, tcpServer.closed.get())
    }

    @Ignore("b/347934220")
    @Test
    fun serverConnectsToOnlyOneOfManyServers(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val freePort = findFreeTcpPort()
        val serverCount = 5
        val queryPerServerCount = 2
        val servers = createMultipleServers(freePort).take(serverCount).toList()

        // Act
        val responses = (1..queryPerServerCount).flatMap {
            servers.map { serverInfo ->
                async {
                    val server = serverInfo.server
                    server.withClientSocket { _, socketChannel ->
                        val socket = MyTestTcpServer.wrapClientSocket(socketChannel)
                        socket.writeHello()
                        socket.readString()
                    }
                }
            }
        }.awaitAll()

        // Assert
        val queryCount = serverCount * queryPerServerCount
        assertEquals(generateSequence{"Hello"}.take(queryCount).toList(), responses)
        val activatedServers = servers.filter { it.tcpServer.launchedCallCount.get() > 0 }
        assertEquals(1, activatedServers.size)
        assertEquals(queryCount, activatedServers[0].tcpServer.requestCount.get())
    }

    @Test
    fun serverSupportFailOverIfTcpServerFails(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val freePort = findFreeTcpPort()
        val serverCount = 5
        val servers = (1..serverCount).map {
            createFailoverServer(freePort, serverFactory = { session ->
                object : MyTestTcpServer(session) {
                    override suspend fun runServer(serverSocket: AdbServerSocket) {
                        // Accept one connection then fail forever
                        serverSocket.accept().use { runOneServerRequest(it) }
                        serverSocket.close()
                    }
                }
            })
        }

        // Act: Start with "failingServer" to ensure it is stated first, but fails on the 2nd
        // request it receives. Then use "server" to test the failover behavior, i.e. a failover
        // "MyTestTcpServer" is started and takes over the TCP port.
        val responses = servers.map {
            it.server.withClientSocket { _, socketChannel ->
                val socket = MyTestTcpServer.wrapClientSocket(socketChannel)
                socket.writeHello()
                socket.readString()
            }
        }

        // Assert
        assertEquals(generateSequence { "Hello" }.take(serverCount).toList(), responses)
        servers.forEach {
            assertEquals(1, it.tcpServer.requestCount.get())
        }
    }

    private suspend fun findFreeTcpPort(): Int {
        val session = registerCloseable(FakeAdbSession())
        val freePort = session.channelFactory.createServerSocket().use {
            it.bind(InetSocketAddress(0)).port
        }
        return freePort
    }

    private fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }

    private fun <T> assertTcpServerException(
        throwable: Throwable?,
        cls: Class<T>,
        @Suppress("SameParameterValue") message: String
    ) where T : Throwable {
        assertNotNull(throwable)
        //assertEquals(message, throwable?.message)
        //assertEquals(cls, throwable?.javaClass)
        throwable?.also {
            it.suppressed.firstOrNull { suppressed ->
                suppressed.javaClass == cls && suppressed.message == message
            } ?: run {
                fail("Exception of type '$cls' with message '$message' not found in list of suppressed of $throwable")
            }
        }
    }

    private fun createMultipleServers(serverPort: Int): Sequence<ServerInfo> {
        return generateSequence {
            createFailoverServer(serverPort, serverFactory = {session -> MyTestTcpServer(session) } )
        }
    }

    private fun createFailoverServer(
        serverPort: Int,
        serverFactory: (AdbSession) -> MyTestTcpServer = { session -> MyTestTcpServer(session) }
    ): ServerInfo {
        val session = registerCloseable(FakeAdbSession())
        val tcpServer = serverFactory(session)
        val server = registerCloseable(TcpServerConnection.createWithFailoverConnection(
            session,
            tcpServer,
            serverPort,
            Duration.ofSeconds(1),
            RetryPolicy.fixedDelay(Duration.ofMillis(100)),
        ))

        return ServerInfo(session, tcpServer, server)
    }


    private data class ServerInfo(
        val session: AdbSession,
        val tcpServer: MyTestTcpServer,
        val server: TcpServerConnection
    )

    private open class MyTestTcpServer(session: AdbSession) : TcpServer {
        val scope = session.scope.createChildScope(isSupervisor = true)
        var launchedCallCount = AtomicInteger()
        var closed = AtomicBoolean(false)
        var requestCount = AtomicInteger()

        override fun launch(serverSocket: AdbServerSocket): Job {
            launchedCallCount.incrementAndGet()
            val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                println("Exception in '${this::class.java.simpleName}' server coroutine: $throwable")
            }
            return scope.launch(exceptionHandler) {
                runServer(serverSocket)
            }
        }

        open suspend fun runServer(serverSocket: AdbServerSocket) {
            while (true) {
                currentCoroutineContext().ensureActive()
                serverSocket.accept().use { socketChannel ->
                    runOneServerRequest(socketChannel)
                }
            }
        }

        open suspend fun runOneServerRequest(socketChannel: AdbChannel) {
            requestCount.incrementAndGet()
            val socket = wrapClientSocket(socketChannel)
            val request = socket.readString()
            socket.writeString(request)
        }

        override fun close() {
            closed.set(true)
            scope.cancel("${this::class.java.simpleName} has been closed")
        }

        class ClientSocket(socketChannel: AdbChannel) : AdbChannel by socketChannel {
            suspend fun writeHello() {
                writeString("Hello")
            }

            suspend fun writeString(value: String) {
                val buffer = ResizableBuffer()
                val bytes = value.toByteArray(Charsets.UTF_8)
                buffer.appendInt(bytes.size)
                buffer.appendBytes(bytes)
                writeExactly(buffer.forChannelWrite())
            }

            suspend fun readString(): String {
                val buffer = ResizableBuffer()

                buffer.clear()
                readExactly(buffer.forChannelRead(4))
                val size = buffer.afterChannelRead().getInt()
                buffer.clear()

                readExactly(buffer.forChannelRead(size))
                return Charsets.UTF_8.decode(buffer.afterChannelRead()).toString()
            }
        }

        companion object {
            fun wrapClientSocket(socketChannel: AdbChannel): ClientSocket {
                return ClientSocket(socketChannel)
            }
        }
    }
}
