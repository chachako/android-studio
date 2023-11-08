package com.google.test.inspectors

internal interface HttpClient {
  val name: String

  suspend fun doGet(url: String): Result

  suspend fun doPost(url: String, data: ByteArray, type: String): Result

  class Result(val rc: Int, val content: String)
}
