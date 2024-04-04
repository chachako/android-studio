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

package com.google.test.inspectors.ui.scafold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun AppScaffold(
  viewModel: AppScaffoldViewModel,
  topBar: @Composable () -> Unit = {},
  content: @Composable (PaddingValues) -> Unit,
) {
  val message by viewModel.snackState.collectAsStateWithLifecycle()
  val snackbar: SnackbarHostState = remember { SnackbarHostState() }

  Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = topBar) {
    Box(modifier = Modifier.padding(it)) {
      content(it)
      message?.let {
        LaunchedEffect(message) {
          snackbar.currentSnackbarData?.dismiss()
          snackbar.showSnackbar(it)
        }
      }
    }
  }
}
