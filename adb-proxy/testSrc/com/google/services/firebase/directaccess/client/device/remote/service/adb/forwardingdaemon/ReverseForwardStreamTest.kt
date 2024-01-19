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
import com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.MessageType
import com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.StreamDataHeader
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Before
import org.junit.Test

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

  private lateinit var sockets: MutableStateFlow<List<AdbChannel>>
  /** A [CountDownLatch] to ensure all [SOCKET_COUNT] sockets are reading data. */
  private lateinit var countDownLatch: CountDownLatch
  private lateinit var readComplete: CompletableDeferred<Boolean>

  @OptIn(ExperimentalCoroutinesApi::class)
  @Before
  fun setUp() = runBlockingWithTimeout {
    fakeAdbSession = FakeAdbSession()
    scope =
      fakeAdbSession.scope.createChildScope(
        true,
        Dispatchers.Default.limitedParallelism(DEFAULT_DISPATCHER_PARALLELISM),
      )
    val socket = fakeAdbSession.channelFactory.createServerSocket()

    val openMessageList =
      (1..SOCKET_COUNT).map { StreamDataHeader(MessageType.OPEN, it, 0).toByteBuffer() }
    val dataMessageList =
      (1..SOCKET_COUNT).map { StreamDataHeader(MessageType.DATA, it, 0).toByteBuffer() }
    val closeMessageList =
      (1..SOCKET_COUNT).map { StreamDataHeader(MessageType.CLSE, it, 0).toByteBuffer() }
    val messageList =
      listOf(StreamDataHeader(MessageType.REDY, 0, 0).toByteBuffer()) +
        openMessageList +
        dataMessageList +
        closeMessageList
    val inputData =
      ByteBuffer.allocate(messageList.sumOf { it.remaining() }).apply {
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
              ByteBuffer.allocate(0),
            )
          }
          return address
        }
      }

    testSocket.bind()
    adbChannel = fakeAdbSession.channelFactory.connectSocket(testSocket.localAddress()!!)
    responseWriter = ResponseWriter(adbChannel, true)

    sockets = MutableStateFlow(listOf())
    countDownLatch = CountDownLatch(SOCKET_COUNT)
    readComplete = CompletableDeferred()

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
      ) { _ ->
        (object : AdbChannel {
            override suspend fun shutdownInput() = Unit

            override suspend fun shutdownOutput() = Unit

            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
              readComplete.await()
              countDownLatch.countDown()
            }

            override fun close() = Unit

            override suspend fun writeBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
              buffer.position(buffer.limit())
            }
          })
          .also { newSocketChannel -> sockets.update { it + newSocketChannel } }
      }
  }

  @After
  fun tearDown() {
    adbChannel.close()
    testSocket.close()
    fakeAdbSession.close()
  }

  /**
   * [Dispatchers.Default] has limited parallelism support and could freeze with too many blocking
   * running jobs. This test simulate the situation by limiting the dispatcher's parallelism to
   * [DEFAULT_DISPATCHER_PARALLELISM] and creates more sockets to overwhelm it. Ideally, all running
   * jobs reading from sockets should be dispatched properly and do not block.
   */
  @Test
  fun testParallelism() = runBlockingWithTimeout {
    reverseForwardStream.run()
    testSocket.accept().use { channel -> channel.assertCommand(OKAY) }
    // Wait until all sockets become available.
    yieldUntil { sockets.value.size == SOCKET_COUNT }
    readComplete.complete(true)
    // Wait until all sockets start to read data.
    countDownLatch.await()
  }
}
