package com.google.test.inspectors.main

import com.google.test.inspectors.HttpClient

internal interface MainScreenActions {
  fun startJob() {}

  fun startWork() {}

  fun doGet(client: HttpClient, url: String) {}

  fun doPost(client: HttpClient, url: String, data: ByteArray, type: String) {}

  fun doProtoGrpc(name: String) {}

  fun doJsonGrpc(name: String) {}

  fun doXmlGrpc(name: String) {}

  fun doCustomGrpc(name: String) {}

  fun doSetAlarm() {}
}
