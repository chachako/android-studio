package com.google.test.inspectors

import android.content.Context
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters

internal class AppWorker(context: Context, private val params: WorkerParameters) :
  Worker(context, params) {

  override fun doWork(): Result {
    Logger.info("doWork")
    return Result.success(
      Data.Builder().putString(MESSAGE_KEY, "Hello from Worker ${params.id}").build()
    )
  }

  companion object {

    const val MESSAGE_KEY = "message"
  }
}
