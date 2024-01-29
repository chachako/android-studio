package com.google.test.inspectors

import android.util.Log
import com.google.test.inspectors.HttpClient.Result
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object JavaNet : AbstractHttpClient<HttpURLConnection>() {

  override val name: String
    get() = "Java Net"

  override fun HttpURLConnection.configurePost(data: ByteArray, type: String): HttpURLConnection {
    setRequestProperty("Content-Type", type)
    doOutput = true
    outputStream.use { it.write(data) }
    return this
  }

  override suspend fun doRequest(
    url: String,
    configure: HttpURLConnection.() -> HttpURLConnection,
  ): Result {
    return withContext(Dispatchers.IO) {
      val connection = URL(url).openConnection() as HttpURLConnection
      connection.setRequestProperty("Accept-Encoding", "gzip")
      connection.configure()

      Log.i("NetworkApp", "Content Encoding: ${connection.contentEncoding}")
      val content = connection.inputStream.reader().use { it.readText() }

      val rc = connection.responseCode
      Log.i("NetworkApp", "Response: $rc")

      return@withContext Result(rc, content)
    }
  }
}
