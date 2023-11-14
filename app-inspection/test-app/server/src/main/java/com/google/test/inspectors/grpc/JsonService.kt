package com.google.test.inspectors.grpc

import com.google.test.inspectors.grpc.json.JsonGrpc
import com.google.test.inspectors.grpc.json.JsonResponse
import io.grpc.BindableService
import io.grpc.ServerServiceDefinition
import io.grpc.stub.ServerCalls

internal class JsonService : BindableService {

  override fun bindService(): ServerServiceDefinition {
    return ServerServiceDefinition.builder(JsonGrpc.SERVICE_NAME)
      .addMethod(
        JsonGrpc.doJsonGrpcMethod,
        ServerCalls.asyncUnaryCall { request, observer ->
          val response = JsonResponse("Hello ${request.name} (Json)")
          println("doJson:\n  Request: $request\n  Response: $response")
          observer.onNext(response)
          observer.onCompleted()
        }
      )
      .build()
  }
}
