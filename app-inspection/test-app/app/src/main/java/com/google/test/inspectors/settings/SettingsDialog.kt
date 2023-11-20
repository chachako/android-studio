/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.test.inspectors.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.test.inspectors.ui.theme.InspectorsTestAppTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(FlowPreview::class)
@Composable
internal fun SettingsDialog(
  onDismiss: () -> Unit,
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  var host by remember { mutableStateOf("") }
  LaunchedEffect(key1 = Unit) {
    host = viewModel.getHost()
    snapshotFlow { host }
      .debounce(500L)
      .distinctUntilChanged()
      .onEach { viewModel.setHost(it) }
      .launchIn(this)
  }
  var port by remember { mutableIntStateOf(SettingsViewModel.DEFAULT_PORT) }
  LaunchedEffect(key1 = Unit) {
    port = viewModel.getPort()
    snapshotFlow { port }
      .debounce(500L)
      .distinctUntilChanged()
      .onEach { viewModel.setPort(it) }
      .launchIn(this)
  }

  SettingsDialog(
    host = host,
    port = port,
    onHostUpdated = { host = it },
    onPortUpdated = { port = it },
    onDismiss = onDismiss,
  )
}

@Composable
private fun SettingsDialog(
  host: String,
  port: Int,
  onHostUpdated: (String) -> Unit,
  onPortUpdated: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  val configuration = LocalConfiguration.current

  AlertDialog(
    properties = DialogProperties(usePlatformDefaultWidth = false),
    modifier = Modifier.widthIn(max = configuration.screenWidthDp.dp - 80.dp),
    onDismissRequest = { onDismiss() },
    title = {
      Text(
        text = "Settings",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Divider()
      Column(Modifier.verticalScroll(rememberScrollState())) {
        SettingsPanel(
          host = host,
          port = port,
          onHostUpdated = onHostUpdated,
          onPortUpdated = onPortUpdated,
        )
      }
    },
    confirmButton = {
      Text(
        text = "OK",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 8.dp).clickable { onDismiss() },
      )
    },
  )
}

// [ColumnScope] is used for using the [ColumnScope.AnimatedVisibility] extension overload
// composable.
@Composable
private fun SettingsPanel(
  host: String,
  port: Int,
  onHostUpdated: (String) -> Unit,
  onPortUpdated: (Int) -> Unit,
) {
  OutlinedTextField(
    value = host,
    onValueChange = onHostUpdated,
    label = { Text(text = "Host") },
    modifier = Modifier.fillMaxWidth(),
  )
  OutlinedTextField(
    value = port.toString(),
    onValueChange = { onPortUpdated(it.toIntOrNull() ?: port) },
    label = { Text(text = "Port") },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = Modifier.fillMaxWidth(),
  )
}

@Preview
@Composable
private fun PreviewSettingsDialog() {
  InspectorsTestAppTheme {
    SettingsDialog(
      host = "",
      port = 12345,
      onHostUpdated = {},
      onPortUpdated = {},
      onDismiss = {},
    )
  }
}
