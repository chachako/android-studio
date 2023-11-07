package com.google.test.inspectors

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object NetworkJavaNet {

  suspend fun doGet(url: String, onResponse: suspend (Int, String?) -> Unit) {
    doRequest(url, onResponse) {}
  }

  suspend fun doPost(
    url: String,
    data: ByteArray,
    type: String,
    onResponse: suspend (Int, String?) -> Unit
  ) {
    doRequest(url, onResponse) {
      setRequestProperty("Accept-Encoding", "gzip")
      setRequestProperty("Content-Type", type)
      doOutput = true
      outputStream.use { it.write(data) }
    }
  }

  private suspend fun doRequest(
    url: String,
    onResponse: suspend (Int, String?) -> Unit,
    configure: HttpURLConnection.() -> Unit
  ) =
    withContext(Dispatchers.IO) {
      val connection = URL(url).openConnection() as HttpURLConnection
      connection.setRequestProperty("Accept-Encoding", "gzip")
      connection.configure()

      val rc = connection.responseCode
      Log.i("NetworkApp", "Response: $rc")

      val content =
        when (rc) {
          200 -> connection.inputStream.reader().use { it.readText() }
          else -> null
        }
      onResponse(rc, content)
    }
}
