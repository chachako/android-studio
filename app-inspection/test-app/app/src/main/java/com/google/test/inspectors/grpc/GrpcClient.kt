package com.google.test.inspectors.grpc

import com.google.test.inspectors.grpc.json.JsonRequest
import com.google.test.inspectors.grpc.json.JsonServiceCoroutineStub
import com.google.test.inspectors.grpc.proto.ProtoRequest
import com.google.test.inspectors.grpc.proto.ProtoServiceGrpcKt
import io.grpc.ManagedChannelBuilder

internal class GrpcClient(host: String, port: Int) {
  // TODO(aalbert): Add secure channel support
  private val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()

  private val protoStub = ProtoServiceGrpcKt.ProtoServiceCoroutineStub(channel)
  private val jsonStub = JsonServiceCoroutineStub(channel)

  suspend fun doProtoGrpc(request: ProtoRequest) = protoStub.doProtoRpc(request)

  suspend fun doJsonGrpc(request: JsonRequest) = jsonStub.doJsonGrpc(request)
}
