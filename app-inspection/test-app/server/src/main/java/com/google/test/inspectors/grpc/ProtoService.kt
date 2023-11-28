package com.google.test.inspectors.grpc

import com.google.test.inspectors.grpc.proto.ProtoRequest
import com.google.test.inspectors.grpc.proto.ProtoResponse
import com.google.test.inspectors.grpc.proto.ProtoServiceGrpc
import com.google.test.inspectors.grpc.proto.protoResponse
import io.grpc.stub.StreamObserver

internal class ProtoService : ProtoServiceGrpc.ProtoServiceImplBase() {
  override fun doProtoRpc(request: ProtoRequest, responseObserver: StreamObserver<ProtoResponse>) {
    val response = protoResponse {
      message = "Hello ${request.name}"
      item.add("Item 1")
      item.add("Item 2")
    }
    println("doJson:\n  Request: $request\n  Response: $response")
    responseObserver.onNext(response)
    responseObserver.onCompleted()
  }
}
