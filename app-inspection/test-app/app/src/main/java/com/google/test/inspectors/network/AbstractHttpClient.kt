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
