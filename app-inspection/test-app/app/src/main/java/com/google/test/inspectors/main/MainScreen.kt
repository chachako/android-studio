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

package com.google.test.inspectors.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.test.inspectors.network.JavaNet
import com.google.test.inspectors.network.OkHttp2
import com.google.test.inspectors.network.OkHttp3
import com.google.test.inspectors.settings.SettingsDialog
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
fun MainScreen() {
  val viewModel: MainViewModel = hiltViewModel()
  var showSettingsDialog by rememberSaveable { mutableStateOf(false) }

  AppScaffold(viewModel, topBar = { TopBar(onClickSettings = { showSettingsDialog = true }) }) {
    Box(modifier = Modifier.padding(it)) {
      MainScreen(viewModel)
      if (showSettingsDialog) {
        SettingsDialog(onDismiss = { showSettingsDialog = false })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onClickSettings: () -> Unit) {

  TopAppBar(
    title = { Text(text = "Network Inspectors") },
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

@Composable
private fun MainScreen(actions: MainScreenActions) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(8.dp),
  ) {
    button("Start Job") { actions.startJob() }
    button("Start Work") { actions.startWork() }
    listOf(JavaNet, OkHttp2(), OkHttp3()).forEach { client ->
      button("${client.name} GET") { actions.doGet(client, "https://reqres.in/api/users") }
      button("${client.name} POST") {
        actions.doPost(client, "https://reqres.in/api/users", POST_DATA, JSON_TYPE)
      }
    }
    button("gRPC Proto") { actions.doProtoGrpc("Proto") }
    button("gRPC Json") { actions.doJsonGrpc("Json") }
    button("gRPC XML") { actions.doXmlGrpc("XML") }
    button("gRPC Custom") { actions.doCustomGrpc("Custom") }
    button("Set Alarm") { actions.doSetAlarm() }
    button("Acquire Wake Lock") { actions.doAcquireWakeLock() }
    button("Release Wake Lock") { actions.doReleaseWakeLock() }
  }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
  InspectorsTestAppTheme { MainScreen(object : MainScreenActions {}) }
}

@Preview(showBackground = true)
@Composable
fun TopBarPreview() {
  InspectorsTestAppTheme { TopBar { /*TODO*/} }
}
