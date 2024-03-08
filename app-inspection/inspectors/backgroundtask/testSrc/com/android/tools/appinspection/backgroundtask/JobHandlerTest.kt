/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.appinspection.backgroundtask

import android.app.job.JobInfo
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.JobInfo.NetworkType
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private typealias JobInfoProto = BackgroundTaskInspectorProtocol.JobInfo

private typealias JobInfoProtoBuilder = BackgroundTaskInspectorProtocol.JobInfo.Builder

@RunWith(RobolectricTestRunner::class)
@Config(
  manifest = Config.NONE,
  minSdk = Build.VERSION_CODES.O,
  maxSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
)
class JobHandlerTest {

  @get:Rule val inspectorRule = BackgroundTaskInspectorRule()

  private val fakeParameters =
    JobParametersWrapper(
      1,
      PersistableBundle().put("EXTRA", "value"),
      Bundle().put("TransientExtra", "value"),
      true,
      arrayOf(),
      arrayOf("authority"),
    )

  @Test
  fun jobScheduled_periodic() {
    val jobHandler = inspectorRule.inspector.jobHandler
    val job =
      JobInfo.Builder(1, ComponentName("com.package.name", "TestClass"))
        .setBackoffCriteria(20000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
        .setPeriodic(1000000, 500000)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
        .setTriggerContentMaxDelay(105)
        .setTriggerContentUpdateDelay(106)
        .setRequiresCharging(true)
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .setExtras(PersistableBundle().put("EXTRA", "value"))
        .build()
    jobHandler.onScheduleJobEntry(job)
    jobHandler.onScheduleJobExit(0)
    inspectorRule.connection.consume {
      assertThat(jobScheduled.job)
        .isEqualTo(
          JobInfoProto.newBuilder()
            .setJobId(1)
            .setServiceName("TestClass")
            .setBackoffPolicy(BackoffPolicy.BACKOFF_POLICY_EXPONENTIAL)
            .setInitialBackoffMs(20000)
            .setIsPeriodic(true)
            .setFlexMs(500000)
            .setIntervalMs(1000000)
            .setNetworkType(NetworkType.NETWORK_TYPE_NOT_ROAMING)
            .setTriggerContentMaxDelay(105)
            .setTriggerContentUpdateDelay(106)
            .setIsRequireBatteryNotLow(true)
            .setIsRequireCharging(true)
            .setIsRequireStorageNotLow(true)
            .setExtras("PersistableBundle[{EXTRA=value}]")
            .setTransientExtras("Bundle[{}]")
            .build()
        )
    }
  }

  @Test
  fun jobScheduled_single() {
    val jobHandler = inspectorRule.inspector.jobHandler
    val job =
      JobInfo.Builder(1, ComponentName("com.package.name", "TestClass"))
        .setMinimumLatency(103)
        .setOverrideDeadline(104)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
        .setTransientExtras(Bundle().put("TransientExtras", "value"))
        .addTriggerContentUri(JobInfo.TriggerContentUri(Uri.parse("http://foo"), 0))
        .addTriggerContentUri(JobInfo.TriggerContentUri(Uri.parse("http://bar"), 0))
        .build()
    jobHandler.onScheduleJobEntry(job)
    jobHandler.onScheduleJobExit(0)
    inspectorRule.connection.consume {
      assertThat(jobScheduled.job)
        .isEqualTo(
          JobInfoProto.newBuilder()
            .setJobId(1)
            .setServiceName("TestClass")
            .setBackoffPolicy(BackoffPolicy.BACKOFF_POLICY_EXPONENTIAL)
            .setInitialBackoffMs(30000) // default value
            .setDefaultFlexMsDefault(300000)
            .setDefaultIntervalMs(900000)
            .setMinLatencyMs(103)
            .setMaxExecutionDelayMs(104)
            .setNetworkType(NetworkType.NETWORK_TYPE_UNMETERED)
            .addTriggerContentUris("http://foo")
            .addTriggerContentUris("http://bar")
            .setTriggerContentMaxDelay(-1) // default value
            .setTriggerContentUpdateDelay(-1) // default value
            .setExtras("PersistableBundle[{}]")
            .setTransientExtras("Bundle[{TransientExtras=value}]")
            .build()
        )
    }
  }

  @Test
  fun jobStarted() {
    val jobHandler = inspectorRule.inspector.jobHandler
    jobHandler.wrapOnStartJob(fakeParameters, true)
    inspectorRule.connection.consume {
      with(jobStarted) {
        with(params) {
          assertThat(jobId).isEqualTo(1)
          assertThat(extras).isEqualTo("PersistableBundle[{EXTRA=value}]")
          assertThat(transientExtras).isEqualTo("Bundle[{TransientExtra=value}]")
          assertThat(isOverrideDeadlineExpired).isTrue()
          assertThat(triggeredContentUrisCount).isEqualTo(0)
          assertThat(triggeredContentAuthoritiesCount).isEqualTo(1)
          assertThat(triggeredContentAuthoritiesList[0]).isEqualTo("authority")
        }
        assertThat(workOngoing).isTrue()
      }
    }
  }

  @Test
  fun jobStopped() {
    val jobHandler = inspectorRule.inspector.jobHandler
    jobHandler.wrapOnStopJob(fakeParameters, true)
    inspectorRule.connection.consume {
      with(jobStopped) {
        with(params) {
          assertThat(jobId).isEqualTo(1)
          assertThat(extras).isEqualTo("PersistableBundle[{EXTRA=value}]")
          assertThat(transientExtras).isEqualTo("Bundle[{TransientExtra=value}]")
          assertThat(isOverrideDeadlineExpired).isTrue()
          assertThat(triggeredContentUrisCount).isEqualTo(0)
          assertThat(triggeredContentAuthoritiesCount).isEqualTo(1)
          assertThat(triggeredContentAuthoritiesList[0]).isEqualTo("authority")
        }
        assertThat(reschedule).isTrue()
      }
    }
  }

  @Test
  fun jobFinished() {
    val jobHandler = inspectorRule.inspector.jobHandler
    jobHandler.wrapJobFinished(fakeParameters, true)
    inspectorRule.connection.consume {
      with(jobFinished) {
        with(params) {
          assertThat(jobId).isEqualTo(1)
          assertThat(extras).isEqualTo("PersistableBundle[{EXTRA=value}]")
          assertThat(transientExtras).isEqualTo("Bundle[{TransientExtra=value}]")
          assertThat(isOverrideDeadlineExpired).isTrue()
          assertThat(triggeredContentUrisCount).isEqualTo(0)
          assertThat(triggeredContentAuthoritiesCount).isEqualTo(1)
          assertThat(triggeredContentAuthoritiesList[0]).isEqualTo("authority")
        }
        assertThat(needsReschedule).isTrue()
      }
    }
  }
}

private fun JobInfoProtoBuilder.setDefaultFlexMsDefault(value: Long): JobInfoProtoBuilder {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
    flexMs = value
  }
  return this
}

private fun JobInfoProtoBuilder.setDefaultIntervalMs(value: Long): JobInfoProtoBuilder {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
    intervalMs = value
  }
  return this
}
