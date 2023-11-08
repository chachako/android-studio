package com.google.test.inspectors

import com.google.test.inspectors.HttpClient.Result

internal abstract class AbstractHttpClient<T> : HttpClient {
  final override suspend fun doGet(url: String): Result {
    return doRequest(url)
  }

  final override suspend fun doPost(url: String, data: ByteArray, type: String): Result {
    return doRequest(url) { configurePost(data, type) }
  }

  abstract suspend fun doRequest(url: String, configure: T.() -> T = { this }): Result

  abstract fun T.configurePost(data: ByteArray, type: String): T
}
