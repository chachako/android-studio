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

import androidx.lifecycle.viewModelScope
import com.google.test.inspectors.Logger
import com.google.test.inspectors.grpc.custom.CustomRequest
import com.google.test.inspectors.grpc.json.JsonRequest
import com.google.test.inspectors.grpc.proto.protoRequest
import com.google.test.inspectors.grpc.xml.XmlRequest
import com.google.test.inspectors.network.grpc.GrpcClient
import com.google.test.inspectors.settings.db.SettingsDao
import com.google.test.inspectors.ui.scafold.AppScaffoldViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltViewModel
internal class NetworkViewModel @Inject constructor(private val settingsDao: SettingsDao) :
  AppScaffoldViewModel(), NetworkScreenActions {

  private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    setSnack("Error: ${throwable.message}")
    Logger.error("Error: ${throwable.message}", throwable)
  }

  private val scope = CoroutineScope(viewModelScope.coroutineContext + exceptionHandler)

  override fun doGet(client: HttpClient, url: String, encoding: String) {
    scope.launch {
      val result = client.doGet(url, encoding)
      setSnack("${client.name} Result: ${result.rc}")
    }
  }

  override fun doPost(client: HttpClient, url: String, data: ByteArray, type: String) {
    scope.launch {
      val result = client.doPost(url, data, type)
      setSnack("${client.name} Result: ${result.rc}")
    }
  }

  override fun doPostOneShot(client: OkHttp3, url: String, data: ByteArray, type: String) {
    scope.launch {
      val result = client.doPostOneShot(url, data, type)
      setSnack("${client.name} Result: ${result.rc}")
    }
  }

  override fun doPostDuplex(client: OkHttp3, url: String, data: ByteArray, type: String) {
    scope.launch {
      val result = client.doPostDuplex(url, data, type)
      setSnack("${client.name} Result: ${result.rc}")
    }
  }

  override fun doProtoGrpc(name: String) {
    scope.launch {
      val response = newGrpcClient().use { it.doProtoGrpc(protoRequest { this.name = name }) }
      setSnack(response.message)
    }
  }

  override fun doJsonGrpc(name: String) {
    scope.launch {
      val response = newGrpcClient().use { it.doJsonGrpc(JsonRequest(name)) }
      setSnack(response.message)
    }
  }

  override fun doXmlGrpc(name: String) {
    scope.launch {
      val response = newGrpcClient().use { it.doXmlGrpc(XmlRequest(name)) }
      setSnack(response.message)
    }
  }

  override fun doCustomGrpc(name: String) {
    scope.launch {
      val response = newGrpcClient().use { it.doCustomGrpc(CustomRequest(name)) }
      setSnack(response.message)
    }
  }

  private suspend fun newGrpcClient() =
    GrpcClient(settingsDao.getHost(), settingsDao.getPort(), settingsDao.getChannelBuilderType())
}
