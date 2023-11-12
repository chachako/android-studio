package com.google.test.inspectors.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.test.inspectors.JavaNet
import com.google.test.inspectors.OkHttp2
import com.google.test.inspectors.OkHttp3
import com.google.test.inspectors.SimpleTextButton
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
  val message by viewModel.snackState.collectAsStateWithLifecycle()
  val snackbar: SnackbarHostState = remember { SnackbarHostState() }
  Scaffold(snackbarHost = { SnackbarHost(snackbar) }) {
    Box(modifier = Modifier.padding(it))

    MainScreen(viewModel)

    message?.let {
      LaunchedEffect(message) {
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(it)
      }
    }
  }
}

@Composable
private fun MainScreen(actions: MainScreenActions) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(8.dp)
  ) {
    button("Start Job") { actions.startJob() }
    button("Start Work") { actions.startWork() }
    listOf(JavaNet, OkHttp2(), OkHttp3()).forEach { client ->
      button("${client.name} GET") { actions.doGet(client, "https://reqres.in/api/users") }
      button("${client.name} POST") {
        actions.doPost(client, "https://reqres.in/api/users", POST_DATA, JSON_TYPE)
      }
    }
    button("gRPC Proto") { actions.doProtoGrpc("Foo") }
    button("gRPC Json") { actions.doJsonGrpc("Bar") }
  }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
  InspectorsTestAppTheme { MainScreen(object : MainScreenActions {}) }
}

private fun LazyGridScope.button(text: String, onClick: () -> Unit) {
  item { SimpleTextButton(text, onClick) }
}
