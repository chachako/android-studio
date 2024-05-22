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
package com.android.tools.appinspection.network.okhttp

import com.android.tools.appinspection.network.HttpTrackerFactory
import com.android.tools.appinspection.network.rules.FIELD_RESPONSE_STATUS_CODE
import com.android.tools.appinspection.network.rules.InterceptionRuleService
import com.android.tools.appinspection.network.rules.NetworkConnection
import com.android.tools.appinspection.network.rules.NetworkResponse
import com.android.tools.appinspection.network.trackers.HttpConnectionTracker
import java.io.IOException
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.HttpTransport.OKHTTP3

class OkHttp3Interceptor(
  private val trackerFactory: HttpTrackerFactory,
  private val interceptionRuleService: InterceptionRuleService,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    var tracker: HttpConnectionTracker? = null
    try {
      tracker = trackRequest(request)
    } catch (e: Throwable) {
      logInterceptionError(e, "OkHttp3 request")
    }
    var response: Response
    response =
      try {
        chain.proceed(request)
      } catch (ex: IOException) {
        tracker?.error(ex.toString())
        throw ex
      }
    try {
      if (tracker != null) {
        response = trackResponse(tracker, request, response)
      }
    } catch (e: Throwable) {
      logInterceptionError(e, "OkHttp3 response")
    }
    return response
  }

  private fun trackRequest(request: Request): HttpConnectionTracker? {
    val callstack = getOkHttpCallStack(request.javaClass.getPackage().name)
    // Do not track request if it was from this package
    if (shouldIgnoreRequest(callstack, this.javaClass.name)) return null
    val tracker = trackerFactory.trackConnection(request.url.toString(), callstack)
    tracker.trackRequest(request.method, request.headers.toMultimap(), OKHTTP3)
    request.body?.let { body ->
      val outputStream = tracker.trackRequestBody(createNullOutputStream())
      val bufferedSink = outputStream.sink().buffer()
      when {
        body.isDuplex() -> bufferedSink.writeUtf8("Duplex body omitted")
        body.isOneShot() -> bufferedSink.writeUtf8("One-shot body omitted")
        else -> body.writeTo(bufferedSink)
      }
      bufferedSink.close()
    }
    return tracker
  }

  private fun trackResponse(
    tracker: HttpConnectionTracker,
    request: Request,
    response: Response,
  ): Response {
    val fields = mutableMapOf<String?, List<String>>()
    fields.putAll(response.headers.toMultimap())
    fields[FIELD_RESPONSE_STATUS_CODE] = listOf(response.code.toString())
    val body = response.body ?: throw Exception("No response body found")

    val interceptedResponse =
      interceptionRuleService.interceptResponse(
        NetworkConnection(request.url.toString(), request.method),
        NetworkResponse(response.code, fields, body.source().inputStream()),
      )

    tracker.trackResponseHeaders(
      interceptedResponse.responseCode,
      interceptedResponse.responseHeaders,
    )
    val source = tracker.trackResponseBody(interceptedResponse.body).source().buffer()

    val responseBody = source.safeAsResponseBody(body.contentType(), body.contentLength())
    if (interceptedResponse.responseHeaders.containsKey(null)) {
      throw Exception("OkHttp3 does not allow null in headers")
    }
    val headers =
      headersOf(
        *interceptedResponse.responseHeaders.entries
          .flatMap { entry -> entry.value.map { listOf(entry.key, it) } }
          .flatten()
          .filterNotNull()
          .toTypedArray()
      )
    val code = headers[FIELD_RESPONSE_STATUS_CODE]?.toIntOrNull() ?: response.code
    return response.newBuilder().headers(headers).code(code).body(responseBody).build()
  }
}

/**
 * A safe way to call [BufferedSource.asResponseBody]
 *
 * Try new `asResponseBody` first. If app is using an old version of OkHttp3, use the deprecated
 * `create` method.
 *
 * Note that it's not possible to call the deprecated method directly because Kotlin assumes it's in
 * a companion object which doesn't exist in the old Java implementation.
 */
private fun BufferedSource.safeAsResponseBody(
  contentType: MediaType?,
  contentLength: Long,
): ResponseBody {
  return try {
    asResponseBody(contentType, contentLength)
  } catch (e: Throwable) {
    val method =
      ResponseBody::class
        .java
        .getDeclaredMethod(
          "create",
          MediaType::class.java,
          Long::class.java,
          BufferedSource::class.java,
        )
    method.invoke(null, contentType, contentLength, this) as ResponseBody
  }
}

/**
 * A safe way to call [Headers.headersOf]
 *
 * Try new `headersOf` first. If app is using an old version of OkHttp3, use the deprecated `of`
 * method.
 *
 * Note that it's not possible to call the deprecated method directly because Kotlin assumes it's in
 * a companion object which doesn't exist in the old Java implementation.
 */
private fun headersOf(vararg namesAndValues: String): Headers {
  return try {
    Headers.headersOf(*namesAndValues)
  } catch (e: Throwable) {
    val method = Headers::class.java.getDeclaredMethod("of", Array<String>::class.java)
    method.invoke(null, namesAndValues) as Headers
  }
}
