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

package com.android.tools.appinspection.network.trackers

import com.android.tools.appinspection.network.reporters.ConnectionReporter
import com.android.tools.appinspection.network.rules.NetworkInterceptionMetrics
import java.io.InputStream
import java.io.OutputStream
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.HttpTransport

/**
 * This is the concrete AndroidStudio implementation of the public HTTP tracking interface. We're
 * passing the HTTP events and content to the network inspector.
 *
 * Note that the HTTP stacks using [HttpConnectionTracker] should not care or know about the details
 * of the implementation of the interface.
 */
internal class ConnectionTracker(
  private val myUrl: String,
  private val callstack: String,
  private val reporter: ConnectionReporter,
) : HttpConnectionTracker {

  override fun disconnect() {}

  override fun error(message: String) {
    reporter.onError(message)
  }

  override fun trackRequestBody(stream: OutputStream): OutputStream {
    return OutputStreamTracker(stream, reporter.createOutputStreamReporter())
  }

  override fun trackRequest(
    method: String,
    headers: Map<String, List<String>>,
    transport: HttpTransport,
  ) {
    reporter.onRequest(myUrl, callstack, method, headers, transport)
    reporter.reportCurrentThread()
  }

  override fun trackResponseHeaders(responseCode: Int, headers: Map<String?, List<String>>) {
    reporter.onResponse(responseCode, headers)
    reporter.reportCurrentThread()
  }

  override fun trackResponseBody(stream: InputStream): InputStream {
    return InputStreamTracker(stream, reporter.createInputStreamReporter())
  }

  override fun trackResponseInterception(interception: NetworkInterceptionMetrics) {
    reporter.onInterception(interception)
  }
}
