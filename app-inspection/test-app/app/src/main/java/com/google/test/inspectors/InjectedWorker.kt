package com.google.test.inspectors

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
internal class InjectedWorker
@AssistedInject
constructor(@Assisted context: Context, @Assisted private val params: WorkerParameters) :
  Worker(context, params) {

  override fun doWork(): Result {
    Logger.info("doWork1")
    return Result.success(
      Data.Builder().putString(MESSAGE_KEY, "Hello from InjectedWorker ${params.id}").build()
    )
  }

  companion object {

    const val MESSAGE_KEY = "message"
  }
}
