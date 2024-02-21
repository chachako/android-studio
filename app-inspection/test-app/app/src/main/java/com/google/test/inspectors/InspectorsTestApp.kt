package com.google.test.inspectors

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
internal class InspectorsTestApp : Application(), Configuration.Provider {
  @Inject lateinit var workerFactory: HiltWorkerFactory

  override fun getWorkManagerConfiguration(): Configuration {
    return Configuration.Builder()
      .setWorkerFactory(workerFactory)
      .setMinimumLoggingLevel(Log.VERBOSE)
      .build()
  }
}
