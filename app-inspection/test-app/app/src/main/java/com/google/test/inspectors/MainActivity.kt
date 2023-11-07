package com.google.test.inspectors

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration.Short
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo.State.SUCCEEDED
import androidx.work.WorkManager
import com.google.test.inspectors.ui.theme.InspectorsTestAppTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch

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
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(it).fillMaxWidth(),
      ) {
        SimpleTextButton("Start Job") {
          startJob()
          scope.launch {
            snackbarHostState.showSnackbar("Started job ${jobId.get()}", duration = Short)
          }
        }
        SimpleTextButton("Start Work") {
          startWork { scope.launch { snackbarHostState.showSnackbar(it, duration = Short) } }
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
