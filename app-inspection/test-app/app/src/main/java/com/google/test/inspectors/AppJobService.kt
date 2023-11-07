package com.google.test.inspectors

import android.app.job.JobParameters
import android.app.job.JobService
import androidx.work.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class AppJobService : JobService() {

  private val job = SupervisorJob()
  private val scope = CoroutineScope(Dispatchers.IO + job)

  init {
    Configuration.Builder().setJobSchedulerJobIdRange(0, 1000)
  }

  override fun onStartJob(parameters: JobParameters): Boolean {
    Logger.info("onStartJob")

    scope.launch {
      Logger.info("Background work")

      jobFinished(parameters, false)
    }

    return true // Our task will run in background, we will take care of notifying the finish.
  }

  override fun onStopJob(parameters: JobParameters): Boolean {
    Logger.info("onStartJob")
    return true
  }

  override fun onDestroy() {
    super.onDestroy()
    job.cancel()
  }
}
