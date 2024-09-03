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
package com.example.backuprestore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.backuprestore.db.User
import com.example.backuprestore.ui.theme.BackupRestoreTheme

internal class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val viewModel = MainViewModel(application)
    setContent {
      BackupRestoreTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          val users by viewModel.users.collectAsStateWithLifecycle()
          val cloudUsers by viewModel.cloudUsers.collectAsStateWithLifecycle()
          val d2dUsers by viewModel.d2dUsers.collectAsStateWithLifecycle()
          App(
            users,
            { name: String -> viewModel.addUser(name) },
            cloudUsers,
            { name: String -> viewModel.addCloudUser(name) },
            d2dUsers,
            { name: String -> viewModel.addD2dUser(name) },
            modifier = Modifier.padding(innerPadding),
          )
        }
      }
    }
  }

  @Composable
  private fun App(
    users: List<User>,
    onAddUser: (String) -> Unit,
    cloudUsers: List<User>,
    onAddCloudUser: (String) -> Unit,
    d2dUsers: List<User>,
    onAddD2dUser: (String) -> Unit,
    modifier: Modifier = Modifier,
  ) {
    Column(modifier = modifier) {
      Text("Num users (Global): ${users.size}")
      TextButton(onClick = { onAddUser("Foo") }) { Text("Add global user") }
      Text("Num users (Cloud): ${cloudUsers.size}")
      TextButton(onClick = { onAddCloudUser("Foo") }) { Text("Add cloud user") }
      Text("Num users (D2D): ${d2dUsers.size}")
      TextButton(onClick = { onAddD2dUser("Foo") }) { Text("Add d2d user") }
    }
  }
}
