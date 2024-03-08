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

import android.os.Build
import androidx.inspection.ArtTooling
import androidx.inspection.InspectorEnvironment
import androidx.inspection.InspectorExecutors
import com.android.tools.appinspection.network.testing.FakeConnection
import com.android.tools.appinspection.network.testing.FakeEnvironment
import com.android.tools.appinspection.network.testing.FakeTrafficStatsProvider.Stat
import com.android.tools.appinspection.network.testing.NetworkInspectorRule
import com.android.tools.appinspection.network.testing.getLogLines
import com.android.tools.appinspection.network.testing.getVisibleLogLines
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import studio.network.inspection.NetworkInspectorProtocol.SpeedEvent

@RunWith(RobolectricTestRunner::class)
@Config(
  manifest = Config.NONE,
  minSdk = Build.VERSION_CODES.O,
  maxSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
)
internal class NetworkInspectorTest {

  @get:Rule val inspectorRule = NetworkInspectorRule(autoStart = false)

  private val trafficStatsProvider
    get() = inspectorRule.trafficStatsProvider

  @Test
  fun speedDataCollection() = runBlocking {
    trafficStatsProvider.setData(Stat(0, 0), Stat(10, 10), Stat(20, 10), Stat(20, 20), Stat(20, 30))
    inspectorRule.start()
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
      Stat(0, 0),
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
    val response = inspectorRule.start()
    delay(1000)

    assertThat(response.startInspectionResponse.speedCollectionStarted).isTrue()
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
  fun registerHooks_logs() {
    val networkInspector = NetworkInspector(FakeConnection(), FakeEnvironment())

    val (javaNet, okhttp, grpc) = networkInspector.registerHooks()

    // Note that `AndroidChannelBuilder` is not hooked. This is because we didn't add a build
    // dependency on `grpc-android`. The test still verifies that we attempt to hook it.
    assertThat(getLogLines())
      .containsExactly(
        "DEBUG: studio.inspectors: Instrumented java.net.URL",
        "DEBUG: studio.inspectors: Instrumented com.squareup.okhttp.OkHttpClient",
        "DEBUG: studio.inspectors: Instrumented okhttp3.OkHttpClient",
        "DEBUG: studio.inspectors: Instrumented io.grpc.ManagedChannelBuilder",
        "DEBUG: studio.inspectors: Could not load class io.grpc.android.AndroidChannelBuilder",
      )
    assertThat(javaNet).isTrue()
    assertThat(okhttp).isTrue()
    assertThat(grpc).isTrue()
  }

  @Test
  fun registerHooks_failToAddOkHttp2And3Hooks_doesNotThrowException() {
    val environment = TestInspectorEnvironment("OkHttpClient")
    val networkInspector = NetworkInspector(FakeConnection(), environment)

    val (javaNet, okhttp, grpc) = networkInspector.registerHooks()

    assertThat(getVisibleLogLines())
      .containsExactly(
        "DEBUG: Network Inspector: Did not instrument OkHttpClient. App does not use OKHttp or class is omitted by app reduce"
      )
    assertThat(javaNet).isTrue()
    assertThat(okhttp).isFalse()
    assertThat(grpc).isTrue()
  }

  @Test
  fun registerHooks_failToAddOkHttp2_doesNotThrowExceptionDoesNotLogFailure() {
    val environment = TestInspectorEnvironment("com.squareup.okhttp.OkHttpClient")
    val networkInspector = NetworkInspector(FakeConnection(), environment)

    val (javaNet, okhttp, grpc) = networkInspector.registerHooks()

    assertThat(getVisibleLogLines()).isEmpty()
    assertThat(javaNet).isTrue()
    assertThat(okhttp).isTrue()
    assertThat(grpc).isTrue()
  }

  @Test
  fun registerHooks_failToAddOkHttp3_doesNotThrowExceptionDoesNotLogFailure() {
    val environment = TestInspectorEnvironment("okhttp3.OkHttpClient")
    val networkInspector = NetworkInspector(FakeConnection(), environment)

    val (javaNet, okhttp, grpc) = networkInspector.registerHooks()

    assertThat(getVisibleLogLines()).isEmpty()
    assertThat(javaNet).isTrue()
    assertThat(okhttp).isTrue()
    assertThat(grpc).isTrue()
  }

  @Test
  fun failToAddGrpcHooks_doesNotThrowException() {
    val environment = TestInspectorEnvironment("ManagedChannelBuilder")
    val networkInspector = NetworkInspector(FakeConnection(), environment)

    val (javaNet, okhttp, grpc) = networkInspector.registerHooks()

    assertThat(getVisibleLogLines())
      .containsExactly(
        "DEBUG: Network Inspector: Did not instrument 'ManagedChannelBuilder'. App does not use gRPC or class is omitted by app reduce"
      )
    assertThat(javaNet).isTrue()
    assertThat(okhttp).isTrue()
    assertThat(grpc).isFalse()
  }

  private inner class TestInspectorEnvironment(private val rejectClassName: String) :
    InspectorEnvironment {

    override fun artTooling(): ArtTooling {
      return object : ArtTooling by inspectorRule.environment.artTooling() {
        override fun <T : Any?> findInstances(clazz: Class<T>): List<T> {
          return when {
            clazz.name.endsWith(rejectClassName) -> emptyList()
            else -> inspectorRule.environment.artTooling().findInstances(clazz)
          }
        }

        override fun <T : Any?> registerExitHook(
          originClass: Class<*>,
          originMethod: String,
          exitHook: ArtTooling.ExitHook<T>,
        ) {
          if (originClass.name.endsWith(rejectClassName)) {
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
}

private fun speedEvent(rxSpeed: Long, txSpeed: Long) =
  SpeedEvent.newBuilder().setRxSpeed(rxSpeed).setTxSpeed(txSpeed).build().toDebugString()

// Proto.toString is hard to read because fields with default values are elided
private fun SpeedEvent.toDebugString() = "rx=$rxSpeed tx=$txSpeed"
