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

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.inspection.ArtTooling
import androidx.inspection.Connection
import androidx.inspection.Inspector
import androidx.inspection.InspectorEnvironment
import com.android.tools.appinspection.network.grpc.GrpcInterceptor
import com.android.tools.appinspection.network.httpurl.wrapURLConnection
import com.android.tools.appinspection.network.okhttp.OkHttp2Interceptor
import com.android.tools.appinspection.network.okhttp.OkHttp3Interceptor
import com.android.tools.appinspection.network.rules.InterceptionRuleImpl
import com.android.tools.appinspection.network.rules.InterceptionRuleServiceImpl
import com.android.tools.appinspection.network.utils.Logger
import com.android.tools.appinspection.network.utils.LoggerImpl
import com.squareup.okhttp.Interceptor
import com.squareup.okhttp.OkHttpClient
import io.grpc.ManagedChannelBuilder
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio.network.inspection.NetworkInspectorProtocol

private const val POLL_INTERVAL_MS = 500L
private const val MULTIPLIER_FACTOR = 1000 / POLL_INTERVAL_MS
private val INTERCEPT_COMMAND_RESPONSE =
  NetworkInspectorProtocol.Response.newBuilder()
    .apply { interceptResponse = NetworkInspectorProtocol.InterceptResponse.getDefaultInstance() }
    .build()
    .toByteArray()

private const val GRPC_FOR_ADDRESS_METHOD =
  "forAddress(Ljava/lang/String;I)Lio/grpc/ManagedChannelBuilder;"

private const val GRPC_FOR_TARGET_METHOD =
  "forTarget(Ljava/lang/String;)Lio/grpc/ManagedChannelBuilder;"

internal class NetworkInspector(
  connection: Connection,
  private val environment: InspectorEnvironment,
  private val trafficStatsProvider: TrafficStatsProvider = TrafficStatsProviderImpl(),
  private val speedDataIntervalMs: Long = POLL_INTERVAL_MS,
  private val logger: Logger = LoggerImpl(),
) : Inspector(connection) {

  private val scope =
    CoroutineScope(SupervisorJob() + environment.executors().primary().asCoroutineDispatcher())

  private val trackerService = HttpTrackerFactoryImpl(connection)
  private var isStarted = false

  private var okHttp2Interceptors: MutableList<Interceptor>? = null

  private val interceptionService = InterceptionRuleServiceImpl()

  override fun onReceiveCommand(data: ByteArray, callback: CommandCallback) {
    val command = NetworkInspectorProtocol.Command.parseFrom(data)
    when {
      command.hasStartInspectionCommand() -> {
        callback.reply(
          NetworkInspectorProtocol.Response.newBuilder()
            .setStartInspectionResponse(
              NetworkInspectorProtocol.StartInspectionResponse.newBuilder()
                .setTimestamp(System.nanoTime())
            )
            .build()
            .toByteArray()
        )

        // Studio should only ever send one start command, but it's harmless to
        // reply with a response. We just need to make sure that we don't collect
        // information twice.
        if (!isStarted) {
          startSpeedCollection()
          registerHooks()
          isStarted = true
        }
      }
      command.hasInterceptCommand() -> {
        val interceptCommand = command.interceptCommand
        when {
          interceptCommand.hasInterceptRuleAdded() -> {
            val interceptRuleAdded = interceptCommand.interceptRuleAdded
            val rule = interceptRuleAdded.rule
            interceptionService.addRule(interceptRuleAdded.ruleId, InterceptionRuleImpl(rule))
            callback.reply(INTERCEPT_COMMAND_RESPONSE)
          }
          interceptCommand.hasInterceptRuleUpdated() -> {
            val interceptRuleAdded = interceptCommand.interceptRuleUpdated
            val rule = interceptRuleAdded.rule
            interceptionService.addRule(interceptRuleAdded.ruleId, InterceptionRuleImpl(rule))
            callback.reply(INTERCEPT_COMMAND_RESPONSE)
          }
          interceptCommand.hasInterceptRuleRemoved() -> {
            interceptionService.removeRule(interceptCommand.interceptRuleRemoved.ruleId)
            callback.reply(INTERCEPT_COMMAND_RESPONSE)
          }
          interceptCommand.hasReorderInterceptRules() -> {
            interceptionService.reorderRules(interceptCommand.reorderInterceptRules.ruleIdList)
            callback.reply(INTERCEPT_COMMAND_RESPONSE)
          }
        }
      }
    }
  }

  private fun startSpeedCollection() =
    scope.launch {
      // The app can have multiple Application instances. In that case, we use the first non-null
      // uid, which is most likely from the Application created by Android.
      val uid =
        environment.artTooling().findInstances(Application::class.java).firstNotNullOfOrNull {
          runCatching { it.applicationInfo?.uid }.getOrNull()
        }
      if (uid == null) {
        logger.error(
          "Failed to find application instance. Collection of network speed is not available."
        )
        return@launch
      }
      var prevRxBytes = trafficStatsProvider.getUidRxBytes(uid)
      var prevTxBytes = trafficStatsProvider.getUidTxBytes(uid)
      var prevZero = false

      while (true) {
        delay(speedDataIntervalMs)
        val rxBytes = trafficStatsProvider.getUidRxBytes(uid)
        val txBytes = trafficStatsProvider.getUidTxBytes(uid)
        val rxDelta = rxBytes - prevRxBytes
        val txDelta = txBytes - prevTxBytes
        val zero = (rxDelta == 0L && txDelta == 0L)
        val timestamp = System.nanoTime()

        // There is no value in sending a constant stream of `zero` events. We just need to make
        // sure we send the first and last `zero` event of such a sequence.
        if (zero) {
          if (!prevZero) {
            // If the current event is zero but the previous one was not, we send it
            sendSpeedEvent(timestamp, 0, 0)
          }
        } else {
          // If the current event is not zero and the previous event was `zero`, send the
          // previous `zero` before the current event.
          if (prevZero) {
            sendSpeedEvent(timestamp - TimeUnit.MILLISECONDS.toNanos(POLL_INTERVAL_MS), 0, 0)
          }
          sendSpeedEvent(timestamp, rxDelta * MULTIPLIER_FACTOR, txDelta * MULTIPLIER_FACTOR)
        }
        prevRxBytes = rxBytes
        prevTxBytes = txBytes
        prevZero = zero
      }
    }

  private fun sendSpeedEvent(timestamp: Long, rxSpeed: Long, txSpeed: Long) {
    connection.sendEvent(
      NetworkInspectorProtocol.Event.newBuilder()
        .setSpeedEvent(
          NetworkInspectorProtocol.SpeedEvent.newBuilder().setRxSpeed(rxSpeed).setTxSpeed(txSpeed)
        )
        .setTimestamp(timestamp)
        .build()
        .toByteArray()
    )
  }

  @VisibleForTesting
  internal fun registerHooks() {
    environment
      .artTooling()
      .registerExitHook(
        URL::class.java,
        "openConnection()Ljava/net/URLConnection;",
        ArtTooling.ExitHook<URLConnection> { urlConnection ->
          wrapURLConnection(urlConnection, trackerService, interceptionService)
        }
      )
    logger.debugHidden("Instrumented ${URL::class.qualifiedName}")

    var okHttpInstrumented = false
    try {
      /*
       * Modifies a list of okhttp2 Interceptor in place, adding our own
       * interceptor into it if not already present, and then returning the list again.
       *
       * In okhttp2 (unlike okhttp3), networkInterceptors() returns direct access to an
       * OkHttpClient list of interceptors and uses that as the API for a user to add more.
       *
       * Therefore, we have to modify the list in place, whenever it is first accessed (either
       * by the user to add their own interceptor, or by OkHttp internally to iterate through
       * all interceptors).
       */
      environment
        .artTooling()
        .registerExitHook(
          OkHttpClient::class.java,
          "networkInterceptors()Ljava/util/List;",
          ArtTooling.ExitHook<MutableList<Interceptor>> { list ->
            if (list.none { it is OkHttp2Interceptor }) {
              okHttp2Interceptors = list
              list.add(0, OkHttp2Interceptor(trackerService, interceptionService))
            }
            list
          }
        )
      logger.debugHidden("Instrumented ${OkHttpClient::class.qualifiedName}")
      okHttpInstrumented = true
    } catch (e: NoClassDefFoundError) {
      // Ignore. App may not depend on OkHttp.
    }

    try {
      environment
        .artTooling()
        .registerExitHook(
          okhttp3.OkHttpClient::class.java,
          "networkInterceptors()Ljava/util/List;",
          ArtTooling.ExitHook<List<okhttp3.Interceptor>> { list ->
            val interceptors = ArrayList<okhttp3.Interceptor>()
            interceptors.add(OkHttp3Interceptor(trackerService, interceptionService))
            interceptors.addAll(list)
            interceptors
          }
        )
      logger.debugHidden("Instrumented ${okhttp3.OkHttpClient::class.qualifiedName}")
      okHttpInstrumented = true
    } catch (e: NoClassDefFoundError) {
      // Ignore. App may not depend on OkHttp.
    }
    if (!okHttpInstrumented) {
      // Only log if both OkHttp 2 and 3 were not detected
      logger.debug(
        "Did not instrument OkHttpClient. App does not use OKHttp or class is omitted by app reduce"
      )
    }

    try {
      val grpcInterceptor = GrpcInterceptor()
      listOf(GRPC_FOR_ADDRESS_METHOD, GRPC_FOR_TARGET_METHOD).forEach { method ->
        environment
          .artTooling()
          .registerExitHook(
            ManagedChannelBuilder::class.java,
            method,
            ArtTooling.ExitHook<ManagedChannelBuilder<*>> { channelBuilder ->
              channelBuilder.intercept(grpcInterceptor)
              channelBuilder
            }
          )
      }
      logger.debugHidden("Instrumented ${ManagedChannelBuilder::class.qualifiedName}")
    } catch (e: NoClassDefFoundError) {
      logger.debug(
        "Did not instrument 'ManagedChannelBuilder'. App does not use gRPC or class is omitted by app reduce"
      )
    }
  }

  override fun onDispose() {
    okHttp2Interceptors?.removeIf { it is OkHttp2Interceptor }
    scope.cancel("Network Inspector has been disposed.")
  }
}
