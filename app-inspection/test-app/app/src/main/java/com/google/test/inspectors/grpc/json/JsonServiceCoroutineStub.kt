package com.google.test.inspectors.grpc.json

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.kotlin.AbstractCoroutineStub
import io.grpc.kotlin.ClientCalls

internal class JsonServiceCoroutineStub(
  channel: Channel,
  callOptions: CallOptions = CallOptions.DEFAULT,
) : AbstractCoroutineStub<JsonServiceCoroutineStub>(channel, callOptions) {
  override fun build(channel: Channel, callOptions: CallOptions): JsonServiceCoroutineStub =
    JsonServiceCoroutineStub(channel, callOptions)

  suspend fun doJsonGrpc(request: JsonRequest, headers: Metadata = Metadata()): JsonResponse =
    ClientCalls.unaryRpc(channel, JsonGrpc.doJsonGrpcMethod, request, callOptions, headers)
}
