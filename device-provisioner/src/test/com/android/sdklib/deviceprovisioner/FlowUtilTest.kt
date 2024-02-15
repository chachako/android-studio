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

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.utils.createChildScope
import com.google.common.truth.Truth.assertThat
import com.jetbrains.rd.util.AtomicInteger
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test

class FlowUtilTest {

  class Device(val name: String = "Device") {
    val state = MutableStateFlow("A")
  }

  @Test
  fun pairWithNestedState() = runBlockingWithTimeout {
    val flow = MutableStateFlow<List<Device>>(emptyList())
    val pairedFlow = flow.pairWithNestedState { it.state }
    val channel = Channel<List<Pair<Device, String>>>()
    val job = launch { pairedFlow.collect { channel.send(it) } }

    channel.expectDeviceInState()

    val device1 = Device()
    device1.state.value = "B"
    flow.value = listOf(device1)

    channel.expectDeviceInState("B")

    device1.state.value = "C"

    channel.expectDeviceInState("C")

    flow.value = emptyList()
    channel.expectDeviceInState()

    val device2 = Device()
    device2.state.value = "E"
    flow.value = listOf(device1, device2)

    channel.expectDeviceInState("C", "E")

    job.cancel()
  }

  @Test
  fun mapNestedStateNotNull() = runBlockingWithTimeout {
    val flow = MutableStateFlow<List<Device>>(emptyList())
    val pairedFlow =
      flow.mapNestedStateNotNull({ it.state }, { _, state -> state.takeIf { state.length > 1 } })
    val channel = Channel<List<String>>()
    val job = launch { pairedFlow.collect { channel.send(it) } }

    val device1 = Device()
    val device2 = Device()

    flow.value = listOf(device1, device2)

    assertThat(channel.receive()).isEmpty()

    device1.state.value = "A1"

    assertThat(channel.receive()).containsExactly("A1")

    device2.state.value = "A2"

    assertThat(channel.receive()).containsExactly("A1", "A2")

    flow.value = listOf(device2)

    assertThat(channel.receive()).containsExactly("A2")

    device2.state.value = ""

    assertThat(channel.receive()).isEmpty()

    job.cancel()
  }

  @Test
  fun mapChangedState(): Unit = runTest {
    val childScope = createChildScope()
    val flow = MutableStateFlow<List<Device>>(emptyList())
    val invocations = AtomicInteger()
    val results =
      flow
        .pairWithNestedState { it.state }
        .mapChangedState { device, state ->
          invocations.incrementAndGet()
          "${device.name} $state"
        }
        .stateIn(childScope)

    val device1 = Device("1")
    val device2 = Device("2")
    flow.value = listOf(device1.apply { state.value = "A" })

    advanceUntilIdle()
    assertThat(results.value).containsExactly("1 A")
    assertThat(invocations.get()).isEqualTo(1)

    flow.value = listOf(device1.apply { state.value = "B" }, device2.apply { state.value = "C" })

    advanceUntilIdle()
    assertThat(results.value).containsExactly("1 B", "2 C")
    assertThat(invocations.get()).isEqualTo(3)

    flow.value = listOf(device1.apply { state.value = "B" }, device2.apply { state.value = "D" })

    advanceUntilIdle()
    assertThat(results.value).containsExactly("1 B", "2 D")
    assertThat(invocations.get()).isEqualTo(4)

    flow.value = listOf(device2.apply { state.value = "D" })

    advanceUntilIdle()
    assertThat(results.value).containsExactly("2 D")
    assertThat(invocations.get()).isEqualTo(4)

    flow.value = emptyList()

    advanceUntilIdle()
    assertThat(results.value).isEmpty()
    assertThat(invocations.get()).isEqualTo(4)

    childScope.cancel()
  }

  suspend fun Channel<List<Pair<Device, String>>>.expectDeviceInState(vararg state: String) {
    withTimeout(5000) {
      receiveUntilPassing { pairs ->
        val states = pairs.map { it.second }
        assertThat(states).containsExactly(*state)
      }
    }
  }
}
