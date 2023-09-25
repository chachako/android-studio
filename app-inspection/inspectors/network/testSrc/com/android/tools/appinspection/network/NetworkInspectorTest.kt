/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.appinspection.network

import androidx.inspection.ArtTooling
import androidx.inspection.InspectorEnvironment
import androidx.inspection.InspectorExecutors
import com.android.tools.appinspection.network.FakeTrafficStatsProvider.Stat
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.SpeedEvent

class NetworkInspectorTest {

  @get:Rule val inspectorRule = NetworkInspectorRule()

  private val trafficStatsProvider
    get() = inspectorRule.trafficStatsProvider

  @Test
  fun speedDataCollection() = runBlocking {
    trafficStatsProvider.setData(
      Stat(10, 10),
      Stat(20, 10),
      Stat(20, 20),
      Stat(20, 30),
    )
    delay(1000)

    assertThat(inspectorRule.connection.speedData.map { it.toDebugString() })
      .containsExactly(
        speedEvent(20, 20),
        speedEvent(20, 0),
        speedEvent(0, 20),
        speedEvent(0, 20),
        speedEvent(0, 0),
      )
      .inOrder()
  }

  @Test
  fun speedDataCollection_omitsZeroEvents() = runBlocking {
    trafficStatsProvider.setData(
      Stat(10, 10),
      Stat(10, 10),
      Stat(10, 10),
      Stat(10, 10),
      Stat(20, 20),
      Stat(20, 20),
      Stat(20, 20),
      Stat(20, 20),
      Stat(20, 20),
      Stat(20, 20),
      Stat(30, 30),
      Stat(30, 30),
      Stat(30, 30),
      Stat(30, 30),
      Stat(30, 30),
      Stat(30, 30),
    )
    delay(1000)

    assertThat(inspectorRule.connection.speedData.map { it.toDebugString() })
      .containsExactly(
        speedEvent(20, 20),
        speedEvent(0, 0),
        speedEvent(0, 0),
        speedEvent(20, 20),
        speedEvent(0, 0),
        speedEvent(0, 0),
        speedEvent(20, 20),
        speedEvent(0, 0),
      )
      .inOrder()
  }

  @Test
  fun failToAddOkHttp2And3Hooks_doesNotThrowException() {
    // This test simulates an app that does not depend on OkHttp and the
    // inspector can be initialized without problems.
    val environment =
      object : InspectorEnvironment {
        override fun artTooling(): ArtTooling {
          return object : ArtTooling by inspectorRule.environment.artTooling() {
            override fun <T : Any?> registerExitHook(
              originClass: Class<*>,
              originMethod: String,
              exitHook: ArtTooling.ExitHook<T>
            ) {
              if (originClass.name.endsWith("OkHttpClient")) {
                throw NoClassDefFoundError()
              } else {
                inspectorRule.environment
                  .artTooling()
                  .registerExitHook(originClass, originMethod, exitHook)
              }
            }
          }
        }

        override fun executors(): InspectorExecutors {
          return inspectorRule.environment.executors()
        }
      }

    // This test passes if no exception thrown here.
    NetworkInspector(inspectorRule.connection, environment)
  }
}

private fun speedEvent(
  rxSpeed: Long,
  txSpeed: Long,
) = SpeedEvent.newBuilder().setRxSpeed(rxSpeed).setTxSpeed(txSpeed).build().toDebugString()

// Proto.toString is hard to read because fields with default values are elided
private fun SpeedEvent.toDebugString() = "rx=$rxSpeed tx=$txSpeed"
