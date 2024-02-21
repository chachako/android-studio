package com.google.test.inspectors.main

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import com.google.test.inspectors.AlarmReceiver
import com.google.test.inspectors.AppJobService
import com.google.test.inspectors.HttpClient
import com.google.test.inspectors.InjectedWorker
import com.google.test.inspectors.Logger
import com.google.test.inspectors.ManualWorker
import com.google.test.inspectors.db.SettingsDao
import com.google.test.inspectors.grpc.GrpcClient
import com.google.test.inspectors.grpc.custom.CustomRequest
import com.google.test.inspectors.grpc.json.JsonRequest
import com.google.test.inspectors.grpc.proto.protoRequest
import com.google.test.inspectors.grpc.xml.XmlRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val jobId = AtomicInteger(1)
private const val StopTimeoutMillis: Long = 5000

/**
 * A [SharingStarted] meant to be used with a [kotlinx.coroutines.flow.StateFlow] to expose data to
 * the UI.
 *
 * When the UI stops observing, upstream flows stay active for some time to allow the system to come
 * back from a short-lived configuration change (such as rotations). If the UI stops observing for
 * longer, the cache is kept but the upstream flows are stopped. When the UI comes back, the latest
 * value is replayed and the upstream flows are executed again. This is done to save resources when
 * the app is in the background but let users switch between apps quickly.
 */
private val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(StopTimeoutMillis)

@HiltViewModel
internal class MainViewModel
@Inject
constructor(private val application: Application, private val settingsDao: SettingsDao) :
  ViewModel(), MainScreenActions {

  private val snackFlow: MutableStateFlow<String?> = MutableStateFlow(null)
  val snackState: StateFlow<String?> = snackFlow.stateIn(viewModelScope, WhileUiSubscribed, null)

  private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    setSnack("Error: ${throwable.message}")
    Logger.error("Error: ${throwable.message}", throwable)
  }

  private val scope = CoroutineScope(viewModelScope.coroutineContext + exceptionHandler)

  override fun startJob() {
    val id = jobId.getAndIncrement()
    val job = JobInfo.Builder(id, ComponentName(application, AppJobService::class.java)).build()
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

  override fun doGet(client: HttpClient, url: String) {
    scope.launch {
      val result = client.doGet(url)
      setSnack("${client.name} Result: ${result.rc}")
    }
  }

  override fun doPost(client: HttpClient, url: String, data: ByteArray, type: String) {
    scope.launch {
      val result = client.doPost(url, data, type)
      setSnack("${client.name} Result: ${result.rc}")
    }
  }

  override fun doProtoGrpc(name: String) {
    scope.launch {
      val response = newGrpcClient().use { it.doProtoGrpc(protoRequest { this.name = name }) }
      setSnack(response.message)
    }
  }

  override fun doJsonGrpc(name: String) {
    scope.launch {
      val response = newGrpcClient().use { it.doJsonGrpc(JsonRequest(name)) }
      setSnack(response.message)
    }
  }

  override fun doXmlGrpc(name: String) {
    scope.launch {
      val response = newGrpcClient().use { it.doXmlGrpc(XmlRequest(name)) }
      setSnack(response.message)
    }
  }

  override fun doCustomGrpc(name: String) {
    scope.launch {
      val response = newGrpcClient().use { it.doCustomGrpc(CustomRequest(name)) }
      setSnack(response.message)
    }
  }

  private suspend fun newGrpcClient() =
    GrpcClient(settingsDao.getHost(), settingsDao.getPort(), settingsDao.getChannelBuilderType())

  private fun setSnack(text: String) {
    snackFlow.value =
      when (text) {
        snackState.value -> "$text (repeated)"
        else -> text
      }
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
}
