package com.google.test.inspectors.grpc

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor

private val HEADERS =
  Metadata().apply {
    put(Metadata.Key.of("Custom-Request-Header", Metadata.ASCII_STRING_MARSHALLER), "Foo")
  }

/** An interceptor that adds a request header */
internal class ClientMetadataInterceptor : ClientInterceptor {

  override fun <Req : Any, Res : Any> interceptCall(
    method: MethodDescriptor<Req, Res>,
    callOptions: CallOptions,
    next: Channel
  ): ClientCall<Req, Res> {
    return object : SimpleForwardingClientCall<Req, Res>(next.newCall(method, callOptions)) {
      override fun start(responseListener: Listener<Res>?, headers: Metadata) {
        headers.merge(HEADERS)
        super.start(responseListener, headers)
      }
    }
  }
}
