package com.google.test.inspectors.grpc.custom

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.kotlin.AbstractCoroutineStub
import io.grpc.kotlin.ClientCalls

class CustomServiceCoroutineStub(
  channel: Channel,
  callOptions: CallOptions = CallOptions.DEFAULT,
) : AbstractCoroutineStub<CustomServiceCoroutineStub>(channel, callOptions) {
  override fun build(channel: Channel, callOptions: CallOptions): CustomServiceCoroutineStub =
    CustomServiceCoroutineStub(channel, callOptions)

  suspend fun doCustomGrpc(request: CustomRequest, headers: Metadata = Metadata()): CustomResponse =
    ClientCalls.unaryRpc(channel, CustomGrpc.doCustomGrpcMethod, request, callOptions, headers)
}
