/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.adblib.AdbOutputChannel
import com.android.adblib.AdbServerSocket
import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.utils.createChildScope
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Test

class ForwardingDaemonTest {

  // A series of commands that come from the remote server that are handled by ForwardingDaemon
  // The list is iterated and each command is written to the channel which is read by the
  // ForwardingDaemon.
  private val inputList =
    listOf(
      createByteBuffer(CNXN),
      createByteBuffer(OPEN, 1),
      createByteBuffer(WRTE, 1, 1, 4, "test"),
      createByteBuffer(CLSE)
    )

  private val fakeAdbSession = FakeAdbSession()
  private val fakeStreamOpener =
    object : StreamOpener {
      private val myStream =
        object : Stream {
          override fun sendWrite(command: WriteCommand) {
            assertThat(String(command.payload)).isEqualTo("test")
          }

          override fun sendClose() = Unit
          override suspend fun receiveCommand(command: StreamCommand) = Unit
        }
      override fun connect(forwardingDaemon: ForwardingDaemon) {
        fakeAdbSession.scope.launch {
          forwardingDaemon.onStateChanged(DeviceState.MISSING)
          forwardingDaemon.onStateChanged(DeviceState.DEVICE)
        }
      }

      override fun open(service: String, streamId: Int, adbOutputChannel: AdbOutputChannel) =
        myStream
    }
  private lateinit var testSocket: AdbServerSocket
  private lateinit var forwardingDaemon: ForwardingDaemonImpl
  private val payloadAssertException = AtomicReference<Throwable>()
  private val exceptionHandler = CoroutineExceptionHandler { _, t -> payloadAssertException.set(t) }

  @Before
  fun setUp(): Unit = runBlockingWithTimeout {
    testSocket = fakeAdbSession.channelFactory.createServerSocket()
  }

  @After
  fun tearDown() {
    forwardingDaemon.close()
    fakeAdbSession.close()
  }

  @Test
  fun testForwardingDaemon() = runBlockingWithTimeout {
    val childScope = fakeAdbSession.scope.createChildScope(context = exceptionHandler)
    forwardingDaemon =
      ForwardingDaemonImpl(fakeStreamOpener, childScope, fakeAdbSession) { testSocket }
    assertThat(forwardingDaemon.devicePort).isEqualTo(-1)
    forwardingDaemon.start()
    assertThat(forwardingDaemon.devicePort).isEqualTo(testSocket.localAddress()?.port)
    fakeAdbSession.channelFactory.connectSocket(testSocket.localAddress()!!).use { channel ->
      inputList.forEach { channel.writeExactly(it) }
      // CNXN response
      channel.assertCommand(CNXN)
      // OPEN response
      channel.assertCommand(OKAY, 1, 1)
      // WRTE response
      channel.assertCommand(OKAY, 1, 1)
    }
    assertThat(payloadAssertException.get()).isEqualTo(null)
  }

  @Test
  fun testLatency() = runBlockingWithTimeout {
    val childScope = fakeAdbSession.scope.createChildScope(context = exceptionHandler)
    forwardingDaemon =
      ForwardingDaemonImpl(fakeStreamOpener, childScope, fakeAdbSession) { testSocket }
    assertThat(forwardingDaemon.devicePort).isEqualTo(-1)
    forwardingDaemon.start()
    fakeAdbSession.deviceServices.configureShellV2Command(
      DeviceSelector.fromSerialNumber("localhost:${testSocket.localAddress()!!.port}"),
      "cat",
      "Foo"
    )
    // Check if roundTripLatencyMsFlow emits a value.
    assertThat(forwardingDaemon.roundTripLatencyMsFlow.first()).isNotNull()
  }

  @Test
  fun testOnStateChangeCalledWithCancelledScope() = runBlockingWithTimeout {
    val childScope = fakeAdbSession.scope.createChildScope(context = exceptionHandler)
    forwardingDaemon =
      ForwardingDaemonImpl(fakeStreamOpener, childScope, fakeAdbSession) { testSocket }
    forwardingDaemon.start()
    assertThat(forwardingDaemon.devicePort).isEqualTo(testSocket.localAddress()?.port)

    childScope.cancel()
    // Implicit assert - Test will fail with JobCancellationException if this call fails.
    forwardingDaemon.close()
  }
}
