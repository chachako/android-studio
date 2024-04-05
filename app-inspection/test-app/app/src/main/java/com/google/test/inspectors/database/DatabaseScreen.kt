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

package com.google.test.inspectors.database

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.test.inspectors.ui.ButtonGrid
import com.google.test.inspectors.ui.button
import com.google.test.inspectors.ui.scafold.AppScaffold
import com.google.test.inspectors.ui.theme.InspectorsTestAppTheme

@Composable
fun DatabaseScreen() {
  val viewModel: DatabaseViewModel = hiltViewModel()
  val readWriteDatabaseState by viewModel.readWriteDatabaseState.collectAsStateWithLifecycle()
  val readOnlyDatabaseState by viewModel.readOnlyDatabaseState.collectAsStateWithLifecycle()

  AppScaffold(viewModel, topBar = { TopBar() }) {
    DatabaseScreen(viewModel, readWriteDatabaseState, readOnlyDatabaseState)
  }
}

@Composable
private fun DatabaseScreen(
  actions: DatabaseActions,
  readWriteDatabaseState: Boolean,
  readOnlyDatabaseState: Boolean,
) {
  ButtonGrid {
    when (readWriteDatabaseState) {
      true -> button("Close read-write DB") { actions.doCloseReadWriteDatabase() }
      false -> button("Open read-write DB") { actions.doOpenReadWriteDatabase() }
    }
    if (Build.VERSION.SDK_INT >= 28) {
      when (readOnlyDatabaseState) {
        true -> button("Close read-only DB") { actions.doCloseReadOnlyDatabase() }
        false -> button("Open read-only DB") { actions.doOpenReadOnlyDatabase() }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar() {
  TopAppBar(title = { Text(text = "Database Actions") })
}

@Preview(showBackground = true)
@Composable
fun BackgroundPreview() {
  InspectorsTestAppTheme {
    Scaffold(topBar = { TopBar() }) {
      Box(modifier = Modifier.padding(it)) {
        DatabaseScreen(
          object : DatabaseActions {},
          readWriteDatabaseState = true,
          readOnlyDatabaseState = false,
        )
      }
    }
  }
}
