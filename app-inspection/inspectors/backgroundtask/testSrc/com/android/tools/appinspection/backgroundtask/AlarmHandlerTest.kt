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

import android.app.ActivityThread
import android.app.AlarmManager
import android.app.AlarmManager.RTC_WAKEUP
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.AlarmSet
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.PendingIntent.Type.ACTIVITY
import com.android.tools.appinspection.ComponentNameProto
import com.android.tools.appinspection.IntentProto
import com.android.tools.appinspection.PendingIntentProto
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

private const val PENDING_INTENT_CREATOR_ID = 50
private const val PENDING_INTENT_PACKAGE = "package"
private const val PENDING_INTENT_REQUEST_CODE = 3
private const val PENDING_INTENT_FLAGS = 0x1234

class AlarmHandlerTest {

  @get:Rule val inspectorRule = BackgroundTaskInspectorRule()

  private class TestListener : AlarmManager.OnAlarmListener

  private val pendingIntentHandler
    get() = inspectorRule.inspector.pendingIntentHandler

  @Test
  fun alarmSetWithIntent() {
    val alarmHandler = inspectorRule.inspector.alarmHandler
    val intent =
      Intent(
        action = "action",
        data = Uri(),
        type = "type",
        identifier = "identifier",
        `package` = "package",
        component = ComponentName("package", "classname"),
        categories = setOf("c1", "c2"),
        flags = 0x321,
        extras =
          Bundle()
            .put("INT_EXTRA", 1)
            .put("STRING_EXTRA", "Foo")
            .put("BUNDLE_EXTRA", Bundle().put("INNER_EXTRA", "inner")),
      )
    val pendingIntent = PendingIntent(PENDING_INTENT_CREATOR_ID, PENDING_INTENT_PACKAGE)
    pendingIntentHandler.onIntentCapturedEntry(
      ACTIVITY,
      PENDING_INTENT_REQUEST_CODE,
      intent,
      PENDING_INTENT_FLAGS,
    )
    pendingIntentHandler.onIntentCapturedExit(pendingIntent)
    alarmHandler.onAlarmSet(
      RTC_WAKEUP,
      triggerMs = 2,
      windowMs = 3,
      intervalMs = 4,
      operation = pendingIntent,
      listener = null,
      listenerTag = null,
    )
    inspectorRule.connection.consume {
      assertThat(alarmSet)
        .isEqualTo(
          AlarmSet.newBuilder()
            .setType(AlarmSet.Type.RTC_WAKEUP)
            .setTriggerMs(2)
            .setWindowMs(3)
            .setIntervalMs(4)
            .setOperation(
              PendingIntentProto.newBuilder()
                .setCreatorPackage(PENDING_INTENT_PACKAGE)
                .setCreatorUid(PENDING_INTENT_CREATOR_ID)
                .setType(ACTIVITY)
                .setRequestCode(PENDING_INTENT_REQUEST_CODE)
                .setIntent(
                  IntentProto.newBuilder()
                    .setAction("action")
                    .setData("uri")
                    .setType("type")
                    .setIdentifier("identifier")
                    .setPackage("package")
                    .setComponentName(
                      ComponentNameProto.newBuilder()
                        .setPackageName("package")
                        .setClassName("classname")
                    )
                    .addAllCategories(listOf("c1", "c2"))
                    .setFlags(0x321)
                    .setExtras(
                      """
                      BUNDLE_EXTRA:
                          INNER_EXTRA: inner
                      INT_EXTRA: 1
                      STRING_EXTRA: Foo
                    """
                        .trimIndent()
                    )
                )
                .setFlags(PENDING_INTENT_FLAGS)
            )
            .build()
        )
    }
  }

  @Test
  fun alarmSetWithIntent_nullValues() {
    val alarmHandler = inspectorRule.inspector.alarmHandler
    val intent =
      Intent(
        action = null,
        data = null,
        type = null,
        identifier = null,
        `package` = null,
        component = null,
        categories = null,
        flags = 0,
        extras = null,
      )
    val pendingIntent = PendingIntent(PENDING_INTENT_CREATOR_ID, PENDING_INTENT_PACKAGE)
    pendingIntentHandler.onIntentCapturedEntry(
      ACTIVITY,
      PENDING_INTENT_REQUEST_CODE,
      intent,
      PENDING_INTENT_FLAGS,
    )
    pendingIntentHandler.onIntentCapturedExit(pendingIntent)
    alarmHandler.onAlarmSet(
      RTC_WAKEUP,
      triggerMs = 2,
      windowMs = 3,
      intervalMs = 4,
      operation = pendingIntent,
      listener = null,
      listenerTag = null,
    )
    inspectorRule.connection.consume {
      assertThat(alarmSet)
        .isEqualTo(
          AlarmSet.newBuilder()
            .setType(AlarmSet.Type.RTC_WAKEUP)
            .setTriggerMs(2)
            .setWindowMs(3)
            .setIntervalMs(4)
            .setOperation(
              PendingIntentProto.newBuilder()
                .setCreatorPackage(PENDING_INTENT_PACKAGE)
                .setCreatorUid(PENDING_INTENT_CREATOR_ID)
                .setType(ACTIVITY)
                .setRequestCode(PENDING_INTENT_REQUEST_CODE)
                .setIntent(IntentProto.newBuilder())
                .setFlags(PENDING_INTENT_FLAGS)
            )
            .build()
        )
    }
  }

  @Test
  fun alarmSet_allTypes() {
    val alarmHandler = inspectorRule.inspector.alarmHandler
    val operation = PendingIntent(PENDING_INTENT_CREATOR_ID, PENDING_INTENT_PACKAGE)
    listOf(
        RTC_WAKEUP to AlarmSet.Type.RTC_WAKEUP,
        AlarmManager.RTC to AlarmSet.Type.RTC,
        AlarmManager.ELAPSED_REALTIME_WAKEUP to AlarmSet.Type.ELAPSED_REALTIME_WAKEUP,
        AlarmManager.ELAPSED_REALTIME to AlarmSet.Type.ELAPSED_REALTIME,
      )
      .forEach { (type, protoType) ->
        alarmHandler.onAlarmSet(type, 2, 3, 4, operation, null, null)
        inspectorRule.connection.consume { assertThat(alarmSet.type).isEqualTo(protoType) }
      }
  }

  @Test
  fun alarmCancelledWithIntent() {
    val alarmHandler = inspectorRule.inspector.alarmHandler
    val operation = PendingIntent(PENDING_INTENT_CREATOR_ID, PENDING_INTENT_PACKAGE)
    alarmHandler.onAlarmSet(RTC_WAKEUP, 2, 3, 4, operation, null, null)
    alarmHandler.onAlarmCancelled(operation)
    inspectorRule.connection.consume { assertThat(hasAlarmCancelled()).isTrue() }
  }

  @Test
  fun alarmFiredWithIntent() {
    val alarmHandler = inspectorRule.inspector.alarmHandler
    val intent = Intent()
    val operation = PendingIntent(PENDING_INTENT_CREATOR_ID, PENDING_INTENT_PACKAGE)

    pendingIntentHandler.onIntentCapturedEntry(
      ACTIVITY,
      PENDING_INTENT_REQUEST_CODE,
      intent,
      PENDING_INTENT_FLAGS,
    )
    pendingIntentHandler.onIntentCapturedExit(operation)

    alarmHandler.onAlarmSet(RTC_WAKEUP, 2, 3, 4, operation, null, null)
    val data = ActivityThread().newReceiverData()
    pendingIntentHandler.onReceiverDataCreated(data)
    data.setIntent(intent)
    pendingIntentHandler.onReceiverDataResult(data)

    inspectorRule.connection.consume { assertThat(hasAlarmFired()).isTrue() }
  }

  @Test
  fun alarmSetWithListener() {
    val alarmHandler = inspectorRule.inspector.alarmHandler
    val listener = TestListener()
    alarmHandler.onAlarmSet(AlarmManager.ELAPSED_REALTIME_WAKEUP, 2, 3, 4, null, listener, "tag")
    inspectorRule.connection.consume {
      with(alarmSet) {
        assertThat(type).isEqualTo(AlarmSet.Type.ELAPSED_REALTIME_WAKEUP)
        assertThat(triggerMs).isEqualTo(2)
        assertThat(windowMs).isEqualTo(3)
        assertThat(intervalMs).isEqualTo(4)
        assertThat(this.listener.tag).isEqualTo("tag")
      }
    }
  }

  @Test
  fun alarmCancelledWithListener() {
    val alarmHandler = inspectorRule.inspector.alarmHandler
    val listener = TestListener()
    alarmHandler.onAlarmSet(AlarmManager.ELAPSED_REALTIME_WAKEUP, 2, 3, 4, null, listener, "tag")
    alarmHandler.onAlarmCancelled(listener)
    inspectorRule.connection.consume { assertThat(hasAlarmCancelled()).isTrue() }
  }

  @Test
  fun alarmFiredWithListener() {
    val alarmHandler = inspectorRule.inspector.alarmHandler
    val listener = TestListener()
    alarmHandler.onAlarmSet(AlarmManager.ELAPSED_REALTIME_WAKEUP, 2, 3, 4, null, listener, "tag")
    alarmHandler.onAlarmFired(listener)
    inspectorRule.connection.consume { assertThat(hasAlarmFired()).isTrue() }
  }
}
