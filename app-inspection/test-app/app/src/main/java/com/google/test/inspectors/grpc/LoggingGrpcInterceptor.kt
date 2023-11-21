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

package com.google.test.inspectors.grpc

import android.util.Log
import io.grpc.Attributes
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ClientStreamTracer
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status

private const val TAG = "gRPC"

/** An interceptor that logs some useful information. */
internal class LoggingGrpcInterceptor : ClientInterceptor {

  override fun <Req : Any, Res : Any> interceptCall(
    method: MethodDescriptor<Req, Res>,
    options: CallOptions,
    next: Channel
  ): ClientCall<Req, Res> {
    return InterceptingClientCall(
      next.newCall(method, options.withStreamTracerFactory(StreamTracer.Factory()))
    )
  }

  private class InterceptingClientCall<Req : Any, Res : Any>(
    delegate: ClientCall<Req, Res>,
  ) : SimpleForwardingClientCall<Req, Res>(delegate) {

    override fun start(responseListener: Listener<Res>, headers: Metadata) {
      Log.i(TAG, "InterceptingClientCall.start(): ${headers.toLog()}")
      val listener = ClientCallListener(responseListener)
      super.start(listener, headers)
    }

    override fun sendMessage(message: Req) {
      super.sendMessage(message)
    }
  }

  private class StreamTracer : ClientStreamTracer() {

    override fun streamCreated(transportAttrs: Attributes, headers: Metadata) {
      Log.i(TAG, "StreamTracer.streamCreated(): ${headers.toLog()}")
    }

    class Factory : ClientStreamTracer.Factory() {

      override fun newClientStreamTracer(info: StreamInfo, headers: Metadata): StreamTracer {
        Log.i(TAG, "ClientStreamTracer.Factory.newClientStreamTracer(): ${headers.toLog()}")
        return StreamTracer()
      }
    }
  }

  class ClientCallListener<Res : Any>(
    responseListener: ClientCall.Listener<Res>,
  ) : SimpleForwardingClientCallListener<Res>(responseListener) {

    override fun onMessage(message: Res) {
      super.onMessage(message)
    }

    override fun onHeaders(headers: Metadata) {
      Log.i(TAG, "ClientCallListener.onHeaders(): ${headers.toLog()}")
      super.onHeaders(headers)
    }

    override fun onClose(status: Status, trailers: Metadata) {
      Log.i(TAG, "ClientCallListener.onClose(): ${trailers.toLog()}")
      super.onClose(status, trailers)
    }
  }
}

private fun Metadata.toLog() = "$this (${System.identityHashCode(this)})"
