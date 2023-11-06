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

import com.android.tools.appinspection.network.utils.Logger
import com.android.tools.appinspection.network.utils.LoggerImpl
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
import io.grpc.Status

/**
 * A GRPC [ClientInterceptor] that sends events to the Network Inspector tool.
 *
 * TODO(295991498): Create and send the events instead of just logging them.
 */
internal class GrpcInterceptor(private val logger: Logger = LoggerImpl()) : ClientInterceptor {
  override fun <Req, Res> interceptCall(
    method: MethodDescriptor<Req, Res>,
    options: CallOptions,
    next: Channel
  ): ClientCall<Req, Res> =
    InterceptingClientCall(
      logger,
      method,
      next.newCall(method, options.withStreamTracerFactory(StreamTracer.Factory(logger)))
    )

  private class InterceptingClientCall<Req, Res>(
    private val logger: Logger,
    private val method: MethodDescriptor<Req, Res>,
    delegate: ClientCall<Req, Res>,
  ) : SimpleForwardingClientCall<Req, Res>(delegate) {
    override fun start(responseListener: Listener<Res>, headers: Metadata) {
      val listener = ClientCallListener(logger, responseListener)
      logger.debugHidden(
        "Request started: method=${method.fullMethodName} headers=$headers (${System.identityHashCode(headers)})"
      )
      super.start(listener, headers)
    }

    override fun sendMessage(message: Req) {
      super.sendMessage(message)
      logger.debugHidden("Request payload: ${message.toString().substringAfter('\n')}")
    }
  }

  private class StreamTracer(private val logger: Logger) : ClientStreamTracer() {
    override fun streamCreated(transportAttrs: Attributes, headers: Metadata) {
      val address = transportAttrs.get(TRANSPORT_ATTR_REMOTE_ADDR)
      logger.debugHidden(
        "streamCreated: address: $address headers=$headers (${System.identityHashCode(headers)})"
      )
    }

    override fun streamClosed(status: Status) {
      logger.debugHidden("streamClosed: $status")
    }

    override fun inboundTrailers(trailers: Metadata) {
      logger.debugHidden("Trailers: $trailers")
    }

    class Factory(private val logger: Logger) : ClientStreamTracer.Factory() {
      override fun newClientStreamTracer(info: StreamInfo, headers: Metadata) = StreamTracer(logger)
    }
  }

  class ClientCallListener<Res>(
    private val logger: Logger,
    responseListener: ClientCall.Listener<Res>,
  ) : SimpleForwardingClientCallListener<Res>(responseListener) {
    override fun onMessage(message: Res) {
      super.onMessage(message)
      logger.debugHidden("Response payload: ${message.toString().substringAfter('\n')}")
    }

    override fun onClose(status: Status?, trailers: Metadata?) {
      super.onClose(status, trailers)
      logger.debugHidden("onClose: $status")
    }

    override fun onHeaders(headers: Metadata) {
      super.onHeaders(headers)
      logger.debugHidden("Response headers: $headers")
    }
  }
}
