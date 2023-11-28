package com.google.test.inspectors.grpc.custom

import com.google.test.inspectors.grpc.custom.CustomResponse.Item
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.InputStream

private const val METHOD_NAME = "doCustomRpc"

object CustomGrpc {

  const val SERVICE_NAME = "CustomService"

  val doCustomGrpcMethod: MethodDescriptor<CustomRequest, CustomResponse> =
    MethodDescriptor.newBuilder<CustomRequest, CustomResponse>()
      .setRequestMarshaller(RequestMarshaller())
      .setResponseMarshaller(ResponseMarshaller())
      .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, METHOD_NAME))
      .setType(MethodDescriptor.MethodType.UNARY)
      .build()

  private class RequestMarshaller : Marshaller<CustomRequest> {

    override fun stream(request: CustomRequest): InputStream {
      return ByteArrayInputStream(request.name.encodeToByteArray())
    }

    override fun parse(stream: InputStream): CustomRequest {
      return CustomRequest(stream.reader().readText())
    }
  }

  private class ResponseMarshaller : Marshaller<CustomResponse> {

    override fun stream(response: CustomResponse): InputStream {
      val text = "${response.message}|${response.items.joinToString { it.name }}"
      return ByteArrayInputStream(text.encodeToByteArray())
    }

    override fun parse(stream: InputStream): CustomResponse {
      val split = stream.reader().readText().split('|')
      return CustomResponse(split[0], split[1].split(',').map { Item(it) })
    }
  }

  @JvmStatic
  fun main(args: Array<String>) {
    val o = CustomResponse("Foo", listOf(Item("i1"), Item("i2")))
    val marshaller = ResponseMarshaller()
    val custom = marshaller.stream(o).reader().readText()
    println(custom)
    println(marshaller.parse(marshaller.stream(o)))
  }
}
