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
    configure: HttpURLConnection.() -> HttpURLConnection
  ): Result {
    return withContext(Dispatchers.IO) {
      val connection = URL(url).openConnection() as HttpURLConnection
      connection.setRequestProperty("Accept-Encoding", "gzip")
      connection.configure()

      val rc = connection.responseCode
      Log.i("NetworkApp", "Response: $rc")

      val content =
        when {
          rc in 200 ..< 300 -> connection.inputStream.reader().use { it.readText() }
          else -> connection.errorStream.reader().use { it.readText() }
        }
      return@withContext Result(rc, content)
    }
  }
}
