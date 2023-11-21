package com.google.test.inspectors.grpc

import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import io.grpc.Metadata
import io.grpc.Metadata.ASCII_STRING_MARSHALLER
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status

private val TRAILERS =
  Metadata().apply { put(Metadata.Key.of("Custom-Trailer", ASCII_STRING_MARSHALLER), "A trailer") }

private val HEADERS =
  Metadata().apply {
    put(Metadata.Key.of("Custom-Response-Header", ASCII_STRING_MARSHALLER), "A response header")
  }

/** An interceptor that adds a response header */
internal class ServerMetadataInterceptor : ServerInterceptor {

  override fun <Req : Any, Res : Any> interceptCall(
    call: ServerCall<Req, Res>,
    requestHeaders: Metadata,
    next: ServerCallHandler<Req, Res>
  ): ServerCall.Listener<Req> {
    return next.startCall(
      object : SimpleForwardingServerCall<Req, Res>(call) {
        override fun sendHeaders(responseHeaders: Metadata) {
          responseHeaders.merge(HEADERS)
          println(responseHeaders)
          super.sendHeaders(responseHeaders)
        }

        override fun close(status: Status, trailers: Metadata) {
          trailers.merge(TRAILERS)
          super.close(status, trailers)
        }
      },
      requestHeaders
    )
  }
}
