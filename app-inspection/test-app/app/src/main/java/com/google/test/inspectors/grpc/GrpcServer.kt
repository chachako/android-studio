package com.google.test.inspectors.grpc

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.test.inspectors.grpc.json.JsonService
import com.google.test.inspectors.grpc.proto.ProtoService
import io.grpc.inprocess.InProcessServerBuilder
import java.util.concurrent.Executors

private const val SERVER_NAME = "TestServer"

internal class GrpcServer {

  private val server =
    InProcessServerBuilder.forName(SERVER_NAME)
      .executor(
        Executors.newFixedThreadPool(
          2,
          ThreadFactoryBuilder().setNameFormat("server-thread-%d").build()
        )
      )
      .addService(ProtoService())
      .addService(JsonService())
      .build()

  fun start() {
    server.start()
  }

  fun shutdown() {
    server.shutdownNow()
  }
}
