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

package com.android.tools.appinspection.network.grpc

import com.android.tools.appinspection.common.getStackTrace
import com.android.tools.appinspection.network.trackers.GrpcTracker
import io.grpc.Attributes
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ClientStreamTracer
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener
import io.grpc.Grpc.TRANSPORT_ATTR_REMOTE_ADDR
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.Marshaller
import io.grpc.Status
import java.net.SocketAddress

private const val UNKNOWN = "unknown"

/** A GRPC [ClientInterceptor] that sends events to the Network Inspector tool. */
internal class GrpcInterceptor(
  private val trackerFactory: GrpcTracker.Factory,
) : ClientInterceptor {
  override fun <Req : Any, Res : Any> interceptCall(
    method: MethodDescriptor<Req, Res>,
    options: CallOptions,
    next: Channel
  ): ClientCall<Req, Res> {
    val tracker = trackerFactory.newGrpcTracker()
    return InterceptingClientCall(
      tracker,
      method,
      next.newCall(method, options.withStreamTracerFactory(StreamTracer.Factory(tracker)))
    )
  }

  private class InterceptingClientCall<Req : Any, Res : Any>(
    private val tracker: GrpcTracker,
    private val method: MethodDescriptor<Req, Res>,
    delegate: ClientCall<Req, Res>,
  ) : SimpleForwardingClientCall<Req, Res>(delegate) {
    override fun start(responseListener: Listener<Res>, headers: Metadata) {
      val listener = ClientCallListener(tracker, method.responseMarshaller, responseListener)
      super.start(listener, headers)
      tracker.trackGrpcCallStarted(
        method.serviceName ?: UNKNOWN,
        method.bareMethodName ?: UNKNOWN,
        headers,
        getStackTrace(1)
      )
    }

    override fun sendMessage(message: Req) {
      super.sendMessage(message)
      tracker.trackGrpcMessageSent(message, method.requestMarshaller)
    }
  }

  private class StreamTracer(private val tracker: GrpcTracker) : ClientStreamTracer() {
    override fun streamCreated(transportAttrs: Attributes, headers: Metadata) {
      val address: SocketAddress? = transportAttrs.get(TRANSPORT_ATTR_REMOTE_ADDR)
      tracker.trackGrpcStreamCreated(address?.toString() ?: UNKNOWN, headers)
    }

    class Factory(private val tracker: GrpcTracker) : ClientStreamTracer.Factory() {
      override fun newClientStreamTracer(info: StreamInfo, headers: Metadata) =
        StreamTracer(tracker)
    }
  }

  class ClientCallListener<Res : Any>(
    private val tracker: GrpcTracker,
    private val marshaller: Marshaller<Res>,
    responseListener: ClientCall.Listener<Res>,
  ) : SimpleForwardingClientCallListener<Res>(responseListener) {

    override fun onHeaders(headers: Metadata) {
      super.onHeaders(headers)
      tracker.trackGrpcResponseHeaders(headers)
    }

    override fun onMessage(message: Res) {
      super.onMessage(message)
      tracker.trackGrpcMessageReceived(message, marshaller)
    }

    override fun onClose(status: Status, trailers: Metadata) {
      super.onClose(status, trailers)
      tracker.trackGrpcCallEnded(status, trailers)
    }
  }
}
