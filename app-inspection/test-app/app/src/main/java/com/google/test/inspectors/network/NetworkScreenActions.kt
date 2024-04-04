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

internal interface NetworkScreenActions {

  fun doGet(client: HttpClient, url: String) {}

  fun doPost(client: HttpClient, url: String, data: ByteArray, type: String) {}

  fun doProtoGrpc(name: String) {}

  fun doJsonGrpc(name: String) {}

  fun doXmlGrpc(name: String) {}

  fun doCustomGrpc(name: String) {}
}
