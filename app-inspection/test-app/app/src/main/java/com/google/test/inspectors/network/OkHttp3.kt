/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.test.inspectors.network

import com.google.test.inspectors.network.HttpClient.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

internal class OkHttp3 : AbstractHttpClient<Request.Builder>() {
  private val client = OkHttpClient()

  override val name: String
    get() = "OKHTTP3"

  override fun Request.Builder.configurePost(data: ByteArray, type: String): Request.Builder {
    return post(data.toRequestBody(type.toMediaType()))
  }

  override suspend fun doRequest(
    url: String,
    configure: Request.Builder.() -> Request.Builder,
  ): Result {
    return withContext(Dispatchers.IO) {
      val request = Request.Builder().header("Accept-Encoding", "gzip").url(url).configure().build()
      client.newCall(request).execute().use { response ->
        return@withContext Result(response.code, response.body?.string() ?: "null")
      }
    }
  }

  suspend fun doPostOneShot(url: String, data: ByteArray, type: String): Result {
    return doRequest(url) {
      val body =
        object : DelegatingRequestBody(data.toRequestBody(type.toMediaType())) {
          override fun isOneShot() = true
        }
      post(body)
    }
  }

  suspend fun doPostDuplex(url: String, data: ByteArray, type: String): Result {
    return doRequest(url) {
      val body =
        object : DelegatingRequestBody(data.toRequestBody(type.toMediaType())) {
          override fun isDuplex() = true
        }
      post(body)
    }
  }

  private abstract class DelegatingRequestBody(private val delegate: RequestBody) : RequestBody() {

    override fun contentType() = delegate.contentType()

    override fun contentLength() = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
      delegate.writeTo(sink)
    }
  }
}
