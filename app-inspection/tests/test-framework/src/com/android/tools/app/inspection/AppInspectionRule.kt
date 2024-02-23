/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.app.inspection

import com.android.tools.app.inspection.AppInspection.*
import com.android.tools.fakeandroid.ProcessRunner.Companion.getProcessPath
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport.ExecuteRequest
import com.android.tools.profiler.proto.Transport.GetEventsRequest
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.android.tools.profiler.proto.TransportServiceGrpc.TransportServiceBlockingStub
import com.android.tools.transport.TransportRule
import com.android.tools.transport.device.SdkLevel
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A JUnit rule for wrapping useful app-inspection gRPC operations, spinning up a separate thread to
 * manage host / device communication.
 *
 * While running, it polls the transport framework for events, which should be received after calls
 * to [.sendCommand]. You can fetch those events using [ ][.sendCommandAndGetResponse] instead, or
 * by calling [ ][.consumeCollectedEvent].
 *
 * The thread will be spun down when the rule itself tears down.
 */
class AppInspectionRule(activityClass: String, sdkLevel: SdkLevel) : ExternalResource(), Runnable {
  val transportRule: TransportRule = TransportRule(activityClass, sdkLevel)

  private lateinit var transportStub: TransportServiceBlockingStub

  private val executor: ExecutorService = Executors.newSingleThreadExecutor()
  private var pid = 0

  private val nextCommandId = AtomicInteger(1)
  private val unexpectedResponses: MutableList<Common.Event> = ArrayList()
  private val commandIdToFuture = ConcurrentHashMap<Int, CompletableFuture<Common.Event>>()
  private val events = LinkedBlockingQueue<Common.Event>()
  private val payloads: MutableMap<Long, MutableList<Byte>> = ConcurrentHashMap()

  override fun apply(base: Statement, description: Description): Statement {
    return RuleChain.outerRule(transportRule).apply(super.apply(base, description), description)
  }

  fun hasEventToCollect(): Boolean {
    return events.size > 0
  }

  /** Pulls one event off the event queue, asserting if there are no events. */
  fun consumeCollectedEvent(): AppInspectionEvent {
    val event = events.take()
    assertThat(event).isNotNull()
    assertThat(event.pid).isEqualTo(pid)
    assertThat(event.hasAppInspectionEvent()).isTrue()
    return event.appInspectionEvent
  }

  /** Sends the inspection command and returns a non-null response. */
  fun sendCommandAndGetResponse(appInspectionCommand: AppInspectionCommand): AppInspectionResponse {
    val local = commandIdToFuture[sendCommand(appInspectionCommand)]!!
    val response = local[TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS]
    assertThat(response).isNotNull()
    assertThat(response.hasAppInspectionResponse()).isTrue()
    assertThat(response.pid).isEqualTo(pid)
    return response.appInspectionResponse
  }

  /** Sends the inspection command and returns its generated id. */
  fun sendCommand(appInspectionCommand: AppInspectionCommand): Int {
    val commandId = nextCommandId.getAndIncrement()
    val idAppInspectionCommand = appInspectionCommand.toBuilder().setCommandId(commandId).build()
    val command =
      Commands.Command.newBuilder()
        .setType(Commands.Command.CommandType.APP_INSPECTION)
        .setAppInspectionCommand(idAppInspectionCommand)
        .setStreamId(1234)
        .setPid(pid)
        .build()

    val local = CompletableFuture<Common.Event>()
    commandIdToFuture[commandId] = local
    val executeRequest = ExecuteRequest.newBuilder().setCommand(command).build()

    // Ignore execute response because the stub is blocking anyway
    transportStub.execute(executeRequest)

    return commandId
  }

  /**
   * Assert that the expected text is logged to the console.
   *
   * It's preferred using this over
   * [ ][com.android.tools.fakeandroid.FakeAndroidDriver.waitForInput] because this method also
   * includes a timeout for early aborting if things went wrong.
   */
  fun assertInput(expected: String) {
    assertThat(transportRule.androidDriver.waitForInput(expected, TIMEOUT_MS.toLong())).isTrue()
  }

  override fun run() {
    transportStub.getEvents(GetEventsRequest.getDefaultInstance()).forEach { event ->
      when {
        event.hasAppInspectionEvent() -> events.offer(event)
        event.hasAppInspectionResponse() -> handleResponse(event)
        event.hasAppInspectionPayload() -> handlePayload(event)
      }
    }
  }

  fun removePayload(payloadId: Long): List<Byte>? {
    return payloads.remove(payloadId)
  }

  override fun before() {
    this.transportStub = TransportServiceGrpc.newBlockingStub(transportRule.grpc.channel)
    this.pid = transportRule.pid

    executor.submit(this)
  }

  override fun after() {
    executor.shutdownNow()
    try {
      executor.awaitTermination(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
      throw RuntimeException(e)
    }
    assertThat(unexpectedResponses).isEmpty()
    assertThat(events).isEmpty()
  }

  private fun handleResponse(event: Common.Event) {
    val response = event.appInspectionResponse
    val commandId = response.commandId
    assertThat(commandId).isNotEqualTo(0)
    val localCommandCompleter = commandIdToFuture[commandId]
    localCommandCompleter?.complete(event) ?: unexpectedResponses.add(event)
  }

  private fun handlePayload(event: Common.Event) {
    val payloadId = event.groupId
    val payload = event.appInspectionPayload
    val bytes = payloads.computeIfAbsent(payloadId) { mutableListOf() }
    for (b in payload.chunk.toByteArray()) {
      bytes.add(b)
    }
  }

  companion object {
    /**
     * All asynchronous operations are expected to only take sub seconds, so we choose a relatively
     * small but still generous timeout. If a callback doesn't complete during it, we fail the test.
     */
    private const val TIMEOUT_MS = 10 * 1000

    /** Returns "on-device" path to the inspector's dex and checks its validity.k */
    @JvmStatic
    fun injectInspectorDex(): String {
      val onHostInspector = File(getProcessPath("test.inspector.dex.location"))
      assertThat(onHostInspector.exists()).isTrue()
      val onDeviceInspector = File(onHostInspector.name)
      // Should have already been copied over by the underlying transport test framework
      assertThat(onDeviceInspector.exists()).isTrue()
      return onDeviceInspector.absolutePath
    }
  }
}
