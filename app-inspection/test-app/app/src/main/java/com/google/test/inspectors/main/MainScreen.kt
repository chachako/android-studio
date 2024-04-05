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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import com.google.test.inspectors.ui.ButtonGrid
import com.google.test.inspectors.ui.button
import com.google.test.inspectors.ui.navigation.Destination
import com.google.test.inspectors.ui.theme.InspectorsTestAppTheme

@Composable
fun MainScreen(navigator: NavHostController) {
  MainScreen { navigator.navigate(it) }
}

@Composable
private fun MainScreen(navigate: (String) -> Unit = {}) {
  Scaffold(topBar = { TopBar() }) { padding ->
    Box(modifier = Modifier.padding(padding)) {
      ButtonGrid { Destination.entries.forEach { button(it.title) { navigate(it.name) } } }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar() {
  TopAppBar(title = { Text(text = "Inspectors Test App") })
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
  InspectorsTestAppTheme { MainScreen() }
}
