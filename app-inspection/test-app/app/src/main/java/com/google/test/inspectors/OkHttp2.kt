package com.google.test.inspectors

import com.google.test.inspectors.HttpClient.Result
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
    configure: Request.Builder.() -> Request.Builder
  ): Result {
    return withContext(Dispatchers.IO) {
      val request = Request.Builder().header("Accept-Encoding", "gzip").url(url).configure().build()
      val response: Response = client.newCall(request).execute()
      return@withContext Result(response.code(), response.body().string())
    }
  }
}
