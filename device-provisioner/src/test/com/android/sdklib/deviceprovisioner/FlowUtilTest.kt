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
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Test

class FlowUtilTest {

  class Device {
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

  suspend fun Channel<List<Pair<Device, String>>>.expectDeviceInState(vararg state: String) {
    withTimeout(5000) {
      receiveUntilPassing { pairs ->
        val states = pairs.map { it.second }
        assertThat(states).containsExactly(*state)
      }
    }
  }
}
