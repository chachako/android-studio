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

import android.app.job.JobParameters
import android.app.job.JobService
import androidx.work.Configuration
import com.google.test.inspectors.Logger
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
