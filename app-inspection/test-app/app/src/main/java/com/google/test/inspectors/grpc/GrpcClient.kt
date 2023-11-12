package com.google.test.inspectors.grpc

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.grpc.proto.ProtoRequest
import com.google.grpc.proto.ProtoServiceGrpcKt
import com.google.test.inspectors.grpc.json.JsonRequest
import com.google.test.inspectors.grpc.json.JsonServiceCoroutineStub
import io.grpc.inprocess.InProcessChannelBuilder
import java.util.concurrent.Executors

internal class GrpcClient {
  private val serverName = "TestServer"

  private val channel =
    InProcessChannelBuilder.forName(serverName)
      .executor(
        Executors.newFixedThreadPool(
          4,
          ThreadFactoryBuilder().setNameFormat("client-thread-%d").build()
        )
      )
      .build()

  private val protoStub = ProtoServiceGrpcKt.ProtoServiceCoroutineStub(channel)
  private val jsonStub = JsonServiceCoroutineStub(channel)

  suspend fun doProtoGrpc(request: ProtoRequest) = protoStub.doProtoRpc(request)

  suspend fun doJsonGrpc(request: JsonRequest) = jsonStub.doJsonGrpc(request)
}
