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
import com.squareup.okhttp.MediaType
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class OkHttp2 : AbstractHttpClient<Request.Builder>() {
  private val client = OkHttpClient()

  override val name: String
    get() = "OKHTTP2"

  override fun Request.Builder.configurePost(data: ByteArray, type: String): Request.Builder {
    return post(RequestBody.create(MediaType.parse(type), data))
  }

  override suspend fun doRequest(
    url: String,
    encoding: String?,
    configure: Request.Builder.() -> Request.Builder,
  ): Result {
    return withContext(Dispatchers.IO) {
      val requestBuilder = Request.Builder()
      if (encoding != null) {
        requestBuilder.header("Accept-Encoding", encoding)
      }
      val request = requestBuilder.url(url).configure().build()
      val response: Response = client.newCall(request).execute()
      return@withContext Result(response.code(), response.body().string())
    }
  }
}
