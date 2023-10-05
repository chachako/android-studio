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
package com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon

import com.android.adblib.AdbChannel
import com.android.adblib.AdbServerSocket
import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.utils.createChildScope
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.MessageType
import com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.StreamDataHeader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn

const val SOCKET_COUNT = 8
const val DEFAULT_DISPATCHER_PARALLELISM = 5

class ReverseForwardStreamTest {
  private lateinit var fakeAdbSession: FakeAdbSession
  private lateinit var testSocket: AdbServerSocket
  private lateinit var adbChannel: AdbChannel
  private lateinit var responseWriter: ResponseWriter
  private lateinit var reverseForwardStream: ReverseForwardStream
  private lateinit var scope: CoroutineScope
  private var port = -1

  private lateinit var sockets: MutableStateFlow<List<Socket>>
  /** A [CountDownLatch] to ensure all [SOCKET_COUNT] sockets are reading data. */
  private lateinit var countDownLatch: CountDownLatch

  private inner class TestByteArrayInputStream : InputStream() {
    override fun read(): Int {
      throw RuntimeException("Not yet implemented")
    }

    override fun read(b: ByteArray): Int {
      countDownLatch.countDown()
      // Wait until all sockets are dispatched.
      countDownLatch.await()
      return b.size
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Before
  fun setUp() = runBlockingWithTimeout {
    fakeAdbSession = FakeAdbSession()
    scope =
      fakeAdbSession.scope.createChildScope(
        true,
        Dispatchers.Default.limitedParallelism(DEFAULT_DISPATCHER_PARALLELISM)
      )
    val socket = fakeAdbSession.channelFactory.createServerSocket()

    val openMessageList =
      (1..SOCKET_COUNT).map { StreamDataHeader(MessageType.OPEN, it, 0).toByteArray() }
    val dataMessageList =
      (1..SOCKET_COUNT).map { StreamDataHeader(MessageType.DATA, it, 0).toByteArray() }
    val closeMessageList =
      (1..SOCKET_COUNT).map { StreamDataHeader(MessageType.CLSE, it, 0).toByteArray() }
    val messageList =
      listOf(StreamDataHeader(MessageType.REDY, 0, 0).toByteArray()) +
        openMessageList +
        dataMessageList +
        closeMessageList
    val inputData =
      ByteBuffer.allocate(messageList.sumOf { it.size }).apply {
        messageList.forEach { put(it) }
        flip()
      }

    testSocket =
      object : AdbServerSocket by socket {
        override suspend fun bind(local: InetSocketAddress?, backLog: Int): InetSocketAddress {
          val address = socket.bind(local, backLog)
          // Setup commands with socket bind.
          if (port != address.port) {
            port = address.port
            fakeAdbSession.deviceServices.configureShellV2Command(
              DeviceSelector.fromSerialNumber("localhost:$port"),
              "CLASSPATH=/data/local/tmp/reverse_daemon.dex app_process " +
                "/data/local/tmp/ " +
                "com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.daemon." +
                "ReverseDaemon $port",
              inputData,
              ByteBuffer.allocate(0)
            )
          }
          return address
        }
      }

    testSocket.bind()
    adbChannel = fakeAdbSession.channelFactory.connectSocket(testSocket.localAddress()!!)
    responseWriter = ResponseWriter(adbChannel)

    sockets = MutableStateFlow(listOf())
    countDownLatch = CountDownLatch(SOCKET_COUNT)
    reverseForwardStream =
      ReverseForwardStream(
        port.toString(),
        "tcp:12345",
        1,
        "localhost:$port",
        fakeAdbSession,
        responseWriter,
        scope,
        false,
      ) { _, _ ->
        createSocket()
      }
  }

  private fun createSocket(): Socket =
    mock<Socket>().also { socket ->
      var isClosed = false
      doReturn(TestByteArrayInputStream()).whenever(socket).getInputStream()
      doReturn(ByteArrayOutputStream()).whenever(socket).getOutputStream()
      doAnswer { isClosed }.whenever(socket).isClosed
      doAnswer {
          isClosed = true
          true
        }
        .whenever(socket)
        .close()
      sockets.update { sockets -> sockets + socket }
    }

  @After
  fun tearDown() {
    adbChannel.close()
    testSocket.close()
    fakeAdbSession.close()
  }

  /**
   * [Dispatchers.Default] has limited parallelism support and could freeze with too many sockets
   * created. This test simulate the situation by limiting the dispatcher's parallelism to
   * [DEFAULT_DISPATCHER_PARALLELISM] and creates more sockets to overwhelm it.
   */
  @Test
  fun testParallelism() = runBlockingWithTimeout {
    reverseForwardStream.run()
    testSocket.accept().use { channel -> channel.assertCommand(OKAY) }
    // Wait until all sockets become available.
    yieldUntil { sockets.value.size == SOCKET_COUNT }
    // Wait until all sockets start to read data.
    countDownLatch.await()
    // Wait until all sockets get closed.
    yieldUntil { sockets.value.all { it.isClosed } }
  }
}
