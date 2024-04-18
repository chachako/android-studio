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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.test.inspectors.settings.SettingsDialog
import com.google.test.inspectors.ui.ButtonGrid
import com.google.test.inspectors.ui.button
import com.google.test.inspectors.ui.scafold.AppScaffold
import com.google.test.inspectors.ui.theme.InspectorsTestAppTheme

private val POST_DATA =
  """
    {
        "name": "morpheus",
        "job": "leader"
    }
  """
    .trimIndent()
    .toByteArray()

private const val JSON_TYPE = "application/json"

@Composable
fun NetworkScreen() {
  val viewModel: NetworkViewModel = hiltViewModel()
  var showSettingsDialog by rememberSaveable { mutableStateOf(false) }

  AppScaffold(viewModel, topBar = { TopBar(onClickSettings = { showSettingsDialog = true }) }) {
    NetworkScreen(viewModel)
    if (showSettingsDialog) {
      SettingsDialog(onDismiss = { showSettingsDialog = false })
    }
  }
}

@Composable
private fun NetworkScreen(actions: NetworkScreenActions) {
  ButtonGrid {
    val okHttp3 = OkHttp3()
    listOf(JavaNet, OkHttp2(), okHttp3).forEach { client ->
      button("${client.name} GET") { actions.doGet(client, "https://reqres.in/api/users") }
      button("${client.name} POST") {
        actions.doPost(client, "https://reqres.in/api/users", POST_DATA, JSON_TYPE)
      }
    }
    button("OKHTTP3 OneShot") {
      actions.doPostOneShot(okHttp3, "https://reqres.in/api/users", POST_DATA, JSON_TYPE)
    }
    button("OKHTTP3 Duplex") {
      actions.doPostDuplex(okHttp3, "https://reqres.in/api/users", POST_DATA, JSON_TYPE)
    }
    button("gRPC Proto") { actions.doProtoGrpc("Proto") }
    button("gRPC Json") { actions.doJsonGrpc("Json") }
    button("gRPC XML") { actions.doXmlGrpc("XML") }
    button("gRPC Custom") { actions.doCustomGrpc("Custom") }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onClickSettings: () -> Unit) {
  TopAppBar(
    title = { Text(text = "Network Actions") },
    actions = {
      IconButton(onClick = onClickSettings) {
        Icon(
          imageVector = Icons.Rounded.Settings,
          contentDescription = "Settings",
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
    },
  )
}

@Preview(showBackground = true)
@Composable
fun NetworkPreview() {
  InspectorsTestAppTheme {
    Scaffold(topBar = { TopBar {} }) {
      Box(modifier = Modifier.padding(it)) { NetworkScreen(object : NetworkScreenActions {}) }
    }
  }
}
