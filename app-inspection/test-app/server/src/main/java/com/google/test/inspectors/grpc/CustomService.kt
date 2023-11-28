package com.google.test.inspectors.grpc

import com.google.test.inspectors.grpc.custom.CustomGrpc
import com.google.test.inspectors.grpc.custom.CustomResponse
import com.google.test.inspectors.grpc.custom.CustomResponse.Item
import io.grpc.BindableService
import io.grpc.ServerServiceDefinition
import io.grpc.stub.ServerCalls

internal class CustomService : BindableService {

  override fun bindService(): ServerServiceDefinition {
    return ServerServiceDefinition.builder(CustomGrpc.SERVICE_NAME)
      .addMethod(
        CustomGrpc.doCustomGrpcMethod,
        ServerCalls.asyncUnaryCall { request, observer ->
          val response =
            CustomResponse("Hello ${request.name}", listOf(Item("Item 1"), Item("Item 2")))
          println("doCustom:\n  Request: $request\n  Response: $response")
          observer.onNext(response)
          observer.onCompleted()
        }
      )
      .build()
  }
}
