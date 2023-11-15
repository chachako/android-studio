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

package com.android.tools.appinspection.network

import com.android.tools.appinspection.network.grpc.GrpcInterceptor
import com.android.tools.appinspection.network.trackers.GrpcTracker
import com.android.tools.appinspection.network.utils.ConnectionIdGenerator
import com.android.tools.appinspection.network.utils.TestLogger
import com.google.common.truth.Truth.assertThat
import com.google.grpc.test.GreeterGrpc
import com.google.grpc.test.HelloRequest
import com.google.grpc.test.HelloResponse
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.After
import org.junit.Before
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.GRPC_CALL_ENDED
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.GRPC_CALL_STARTED
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.GRPC_MESSAGE_RECEIVED
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.GRPC_MESSAGE_SENT
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.GRPC_STREAM_CREATED
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.GRPC_THREAD

private val EXPECTED_EVENT_TYPES =
  listOf(
    GRPC_STREAM_CREATED,
    GRPC_THREAD,
    GRPC_CALL_STARTED,
    GRPC_MESSAGE_SENT,
    GRPC_MESSAGE_RECEIVED,
    GRPC_CALL_ENDED
  )

class GrpcTest {

  private val serverName = InProcessServerBuilder.generateName()
  private val server =
    InProcessServerBuilder.forName(serverName).directExecutor().addService(GreeterService()).build()
  private val fakeConnection = FakeConnection()
  private val testLogger = TestLogger()
  private val channel =
    InProcessChannelBuilder.forName(serverName)
      .directExecutor()
      .intercept(GrpcInterceptor { GrpcTracker(fakeConnection, testLogger) })
      .build()
  private val stub = GreeterGrpc.newBlockingStub(channel)

  private class GreeterService : GreeterGrpc.GreeterImplBase() {

    override fun sayHello(request: HelloRequest, responseObserver: StreamObserver<HelloResponse>) {
      val reply = HelloResponse.newBuilder().setMessage("Hello " + request.name).build()
      responseObserver.onNext(reply)
      responseObserver.onCompleted()
    }
  }

  @Before
  fun setUp() {
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdownNow()
    server.awaitTermination(5, SECONDS)
    ConnectionIdGenerator.id.set(0)
  }

  @Test
  fun testEventContent() {
    val response = stub.sayHello(HelloRequest.newBuilder().setName("Foo").build())

    assertThat(response).isEqualTo(HelloResponse.newBuilder().setMessage("Hello Foo").build())
    assertThat(fakeConnection.grpcData.map { it.toDebugString() })
      .containsExactly(
        """
          grpc_call_started {
            service: "greeter.Greeter"
            method: "SayHello"
            headers {
              key: "grpc-accept-encoding"
              values: "gzip"
            }
            headers {
              key: "user-agent"
              values: "grpc-java-inprocess/1.57.0"
            }
            trace: "com.android.tools.appinspection.network.GrpcTest.testEventContent(GrpcTest.kt)"
          }
        """
          .trimIndent(),
        """
          grpc_thread {
            thread_id: 1
            thread_name: "main"
          }
        """
          .trimIndent(),
        """
          grpc_stream_created {
            address: "$serverName"
            headers {
              key: "grpc-accept-encoding"
              values: "gzip"
            }
          }
        """
          .trimIndent(),
        """
          grpc_message_sent {
            payload {
              bytes: "\n\003Foo"
              type: "com.google.grpc.test.HelloRequest"
              text: "name: \"Foo\"\n"
            }
          }
        """
          .trimIndent(),
        """
          grpc_message_received {
            payload {
              bytes: "\n\tHello Foo"
              type: "com.google.grpc.test.HelloResponse"
              text: "message: \"Hello Foo\"\n"
            }
          }
        """
          .trimIndent(),
        """
          grpc_call_ended {
            status: "OK"
          }
        """
          .trimIndent(),
      )
  }

  @Test
  fun testMultipleEvents_groupsByConnectionId() {
    stub.sayHello(HelloRequest.newBuilder().setName("Foo").build())
    stub.sayHello(HelloRequest.newBuilder().setName("Bar").build())

    val events = fakeConnection.grpcData.groupBy({ it.connectionId }) { it.unionCase }

    assertThat(events)
      .containsExactlyEntriesIn(
        mapOf(
          0L to EXPECTED_EVENT_TYPES,
          1L to EXPECTED_EVENT_TYPES,
        )
      )
  }

  private fun GrpcEvent.toDebugString(): String {
    return when (this.unionCase) {
        GRPC_CALL_STARTED -> cleanupGrpcCallStarted()
        GRPC_STREAM_CREATED -> cleanupGrpcStreamCreated()
        else -> this
      }
      .toString()
      .trim()
  }

  private fun GrpcEvent.cleanupGrpcCallStarted(): GrpcEvent {
    val trace =
      grpcCallStarted.trace
        .lines()
        .first { it.contains(this@GrpcTest::class.java.name) }
        .replace(":\\d+\\)".toRegex(), ")")
    return toBuilder().setGrpcCallStarted(grpcCallStarted.toBuilder().setTrace(trace)).build()
  }

  private fun GrpcEvent.cleanupGrpcStreamCreated(): GrpcEvent {
    return toBuilder()
      .setGrpcStreamCreated(grpcStreamCreated.toBuilder().setAddress(serverName))
      .build()
  }
}
