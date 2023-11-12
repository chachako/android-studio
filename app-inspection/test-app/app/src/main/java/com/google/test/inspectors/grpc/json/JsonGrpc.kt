package com.google.test.inspectors.grpc.json

import com.google.gson.Gson
import io.grpc.MethodDescriptor
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

private const val METHOD_NAME = "doJsonRpc"

internal object JsonGrpc {
  const val SERVICE_NAME = "JsonService"

  val doJsonGrpcMethod: MethodDescriptor<JsonRequest, JsonResponse> =
    MethodDescriptor.newBuilder<JsonRequest, JsonResponse>()
      .setRequestMarshaller(JsonMarshaller(JsonRequest::class.java))
      .setResponseMarshaller(JsonMarshaller(JsonResponse::class.java))
      .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, METHOD_NAME))
      .setType(MethodDescriptor.MethodType.UNARY)
      .build()

  private class JsonMarshaller<T : Any>(private val clz: Class<T>) :
    MethodDescriptor.Marshaller<T> {
    private val gson = Gson()

    override fun stream(value: T) =
      ByteArrayInputStream(gson.toJson(value, clz).toByteArray(StandardCharsets.UTF_8))

    override fun parse(stream: InputStream): T =
      gson.fromJson(InputStreamReader(stream, StandardCharsets.UTF_8), clz)
  }
}
