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
import com.android.tools.appinspection.network.trackers.GrpcTracker
import com.android.tools.appinspection.network.utils.Logger
import com.squareup.okhttp.Interceptor
import com.squareup.okhttp.OkHttpClient
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
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

private const val GRPC_CHANNEL_CLASS_NAME = "io.grpc.internal.ManagedChannelImpl"
private const val GRPC_CHANNEL_FIELD_NAME = "interceptorChannel"

internal class NetworkInspector(
  connection: Connection,
  private val environment: InspectorEnvironment,
  private val trafficStatsProvider: TrafficStatsProvider = TrafficStatsProviderImpl(),
  private val speedDataIntervalMs: Long = POLL_INTERVAL_MS,
) : Inspector(connection) {

  /**
   * A list of gRPC specific hooks.
   *
   * TODO(b/313873107): Find a safe way to register gRPC hooks. Note that we only hook
   *   `AndroidChannelBuilder.forTarget()` because the implementation of `forAddress` calls
   *   `forTarget` and would result in double registration.
   */
  private val grpcHooks =
    listOf(
      GrpcHook(
        "io.grpc.ManagedChannelBuilder",
        "forAddress(Ljava/lang/String;I)Lio/grpc/ManagedChannelBuilder;",
      ),
      GrpcHook(
        "io.grpc.ManagedChannelBuilder",
        "forTarget(Ljava/lang/String;)Lio/grpc/ManagedChannelBuilder;",
      ),
      GrpcHook(
        "io.grpc.android.AndroidChannelBuilder",
        "forTarget(Ljava/lang/String;)Lio/grpc/android/AndroidChannelBuilder;",
      ),
    )

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
        if (isStarted) {
          Logger.error("Inspector already started")
          callback.reply(
            NetworkInspectorProtocol.Response.newBuilder()
              .setStartInspectionResponse(
                NetworkInspectorProtocol.StartInspectionResponse.newBuilder()
                  .setAlreadyStarted(true)
              )
              .build()
              .toByteArray()
          )
          return
        }
        val speedCollectionStarted = startSpeedCollection()
        val (javaNet, okhttp, grpc) = registerHooks()

        callback.reply(
          NetworkInspectorProtocol.Response.newBuilder()
            .setStartInspectionResponse(
              NetworkInspectorProtocol.StartInspectionResponse.newBuilder()
                .setTimestamp(System.nanoTime())
                .setSpeedCollectionStarted(speedCollectionStarted)
                .setJavaNetHooksRegistered(javaNet)
                .setOkhttpHooksRegistered(okhttp)
                .setGrpcHooksRegistered(grpc)
            )
            .build()
            .toByteArray()
        )
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

  /**
   * Starts collection of Speed Data.
   *
   * Returns true if collection started successfully, false if not.
   */
  private fun startSpeedCollection(): Boolean {
    // The app can have multiple Application instances. In that case, we use the first non-null
    // uid, which is most likely from the Application created by Android.
    val uid =
      environment.artTooling().findInstances(Application::class.java).firstNotNullOfOrNull {
        runCatching { it.applicationInfo?.uid }.getOrNull()
      }
    if (uid == null) {
      Logger.error(
        "Failed to find application instance. Collection of network speed is not available."
      )
      return false
    }
    scope.launch {
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
    return true
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
  internal fun registerHooks(): Triple<Boolean, Boolean, Boolean> {
    return Triple(registerJavaNetHooks(), registerOkHttpHooks(), registerGrpcHooks())
  }

  private fun registerJavaNetHooks(): Boolean {
    environment
      .artTooling()
      .registerExitHook(
        URL::class.java,
        "openConnection()Ljava/net/URLConnection;",
        ArtTooling.ExitHook<URLConnection> { urlConnection ->
          wrapURLConnection(urlConnection, trackerService, interceptionService)
        },
      )
    Logger.debugHidden("Instrumented ${URL::class.qualifiedName}")
    return true
  }

  private fun registerOkHttpHooks(): Boolean {
    var instrumented = false
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
          },
        )
      Logger.debugHidden("Instrumented ${OkHttpClient::class.qualifiedName}")
      instrumented = true
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
          },
        )
      Logger.debugHidden("Instrumented ${okhttp3.OkHttpClient::class.qualifiedName}")
      instrumented = true
    } catch (e: NoClassDefFoundError) {
      // Ignore. App may not depend on OkHttp.
    }
    if (!instrumented) {
      // Only log if both OkHttp 2 and 3 were not detected
      Logger.debug(
        "Did not instrument OkHttpClient. App does not use OKHttp or class is omitted by app reduce"
      )
    }
    return instrumented
  }

  private fun registerGrpcHooks(): Boolean {
    try {
      val grpcInterceptor = GrpcInterceptor { GrpcTracker(connection) }
      instrumentExistingGrpcChannels(grpcInterceptor)
      instrumentGrpcChannelBuilder(grpcInterceptor)
      Logger.debugHidden("Instrumented ${ManagedChannelBuilder::class.qualifiedName}")
      return true
    } catch (e: NoClassDefFoundError) {
      Logger.debug(
        "Did not instrument 'ManagedChannelBuilder'. App does not use gRPC or class is omitted by app reduce"
      )
      return false
    }
  }

  /**
   * Instruments pre-existing channels
   *
   * The gRPC interception API acts on the `ManagedChannel` builder, not the `ManagedChannel`
   * itself. By the time this is executed, the app could have already created channels, and we are
   * too late to instrument them using the API.
   *
   * Therefore, we find all existing instances on `ManagedChannel` and try to instrument them
   * manually using internal implementation details by reflection.
   *
   * This is known to be brittle but there doesn't seem to be a robust way of doing this.
   */
  private fun instrumentExistingGrpcChannels(grpcInterceptor: GrpcInterceptor) {
    environment.artTooling().findInstances(ManagedChannel::class.java).forEach {
      if (it::class.java.name == GRPC_CHANNEL_CLASS_NAME) {
        try {
          val field = it::class.java.getDeclaredField(GRPC_CHANNEL_FIELD_NAME)
          field.isAccessible = true
          val channel = field.get(it) as Channel
          field.set(it, InterceptingGrpcChannel(channel, grpcInterceptor))
          Logger.debugHidden("Preexisting channel was instrumented")
        } catch (e: Exception) {
          Logger.error("Preexisting channel of type was not instrumented", e)
        }
      } else {
        Logger.debugHidden("Preexisting channel of type ${it::class.java.name} was skipped")
      }
    }
  }

  private fun instrumentGrpcChannelBuilder(grpcInterceptor: GrpcInterceptor) {
    grpcHooks.forEach { hook ->
      val clazz =
        try {
          javaClass.classLoader.loadClass(hook.className)
        } catch (e: ClassNotFoundException) {
          Logger.debugHidden("Could not load class ${hook.className}")
          return@forEach
        }
      environment
        .artTooling()
        .registerExitHook(
          clazz,
          hook.method,
          ArtTooling.ExitHook<ManagedChannelBuilder<*>> { channelBuilder ->
            channelBuilder.intercept(grpcInterceptor)
            channelBuilder
          },
        )
    }
  }

  override fun onDispose() {
    okHttp2Interceptors?.removeIf { it is OkHttp2Interceptor }
    scope.cancel("Network Inspector has been disposed.")
  }

  private class InterceptingGrpcChannel(
    private val delegate: Channel,
    private val interceptor: GrpcInterceptor,
  ) : Channel() {

    override fun <Req : Any, Res : Any> newCall(
      methodDescriptor: MethodDescriptor<Req, Res>,
      callOptions: CallOptions,
    ): ClientCall<Req, Res> {
      return interceptor.interceptCall(methodDescriptor, callOptions, delegate)
    }

    override fun authority(): String = delegate.authority()
  }

  private class GrpcHook(val className: String, val method: String)

  private data class InspectorState(
    val speedDataCollectionStarted: Boolean,
    val instrumentationState: InstrumentationState,
  )

  @VisibleForTesting
  internal data class InstrumentationState(
    val okhttpHooksRegistered: Boolean,
    val grpcHooksRegistered: Boolean,
  )
}
