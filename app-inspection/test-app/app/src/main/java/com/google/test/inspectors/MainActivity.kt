package com.google.test.inspectors

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration.Short
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo.State.SUCCEEDED
import androidx.work.WorkManager
import com.google.test.inspectors.ui.theme.InspectorsTestAppTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch

private val POST_DATA =
  """
  {
      "name": "morpheus",
      "job": "leader"
  }
""".trimIndent().toByteArray()

private const val JSON_TYPE = "application/json"

class MainActivity : ComponentActivity() {

  private val jobId = AtomicInteger(1)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      InspectorsTestAppTheme {
        // A surface container using the 'background' color from the theme
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          App()
        }
      }
    }
  }

  @Composable
  fun App() {
    val scope = rememberCoroutineScope()

    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = it,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(8.dp)
      ) {
        button("Start Job") {
          startJob()
          scope.launch { snackbarHostState.show("Started job ${jobId.get()}") }
        }
        button("Start Work") { startWork { scope.launch { snackbarHostState.show(it) } } }
        listOf(JavaNet, OkHttp2(), OkHttp3()).forEach { client ->
          button("${client.name} GET") {
            scope.launch {
              val result = client.doGet("https://reqres.in/api/users")
              snackbarHostState.show("Result: ${result.rc}")
            }
          }
          button("${client.name} POST") {
            scope.launch {
              val result = client.doPost("https://reqres.in/api/users", POST_DATA, JSON_TYPE)
              snackbarHostState.show("Result: ${result.rc}")
            }
          }
        }
      }
    }
  }

  private fun startJob() {
    val job =
      JobInfo.Builder(jobId.getAndIncrement(), ComponentName(this, AppJobService::class.java))
        .build()
    val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    jobScheduler.schedule(job)
  }

  private fun startWork(onResult: (String) -> Unit) {
    val request = OneTimeWorkRequest.Builder(AppWorker::class.java).build()
    val workManager = WorkManager.getInstance(this)
    workManager.getWorkInfoByIdLiveData(request.id).observe(this) {
      Logger.info("State of ${request.id}: ${it.state}")
      if (it.state == SUCCEEDED) {
        onResult(it.outputData.getString(AppWorker.MESSAGE_KEY) ?: "")
      }
    }
    workManager.enqueue(request)
  }

  @Preview(showBackground = true)
  @Composable
  fun AppPreview() {
    InspectorsTestAppTheme { App() }
  }
}

private suspend fun SnackbarHostState.show(message: String) {
  currentSnackbarData?.dismiss()
  showSnackbar(message, duration = Short)
}

private fun LazyGridScope.button(text: String, onClick: () -> Unit) {
  item { SimpleTextButton(text, onClick) }
}
