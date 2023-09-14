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

import com.android.tools.appinspection.common.logError
import com.android.tools.appinspection.network.HttpTrackerFactory
import com.android.tools.appinspection.network.rules.FIELD_RESPONSE_STATUS_CODE
import com.android.tools.appinspection.network.rules.InterceptionRuleService
import com.android.tools.appinspection.network.rules.NetworkConnection
import com.android.tools.appinspection.network.rules.NetworkResponse
import com.android.tools.appinspection.network.trackers.HttpConnectionTracker
import com.squareup.okhttp.Headers
import com.squareup.okhttp.Interceptor
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response
import com.squareup.okhttp.ResponseBody
import okio.Okio
import java.io.IOException

class OkHttp2Interceptor(
    private val trackerFactory: HttpTrackerFactory,
    private val interceptionRuleService: InterceptionRuleService
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var tracker: HttpConnectionTracker? = null
        try {
            tracker = trackRequest(request)
        } catch (ex: Exception) {
            logError("Could not track an OkHttp2 request", ex)
        } catch (error: NoSuchMethodError) {
            logError(
                "Could not track an OkHttp2 request due to a missing method, which could"
                        + " happen if your project uses proguard to remove unused code",
                error
            )
        }
        var response: Response
        response = try {
            chain.proceed(request)
        } catch (ex: IOException) {
            tracker?.error(ex.toString())
            throw ex
        }
        try {
            if (tracker != null) {
                response = trackResponse(tracker, request, response)
            }
        } catch (ex: Exception) {
            logError("Could not track an OkHttp2 response", ex)
        } catch (error: NoSuchMethodError) {
            logError(
                "Could not track an OkHttp2 response due to a missing method, which could"
                        + " happen if your project uses proguard to remove unused code",
                error
            )
        }
        return response
    }

    private fun trackRequest(request: Request): HttpConnectionTracker? {
        val callstack = getOkHttpCallStack(request.javaClass.getPackage().name)
        // Do not track request if it was from this package
        if (shouldIgnoreRequest(callstack, this.javaClass.name)) return null
        val tracker = trackerFactory.trackConnection(request.urlString(), callstack)
        tracker.trackRequest(request.method(), request.headers().toMultimap())
        if (request.body() != null) {
            val outputStream = tracker.trackRequestBody(createNullOutputStream())
            val bufferedSink = Okio.buffer(Okio.sink(outputStream))
            request.body().writeTo(bufferedSink)
            bufferedSink.close()
        }
        return tracker
    }

    private fun trackResponse(
        tracker: HttpConnectionTracker,
        request: Request,
        response: Response
    ): Response {
        val fields = mutableMapOf<String?, List<String>>()
        fields.putAll(response.headers().toMultimap())
        fields[FIELD_RESPONSE_STATUS_CODE] = listOf(response.code().toString())

        val interceptedResponse = interceptionRuleService.interceptResponse(
            NetworkConnection(request.urlString(), request.method()),
            NetworkResponse(fields, response.body().source().inputStream())
        )

        tracker.trackResponseHeaders(interceptedResponse.responseHeaders)
        val source = Okio.buffer(
            Okio.source(tracker.trackResponseBody(interceptedResponse.body))
        )
        val body = ResponseBody.create(
            response.body().contentType(), response.body().contentLength(), source
        )
        if (interceptedResponse.responseHeaders.containsKey(null)) {
            throw Exception("OkHttp2 does not allow null in headers")
        }
        val headers = Headers.of(
            *interceptedResponse.responseHeaders.entries
                .flatMap { entry -> entry.value.map { listOf(entry.key, it) } }
                .flatten()
                .filterNotNull()
                .toTypedArray()
        )
        val code = headers[FIELD_RESPONSE_STATUS_CODE]?.toIntOrNull() ?: response.code()
        return response.newBuilder().headers(headers).code(code).body(body).build()
    }
}
