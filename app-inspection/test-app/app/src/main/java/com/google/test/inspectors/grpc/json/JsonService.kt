package com.google.test.inspectors.grpc.json

import io.grpc.BindableService
import io.grpc.ServerServiceDefinition
import io.grpc.stub.ServerCalls

internal class JsonService : BindableService {

  override fun bindService(): ServerServiceDefinition {
    return ServerServiceDefinition.builder(JsonGrpc.SERVICE_NAME)
      .addMethod(
        JsonGrpc.doJsonGrpcMethod,
        ServerCalls.asyncUnaryCall { request, observer ->
          observer.onNext(JsonResponse("Hello ${request.name} (Json)"))
          observer.onCompleted()
        }
      )
      .build()
  }
}
