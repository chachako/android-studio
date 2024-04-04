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

import android.util.Log
import com.google.test.inspectors.network.HttpClient.Result
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
