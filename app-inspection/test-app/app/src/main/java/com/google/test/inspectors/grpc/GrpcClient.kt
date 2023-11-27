package com.google.test.inspectors.grpc

import com.google.test.inspectors.grpc.json.JsonRequest
import com.google.test.inspectors.grpc.json.JsonServiceCoroutineStub
import com.google.test.inspectors.grpc.proto.ProtoRequest
import com.google.test.inspectors.grpc.proto.ProtoServiceGrpcKt
import com.google.test.inspectors.grpc.xml.XmlRequest
import com.google.test.inspectors.grpc.xml.XmlServiceCoroutineStub
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.TimeUnit.SECONDS

internal class GrpcClient(host: String, port: Int) : AutoCloseable {

  // TODO(aalbert): Add secure channel support
  private val channel =
    ManagedChannelBuilder.forAddress(host, port)
      .usePlaintext()
      .intercept(ClientMetadataInterceptor(), LoggingGrpcInterceptor())
      .build()

  private val protoStub = ProtoServiceGrpcKt.ProtoServiceCoroutineStub(channel)

  private val jsonStub = JsonServiceCoroutineStub(channel)

  private val xmlStub = XmlServiceCoroutineStub(channel)

  suspend fun doProtoGrpc(request: ProtoRequest) = protoStub.doProtoRpc(request)

  suspend fun doJsonGrpc(request: JsonRequest) = jsonStub.doJsonGrpc(request)

  suspend fun doXmlGrpc(request: XmlRequest) = xmlStub.doXmlGrpc(request)

  override fun close() {
    channel.shutdown()
    channel.awaitTermination(2, SECONDS)
  }
}
