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

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.PowerManager.PARTIAL_WAKE_LOCK
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import com.google.test.inspectors.Logger
import com.google.test.inspectors.ui.scafold.AppScaffoldViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val jobId = AtomicInteger(1)

@HiltViewModel
internal class BackgroundViewModel @Inject constructor(private val application: Application) :
  AppScaffoldViewModel(), BackgroundScreenActions {

  private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    setSnack("Error: ${throwable.message}")
    Logger.error("Error: ${throwable.message}", throwable)
  }

  private val scope = CoroutineScope(viewModelScope.coroutineContext + exceptionHandler)
  private val wakeLock =
    application
      .getSystemService(PowerManager::class.java)
      .newWakeLock(PARTIAL_WAKE_LOCK, "com.google.test.inspectors:test")

  override fun startJob() {
    val id = jobId.getAndIncrement()
    val job =
      JobInfo.Builder(id, ComponentName(application, AppJobService::class.java))
        .safeSetRequiresBatteryNotLow(true)
        .build()
    val jobScheduler = application.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    jobScheduler.schedule(job)

    setSnack("Started job $jobId")
  }

  override fun startWork() {
    startWork(InjectedWorker::class.java)
    startWork(ManualWorker::class.java)
  }

  private fun startWork(worker: Class<out Worker>) {
    val request = OneTimeWorkRequest.Builder(worker).build()
    val workManager = WorkManager.getInstance(application)
    val work: LiveData<WorkInfo> = workManager.getWorkInfoByIdLiveData(request.id)

    scope.launch {
      work.asFlow().collect {
        Logger.info("State of ${request.id}: ${it.state}")
        if (it.state == WorkInfo.State.SUCCEEDED) {
          setSnack(it.outputData.getString(InjectedWorker.MESSAGE_KEY) ?: "no-message")
        }
      }
    }
    workManager.enqueue(request)
  }

  override fun doSetAlarm() {
    val alarmManager = application.getSystemService<AlarmManager>() ?: throw IllegalStateException()
    val intent =
      Intent(application, AlarmReceiver::class.java)
        .setAction("Alarm")
        .setDataAndType(Uri.parse("http://google.com"), "Some type")
        .addCategory("category1")
        .addCategory("category2")
        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        .putExtra("INT_EXTRA", 1)
        .putExtra("STRING_EXTRA", "Foo")
        .putExtra(
          "BUNDLE_EXTRA",
          Bundle().apply {
            putInt("INNER_INT_EXTRA", 1)
            putString("INNER_STRING_EXTRA", "Foo")
          },
        )
    AlarmManagerCompat.setExactAndAllowWhileIdle(
      alarmManager,
      AlarmManager.RTC,
      System.currentTimeMillis() + 3.seconds.inWholeMilliseconds,
      PendingIntent.getBroadcast(
        application,
        1,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      ),
    )
  }

  override fun doAcquireWakeLock() {
    wakeLock.acquire(5000)
  }

  override fun doReleaseWakeLock() {
    wakeLock.release()
  }
}

private fun JobInfo.Builder.safeSetRequiresBatteryNotLow(value: Boolean): JobInfo.Builder {
  if (Build.VERSION.SDK_INT >= 26) {
    setRequiresBatteryNotLow(value)
  }
  return this
}
