package com.google.test.inspectors

import com.google.test.inspectors.HttpClient.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class OkHttp3 : AbstractHttpClient<Request.Builder>() {
  private val client = OkHttpClient()

  override val name: String
    get() = "OKHTTP3"

  override fun Request.Builder.configurePost(data: ByteArray, type: String): Request.Builder {
    return post(data.toRequestBody(type.toMediaType()))
  }

  override suspend fun doRequest(
    url: String,
    configure: Request.Builder.() -> Request.Builder
  ): Result {
    return withContext(Dispatchers.IO) {
      val request = Request.Builder().header("Accept-Encoding", "gzip").url(url).configure().build()
      client.newCall(request).execute().use { response ->
        return@withContext Result(response.code, response.body?.string() ?: "null")
      }
    }
  }
}
