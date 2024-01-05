/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.appinspection.network.reporters

import androidx.inspection.Connection
import com.android.tools.appinspection.network.reporters.StreamReporter.InputStreamReporter
import com.android.tools.appinspection.network.reporters.StreamReporter.OutputStreamReporter
import com.android.tools.appinspection.network.rules.NetworkInterceptionMetrics
import com.android.tools.appinspection.network.utils.ConnectionIdGenerator
import com.android.tools.appinspection.network.utils.sendHttpConnectionEvent
import studio.network.inspection.NetworkInspectorProtocol
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.Header
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.HttpTransport

/**
 * A class that is used to report connection related activity to Studio such as making requests or
 * receiving responses.
 */
internal interface ConnectionReporter : ThreadReporter {

  fun onRequest(
    url: String,
    callstack: String,
    method: String,
    headers: Map<String, List<String>>,
    transport: HttpTransport
  )

  fun onResponse(responseCode: Int, headers: Map<String?, List<String>>)

  fun onInterception(interception: NetworkInterceptionMetrics)

  fun onError(status: String)

  fun createInputStreamReporter(): StreamReporter

  fun createOutputStreamReporter(): StreamReporter

  companion object {

    fun createConnectionTracker(connection: Connection): ConnectionReporter =
      ConnectionReporterImpl(connection)
  }
}

private class ConnectionReporterImpl(private val connection: Connection) :
  ConnectionReporter, ThreadReporter {

  private val connectionId = ConnectionIdGenerator.nextId()
  private val threadReporter = ThreadReporter.createThreadReporter(connection, connectionId)

  override fun reportCurrentThread() {
    threadReporter.reportCurrentThread()
  }

  override fun createInputStreamReporter(): StreamReporter {
    return InputStreamReporter(connection, connectionId, threadReporter)
  }

  override fun createOutputStreamReporter(): StreamReporter {
    return OutputStreamReporter(connection, connectionId, threadReporter)
  }

  override fun onRequest(
    url: String,
    callstack: String,
    method: String,
    headers: Map<String, List<String>>,
    transport: HttpTransport
  ) {
    connection.sendHttpConnectionEvent(
      NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
        .setHttpRequestStarted(
          NetworkInspectorProtocol.HttpConnectionEvent.RequestStarted.newBuilder()
            .setUrl(url)
            .setTrace(callstack)
            .setMethod(method)
            .addAllHeaders(
              headers.entries.map {
                Header.newBuilder().setKey(it.key).addAllValues(it.value).build()
              }
            )
            .setTransport(transport)
        )
        .setConnectionId(connectionId)
    )
  }

  override fun onResponse(responseCode: Int, headers: Map<String?, List<String>>) {
    connection.sendHttpConnectionEvent(
      NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
        .setHttpResponseStarted(
          NetworkInspectorProtocol.HttpConnectionEvent.ResponseStarted.newBuilder()
            .setResponseCode(responseCode)
            .addAllHeaders(
              headers.entries.map {
                Header.newBuilder().setKey(it.key ?: "null").addAllValues(it.value).build()
              }
            )
        )
        .setConnectionId(connectionId)
    )
  }

  override fun onInterception(interception: NetworkInterceptionMetrics) {
    if (interception.criteriaMatched) {
      connection.sendHttpConnectionEvent(
        NetworkInspectorProtocol.HttpConnectionEvent.newBuilder().apply {
          httpResponseInterceptedBuilder.apply {
            statusCode = interception.statusCode
            headerAdded = interception.headerAdded
            headerReplaced = interception.headerReplaced
            bodyReplaced = interception.bodyReplaced
            bodyModified = interception.bodyModified
          }
          this.connectionId = this@ConnectionReporterImpl.connectionId
        }
      )
    }
  }

  override fun onError(status: String) {
    connection.sendHttpConnectionEvent(
      NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
        .setHttpClosed(
          NetworkInspectorProtocol.HttpConnectionEvent.Closed.newBuilder().setCompleted(false)
        )
        .setConnectionId(connectionId)
    )
  }
}
