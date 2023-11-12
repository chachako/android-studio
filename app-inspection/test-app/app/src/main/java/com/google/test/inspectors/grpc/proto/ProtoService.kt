package com.google.test.inspectors.grpc.proto

import com.google.grpc.proto.ProtoRequest
import com.google.grpc.proto.ProtoResponse
import com.google.grpc.proto.ProtoServiceGrpc
import com.google.grpc.proto.protoResponse
import io.grpc.stub.StreamObserver

internal class ProtoService : ProtoServiceGrpc.ProtoServiceImplBase() {
  override fun doProtoRpc(request: ProtoRequest, responseObserver: StreamObserver<ProtoResponse>) {
    responseObserver.onNext(protoResponse { message = "Hello ${request.name} (proto)" })
    responseObserver.onCompleted()
  }
}
