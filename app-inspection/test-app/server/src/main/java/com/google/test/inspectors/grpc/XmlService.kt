package com.google.test.inspectors.grpc

import com.google.test.inspectors.grpc.xml.XmlGrpc
import com.google.test.inspectors.grpc.xml.XmlResponse
import com.google.test.inspectors.grpc.xml.XmlResponse.Item
import io.grpc.BindableService
import io.grpc.ServerServiceDefinition
import io.grpc.stub.ServerCalls

internal class XmlService : BindableService {

  override fun bindService(): ServerServiceDefinition {
    return ServerServiceDefinition.builder(XmlGrpc.SERVICE_NAME)
      .addMethod(
        XmlGrpc.doXmlGrpcMethod,
        ServerCalls.asyncUnaryCall { request, observer ->
          val response =
            XmlResponse("Hello ${request.name}", mutableListOf(Item("Item 1"), Item("Item 2")))
          println("doXml:\n  Request: $request\n  Response: $response")
          observer.onNext(response)
          observer.onCompleted()
        }
      )
      .build()
  }
}
