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

package com.android.tools.appinspection.network.trackers

import androidx.inspection.Connection
import com.android.tools.appinspection.network.utils.ConnectionIdGenerator
import com.android.tools.appinspection.network.utils.Logger
import com.android.tools.appinspection.network.utils.LoggerImpl
import com.android.tools.idea.protobuf.ByteString
import io.grpc.Metadata
import io.grpc.MethodDescriptor.Marshaller
import io.grpc.Status
import java.util.concurrent.atomic.AtomicReference
import studio.network.inspection.NetworkInspectorProtocol
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcCallEnded
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcCallStarted
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcMessageReceived
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcMessageSent
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcMetadata
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcPayload
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcResponseHeaders
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcStreamCreated
import studio.network.inspection.NetworkInspectorProtocol.ThreadData

/** A [GrpcTracker] that sends events to Android Studio */
internal class GrpcTracker(
  private val connection: Connection,
  private val logger: Logger = LoggerImpl(),
) {
  private val connectionId = ConnectionIdGenerator.nextId()

  private var lastThread: AtomicReference<Thread?> = AtomicReference()

  fun trackGrpcCallStarted(
    service: String,
    method: String,
    requestHeaders: Metadata,
    trace: String
  ) {
    try {
      connection.reportGrpcEvent(
        GrpcEvent.newBuilder()
          .setGrpcCallStarted(
            GrpcCallStarted.newBuilder()
              .setService(service)
              .setMethod(method)
              .addAllRequestHeaders(requestHeaders.toGrpcMetadata())
              .setTrace(trace)
          )
      )
    } catch (t: Throwable) {
      logger.error("Failed to report a GrpcEvent", t)
    }
  }

  fun <T> trackGrpcMessageSent(message: T, marshaller: Marshaller<T>) {
    try {
      connection.reportGrpcEvent(
        GrpcEvent.newBuilder()
          .setGrpcMessageSent(
            GrpcMessageSent.newBuilder().setPayload(marshaller.createGrpcPayload(message))
          )
      )
    } catch (t: Throwable) {
      logger.error("Failed to report a GrpcEvent", t)
    }
  }

  fun trackGrpcStreamCreated(address: String, requestHeaders: Metadata) {
    try {
      connection.reportGrpcEvent(
        GrpcEvent.newBuilder()
          .setGrpcStreamCreated(
            GrpcStreamCreated.newBuilder()
              .setAddress(address)
              .addAllRequestHeaders(requestHeaders.toGrpcMetadata())
          )
      )
    } catch (t: Throwable) {
      logger.error("Failed to report a GrpcEvent", t)
    }
  }

  fun trackGrpcResponseHeaders(responseHeaders: Metadata) {
    try {
      connection.reportGrpcEvent(
        GrpcEvent.newBuilder()
          .setGrpcResponseHeaders(
            GrpcResponseHeaders.newBuilder().addAllResponseHeaders(responseHeaders.toGrpcMetadata())
          )
      )
    } catch (t: Throwable) {
      logger.error("Failed to report a GrpcEvent", t)
    }
  }

  fun <T> trackGrpcMessageReceived(message: T, marshaller: Marshaller<T>) {
    try {
      connection.reportGrpcEvent(
        GrpcEvent.newBuilder()
          .setGrpcMessageReceived(
            GrpcMessageReceived.newBuilder().setPayload(marshaller.createGrpcPayload(message))
          )
      )
    } catch (t: Throwable) {
      logger.error("Failed to report a GrpcEvent", t)
    }
  }

  fun trackGrpcCallEnded(status: Status, trailers: Metadata) {
    try {
      val callEnded =
        GrpcCallEnded.newBuilder()
          .setStatus(status.code.toString())
          .addAllTrailers(trailers.toGrpcMetadata())
      if (status.cause != null) {
        callEnded.setError(status.cause?.stackTraceToString())
      }
      connection.reportGrpcEvent(GrpcEvent.newBuilder().setGrpcCallEnded(callEnded))
    } catch (t: Throwable) {
      logger.error("Failed to report a GrpcEvent", t)
    }
  }

  private fun Connection.reportGrpcEvent(event: GrpcEvent.Builder) {
    sendGrpcEvent(event)
    reportCurrentThread()
  }

  private fun Connection.reportCurrentThread() {
    val thread = Thread.currentThread()
    val last = lastThread.getAndSet(thread)
    if (thread !== last) {
      sendGrpcEvent(
        GrpcEvent.newBuilder()
          .setGrpcThread(ThreadData.newBuilder().setThreadId(thread.id).setThreadName(thread.name))
      )
    }
  }

  private fun Connection.sendGrpcEvent(event: GrpcEvent.Builder) {
    sendEvent(
      NetworkInspectorProtocol.Event.newBuilder()
        .setTimestamp(System.nanoTime())
        .setGrpcEvent(event.setConnectionId(connectionId))
        .build()
        .toByteArray()
    )
  }

  fun interface Factory {
    fun newGrpcTracker(): GrpcTracker
  }
}

private fun Metadata.toGrpcMetadata(): List<GrpcMetadata> {
  return keys().map {
    val key = Metadata.Key.of(it, Metadata.ASCII_STRING_MARSHALLER)
    val values = getAll(key)?.toList() ?: emptyList()
    GrpcMetadata.newBuilder().setKey(it).addAllValues(values).build()
  }
}

private fun <T> Marshaller<T>.createGrpcPayload(message: T): GrpcPayload.Builder {
  val msg: Any = message ?: return GrpcPayload.newBuilder()
  val className = msg::class.java.name
  val protoPrefix = "# $className@${Integer.toHexString(message.hashCode())}\n"
  val text = message.toString().removePrefix(protoPrefix)
  return GrpcPayload.newBuilder()
    .setBytes(ByteString.copyFrom(stream(message).readAllBytes()))
    .setType(className)
    .setText(text)
}
