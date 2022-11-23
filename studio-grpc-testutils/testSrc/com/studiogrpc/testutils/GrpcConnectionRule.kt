/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.studiogrpc.testutils

import com.android.tools.idea.io.grpc.BindableService
import com.android.tools.idea.io.grpc.ManagedChannel
import com.android.tools.idea.io.grpc.Server
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder
import com.android.tools.idea.io.grpc.inprocess.InProcessServerBuilder
import org.junit.rules.ExternalResource

/** JUnit rule for creating an in-process gRPC client/server connection. */
class GrpcConnectionRule(val services: List<BindableService>) : ExternalResource() {
  private lateinit var server: Server

  lateinit var channel: ManagedChannel
    private set

  override fun before() {
    val serverName: String = InProcessServerBuilder.generateName()
    server =
      InProcessServerBuilder.forName(serverName)
        .apply { services.forEach { addService(it) } }
        .build()
        .start()

    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
  }

  override fun after() {
    server.shutdownNow()
    channel.shutdownNow()
    server.awaitTermination()
  }
}
