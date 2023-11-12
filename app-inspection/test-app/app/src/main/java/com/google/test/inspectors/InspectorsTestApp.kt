package com.google.test.inspectors

import android.app.Application
import com.google.test.inspectors.grpc.GrpcServer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
internal class InspectorsTestApp : Application() {
  private val grcServer = GrpcServer()

  override fun onCreate() {
    super.onCreate()
    grcServer.start()
  }

  override fun onTerminate() {
    super.onTerminate()
    grcServer.shutdown()
  }
}
