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
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.android.processmonitor.monitor.ddmlib

import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.ddmlib.IDevice.CHANGE_STATE
import com.android.ddmlib.IDevice.DeviceState.DISCONNECTED
import com.android.ddmlib.IDevice.DeviceState.OFFLINE
import com.android.ddmlib.IDevice.DeviceState.ONLINE
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import java.io.Closeable

/**
 * Tests for [ProcessNameMonitorFlowsImpl]
 */
@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalCoroutinesApi::class) // runTest is experimental (replaced runTestTest)
class ProcessNameMonitorFlowsImplTest {
  private val adbAdapter = FakeAdbAdapter()

  private val processNameMonitorFlows = ProcessNameMonitorFlowsImpl(adbAdapter, FakeAdbLoggerFactory().logger)

  private val eventChannel = Channel<String>(1)

  @Test
  fun trackDevices_noInitialDevices(): Unit = runTest {
    collectFlowToChannel(processNameMonitorFlows.trackDevices(), eventChannel).use {
      advanceUntilIdle()

      adbAdapter.fireDeviceConnected(mockDevice("device1", ONLINE))
      assertThat(eventChannel.receive()).isEqualTo("Online(device=device1)")

      adbAdapter.fireDeviceDisconnected(mockDevice("device1", DISCONNECTED))
      assertThat(eventChannel.receive()).isEqualTo("Disconnected(device=device1)")

      adbAdapter.fireDeviceConnected(mockDevice("device1", OFFLINE))
      assertThat(withTimeoutOrNull(1000) { eventChannel.receive() }).named("Expected to timeout").isEqualTo(null)

      adbAdapter.fireDeviceChange(mockDevice("device1", ONLINE), CHANGE_STATE)
      assertThat(eventChannel.receive()).isEqualTo("Online(device=device1)")
    }
  }

  @Test
  fun trackDevices_noInitialDevices1(): Unit = runTest {
    val job = async { processNameMonitorFlows.trackDevices().take(3).toList() }
    advanceUntilIdle()

    adbAdapter.fireDeviceConnected(mockDevice("device1", ONLINE))
    adbAdapter.fireDeviceDisconnected(mockDevice("device1", ONLINE))
    adbAdapter.fireDeviceConnected(mockDevice("device1", OFFLINE))
    adbAdapter.fireDeviceChange(mockDevice("device1", ONLINE), CHANGE_STATE)

    assertThat(job.await().map { it.toString() }).containsExactly(
      "Online(device=device1)",
      "Disconnected(device=device1)",
      "Online(device=device1)",
    ).inOrder()
  }

  @Test
  fun trackDevices_withInitialDevices(): Unit = runTest {
    adbAdapter.devices = listOf(
      mockDevice("device1", OFFLINE),
      mockDevice("device2", ONLINE),
    )

    collectFlowToChannel(processNameMonitorFlows.trackDevices(), eventChannel).use {
      assertThat(eventChannel.receive()).isEqualTo("Online(device=device2)")
    }
  }

  @Test
  fun trackDevices_initialOfflineDevice_becomesOnline(): Unit = runTest {
    adbAdapter.devices = listOf(
      mockDevice("device1", OFFLINE),
      mockDevice("device2", ONLINE),
    )

    collectFlowToChannel(processNameMonitorFlows.trackDevices(), eventChannel).use {
      assertThat(eventChannel.receive()).isEqualTo("Online(device=device2)")

      adbAdapter.fireDeviceConnected(mockDevice("device1", ONLINE))
      assertThat(eventChannel.receive()).isEqualTo("Online(device=device1)")
    }
  }

  @Test
  fun trackDevices_jobCanceled_unregisters(): Unit = runTest {
    val job = launch { processNameMonitorFlows.trackDevices().collect { } }
    advanceUntilIdle()
    assertThat(adbAdapter.deviceChangeListeners).isNotEmpty()

    job.cancel()

    advanceUntilIdle()
    assertThat(adbAdapter.deviceChangeListeners).isEmpty()
  }

  /**
   * Collect a flow and sent the results into a channel (converting to String for easy debugging). Return the Job wrapped by a Closeable,
   * so we don't forget to cancel it.
   */
  private fun <T> CoroutineScope.collectFlowToChannel(flow: Flow<T>, channel: Channel<String>): Closeable {
    return object : Closeable {
      private val job = launch { flow.collect { channel.send(it.toString()) } }
      override fun close() {
        job.cancel()
      }
    }
  }
}
