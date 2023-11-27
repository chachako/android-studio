package com.google.test.inspectors.grpc.xml

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.kotlin.AbstractCoroutineStub
import io.grpc.kotlin.ClientCalls

class XmlServiceCoroutineStub(
  channel: Channel,
  callOptions: CallOptions = CallOptions.DEFAULT,
) : AbstractCoroutineStub<XmlServiceCoroutineStub>(channel, callOptions) {
  override fun build(channel: Channel, callOptions: CallOptions): XmlServiceCoroutineStub =
    XmlServiceCoroutineStub(channel, callOptions)

  suspend fun doXmlGrpc(request: XmlRequest, headers: Metadata = Metadata()): XmlResponse =
    ClientCalls.unaryRpc(channel, XmlGrpc.doXmlGrpcMethod, request, callOptions, headers)
}
