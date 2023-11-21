package com.google.test.inspectors.grpc

import io.grpc.Grpc
import io.grpc.InsecureServerCredentials
import io.grpc.ServerRegistry
import io.grpc.netty.NettyServerProvider
import io.grpc.protobuf.services.ProtoReflectionService
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit.SECONDS
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default

/** A simple gRPC Server */
fun main(args: Array<String>) {
  val parser = ArgParser("auto-delete")
  val port by
    parser.option(ArgType.Int, shortName = "p", description = "Port number").default(54321)
  parser.parse(args)

  ServerRegistry.getDefaultRegistry().register(NettyServerProvider())
  // TODO(aalbert): Add secure channel support
  val server =
    Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
      .addService(ProtoReflectionService.newInstance())
      .addService(ProtoService())
      .addService(JsonService())
      .intercept(ServerMetadataInterceptor())
      .build()
  server.start()
  val addresses =
    NetworkInterface.getNetworkInterfaces().toList().flatMap {
      it.inetAddresses.toList().filterIsInstance<Inet4Address>().map { "${it.hostAddress}:$port" }
    }
  println("Server started, listening on:\n  ${addresses.joinToString("\n  ") { it }}")
  Runtime.getRuntime()
    .addShutdownHook(
      object : Thread() {
        override fun run() {
          println("*** shutting down gRPC server since JVM is shutting down")
          try {
            server.shutdown().awaitTermination(30, SECONDS)
          } catch (e: InterruptedException) {
            e.printStackTrace(System.err)
          }
          println("*** server shut down")
        }
      }
    )
  server.awaitTermination()
}
