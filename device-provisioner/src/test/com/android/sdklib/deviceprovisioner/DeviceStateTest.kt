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
package com.android.sdklib.deviceprovisioner

import com.android.adblib.ConnectedDevice
import com.android.sdklib.deviceprovisioner.DeviceState.Connected
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.time.delay
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito.mock

class DeviceStateTest {

  val connectedDevice = mock(ConnectedDevice::class.java)

  val properties =
    DeviceProperties.buildForTest {
      connectionType = ConnectionType.USB
      icon = EmptyIcon.DEFAULT
    }

  class Activating(override val properties: DeviceProperties) : Disconnected(properties)

  @Test
  fun advanceStateWithTimeoutAdvances() = runTest {
    val state = MutableStateFlow<DeviceState>(Disconnected(properties))
    val result = async {
      state.advanceStateWithTimeout(
        timeout = Duration.ofMinutes(5),
        updateState = { Activating(properties) },
        advanceAction = {
          delay(Duration.ofMinutes(2))
          state.value =
            Connected(
              properties,
              isReady = true,
              isTransitioning = false,
              status = "Connected",
              connectedDevice = connectedDevice
            )
        }
      )
    }

    advanceTimeBy(Duration.ofMinutes(1).toMillis())
    assertThat(state.value).isInstanceOf(Activating::class.java)

    advanceTimeBy(Duration.ofMinutes(2).toMillis())
    assertThat(state.value).isInstanceOf(Connected::class.java)

    advanceTimeBy(Duration.ofMinutes(3).toMillis())
    assertThat(result.await()).isTrue()
  }

  @Test
  fun advanceStateWithTimeoutAborts() = runTest {
    val originalState = Disconnected(properties)
    val state = MutableStateFlow<DeviceState>(originalState)
    val minuteCounter = AtomicInteger()
    val result = async {
      state.advanceStateWithTimeout(
        timeout = Duration.ofSeconds(301),
        updateState = { Activating(properties) },
        advanceAction = {
          // Don't loop forever, a test failure will not terminate this job
          repeat(10) {
            delay(Duration.ofMinutes(1))
            minuteCounter.incrementAndGet()
          }
        }
      )
    }

    advanceTimeBy(Duration.ofMinutes(1).toMillis())
    assertThat(state.value).isInstanceOf(Activating::class.java)

    advanceTimeBy(Duration.ofMinutes(5).toMillis())
    assertThat(state.value).isEqualTo(originalState)

    try {
      result.await()
      fail("Expected TimeoutCancellationException")
    } catch (expected: TimeoutCancellationException) {
      // The job should have been canceled
      advanceTimeBy(Duration.ofMinutes(10).toMillis())
      assertThat(minuteCounter.get()).isEqualTo(5)
    }
  }
}
