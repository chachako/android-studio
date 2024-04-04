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

package com.google.test.inspectors.background

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.test.inspectors.ui.ButtonGrid
import com.google.test.inspectors.ui.button
import com.google.test.inspectors.ui.scafold.AppScaffold
import com.google.test.inspectors.ui.theme.InspectorsTestAppTheme

@Composable
fun BackgroundScreen() {
  val viewModel: BackgroundViewModel = hiltViewModel()
  AppScaffold(viewModel, topBar = { TopBar() }) { BackgroundScreen(viewModel) }
}

@Composable
private fun BackgroundScreen(actions: BackgroundScreenActions) {
  ButtonGrid {
    button("Start Job") { actions.startJob() }
    button("Start Work") { actions.startWork() }
    button("Acquire Wake Lock") { actions.doAcquireWakeLock() }
    button("Release Wake Lock") { actions.doReleaseWakeLock() }
    button("Set Alarm") { actions.doSetAlarm() }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar() {
  TopAppBar(title = { Text(text = "Background Actions") })
}

@Preview(showBackground = true)
@Composable
fun BackgroundPreview() {
  InspectorsTestAppTheme {
    Scaffold(topBar = { TopBar() }) {
      Box(modifier = Modifier.padding(it)) { BackgroundScreen(object : BackgroundScreenActions {}) }
    }
  }
}
