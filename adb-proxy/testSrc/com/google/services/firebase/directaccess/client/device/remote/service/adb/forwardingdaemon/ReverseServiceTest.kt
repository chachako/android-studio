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
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class ReverseServiceTest {
  private val fakeAdbSession = FakeAdbSession()
  private val mockReverseForwardStream: ReverseForwardStream = mock()
  private lateinit var testSocket: AdbServerSocket
  private lateinit var adbChannel: AdbChannel
  private lateinit var reverseService: ReverseService
  private lateinit var countDownLatch: CountDownLatch

  @Before
  fun setUp() = runBlockingWithTimeout {
    testSocket =
      fakeAdbSession.channelFactory.createServerSocket().also { serverSocket ->
        serverSocket.bind()
      }
    adbChannel = fakeAdbSession.channelFactory.connectSocket(testSocket.localAddress()!!)
    val serialNumber = "localhost:${testSocket.port}"
    reverseService =
      ReverseService(
        serialNumber,
        fakeAdbSession.scope,
        ResponseWriter(adbChannel),
        fakeAdbSession
      ) { _, _, _ ->
        mockReverseForwardStream
      }
    doAnswer { countDownLatch.countDown() }.whenever(mockReverseForwardStream).run()
  }

  @After
  fun tearDown() {
    adbChannel.close()
    testSocket.close()
    fakeAdbSession.close()
  }

  @Test
  fun testHandleForward() = runBlockingWithTimeout {
    countDownLatch = CountDownLatch(1)
    reverseService.handleReverse(getReverseCommand(testSocket), 0)
    // Wait until run is called. This also implies that reverseForwardStream.run() was called
    countDownLatch.await()

    reverseService.handleReverse("reverse:killforward:command-${testSocket.port}", 0)
    verify(mockReverseForwardStream).kill()

    testSocket.accept().use { channel ->
      channel.assertCommand(OKAY)
      channel.assertCommand(WRTE, payload = "OKAY")
      channel.assertCommand(CLSE)
    }
  }

  @Test
  fun testKillAll() = runBlockingWithTimeout {
    val testSocket2 = fakeAdbSession.channelFactory.createServerSocket().apply { bind() }
    countDownLatch = CountDownLatch(2)
    reverseService.handleReverse(getReverseCommand(testSocket), 0)
    reverseService.handleReverse(getReverseCommand(testSocket2), 1)
    countDownLatch.await()

    reverseService.handleReverse("reverse:killforward-all", 0)
    verify(mockReverseForwardStream, times(2)).kill()

    testSocket.accept().use { channel ->
      channel.assertCommand(OKAY)
      channel.assertCommand(WRTE, payload = "OKAY")
      channel.assertCommand(CLSE)
    }
    testSocket2.close()
  }

  @Test
  fun testListForward() = runBlockingWithTimeout {
    countDownLatch = CountDownLatch(2)
    val testSocket2 = fakeAdbSession.channelFactory.createServerSocket().apply { bind() }
    doReturn(getCommand(testSocket.port), getCommand(testSocket2.port))
      .whenever(mockReverseForwardStream)
      .devicePort
    doReturn("${testSocket.port}", "${testSocket2.port}")
      .whenever(mockReverseForwardStream)
      .localPort
    reverseService.handleReverse(getReverseCommand(testSocket), 0)
    reverseService.handleReverse(getReverseCommand(testSocket2), 1)
    countDownLatch.await()

    reverseService.handleReverse("reverse:list-forward", 0)

    testSocket.accept().use { channel ->
      channel.assertCommand(OKAY)
      val payload =
        "(reverse) ${getCommand(testSocket.port)} ${testSocket.port}\n" +
          "(reverse) ${getCommand(testSocket2.port)} ${testSocket2.port}"
      channel.assertCommand(WRTE, payload = "OKAY${String.format("%04X", payload.length)}$payload")
      channel.assertCommand(CLSE)
    }

    testSocket2.close()
  }
}

private fun getReverseCommand(socket: AdbServerSocket): String {
  val port = socket.port
  return "reverse:forward:${getCommand(port)};tcp:$port"
}

private fun getCommand(port: Int) = "command-$port"

private val AdbServerSocket.port: Int
  get() = runBlocking {
    assert(localAddress() != null)
    return@runBlocking localAddress()!!.port
  }
