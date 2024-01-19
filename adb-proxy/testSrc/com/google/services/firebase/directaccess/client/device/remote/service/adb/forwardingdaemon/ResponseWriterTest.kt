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
import org.junit.After
import org.junit.Before
import org.junit.Test

class ResponseWriterTest {

  private val fakeAdbSession = FakeAdbSession()
  private lateinit var testSocket: AdbServerSocket
  private lateinit var adbChannel: AdbChannel
  private lateinit var responseWriter: ResponseWriter

  @Before
  fun setUp() = runBlockingWithTimeout {
    testSocket =
      fakeAdbSession.channelFactory.createServerSocket().also { serverSocket ->
        serverSocket.bind()
      }
    adbChannel = fakeAdbSession.channelFactory.connectSocket(testSocket.localAddress()!!)
    responseWriter = ResponseWriter(adbChannel, true)
  }

  @After
  fun tearDown() = runBlockingWithTimeout {
    adbChannel.close()
    testSocket.close()
  }

  @Test
  fun testWriteEmptyResponse() = runBlockingWithTimeout {
    val data = "DATA PAYLOAD"
    responseWriter.writeStringResponse(0, data)
    testSocket.accept().use { channel ->
      channel.assertCommand(OKAY)
      channel.assertCommand(WRTE, payload = "${data.hexLength}$data")
      channel.assertCommand(CLSE, 0)
    }
  }

  @Test
  fun testWriteOkayResponseWithoutPayload() = runBlockingWithTimeout {
    responseWriter.writeOkayResponse(0)
    testSocket.accept().use { channel ->
      channel.assertCommand(OKAY)
      channel.assertCommand(WRTE, payload = "OKAY")
      channel.assertCommand(CLSE, 0)
    }
  }

  @Test
  fun testWriteOkayResponseWithPayload() = runBlockingWithTimeout {
    val okayPayload = "OKAY PAYLOAD"
    responseWriter.writeOkayResponse(0, okayPayload)
    testSocket.accept().use { channel ->
      channel.assertCommand(OKAY)
      channel.assertCommand(
        WRTE,
        0,
        0,
        20,
        1215945526,
        payload = "OKAY${okayPayload.hexLength}$okayPayload",
      )
      channel.assertCommand(CLSE, 0)
    }
  }

  @Test
  fun testWriteFailResponseWithPayload() = runBlockingWithTimeout {
    val failedPayload = "FAIL PAYLOAD"
    responseWriter.writeFailResponse(0, failedPayload)
    testSocket.accept().use { channel ->
      channel.assertCommand(OKAY)
      channel.assertCommand(WRTE, payload = "FAIL${failedPayload.hexLength}$failedPayload")
      channel.assertCommand(CLSE, 0)
    }
  }

  @Test
  fun testWriteResponseWithoutCRC() = runBlockingWithTimeout {
    responseWriter = ResponseWriter(adbChannel, false)
    val okayPayload = "OKAY PAYLOAD"
    responseWriter.writeOkayResponse(0, okayPayload)
    testSocket.accept().use { channel ->
      channel.assertCommand(OKAY)
      channel.assertCommand(WRTE, 0, 0, 20, 0, payload = "OKAY${okayPayload.hexLength}$okayPayload")
      channel.assertCommand(CLSE, 0)
    }
  }
}
