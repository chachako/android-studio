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

import android.app.ActivityThread.ReceiverData
import android.app.AlarmManager
import android.app.AlarmManager.RTC_WAKEUP
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.AlarmSet
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.PendingIntent.Type.ACTIVITY
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.PendingIntent.Type.BROADCAST
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.PendingIntent.Type.SERVICE
import com.android.tools.appinspection.ComponentNameProto
import com.android.tools.appinspection.IntentProto
import com.android.tools.appinspection.IntentProtoBuilder
import com.android.tools.appinspection.PendingIntentHandler
import com.android.tools.appinspection.PendingIntentProto
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

private const val PENDING_INTENT_REQUEST_CODE = 3
private const val PENDING_INTENT_FLAGS = 0x1234

@RunWith(RobolectricTestRunner::class)
@Config(
  manifest = Config.NONE,
  minSdk = Build.VERSION_CODES.O,
  maxSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
)
class AlarmHandlerTest {

  @get:Rule val inspectorRule = BackgroundTaskInspectorRule()

  private val context
    get() = RuntimeEnvironment.getApplication()

  private class TestListener : AlarmManager.OnAlarmListener {

    override fun onAlarm() {}
  }

  private val pendingIntentHandler
    get() = inspectorRule.inspector.pendingIntentHandler

  @Test
  fun alarmSetWithIntent() {
    val alarmHandler = inspectorRule.inspector.alarmHandler
    val intent =
      intent(
        action = "action",
        data = Uri.parse("http://google.com"),
        type = null,
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
    val pendingIntent =
      PendingIntent.getActivity(context, PENDING_INTENT_REQUEST_CODE, intent, PENDING_INTENT_FLAGS)
    pendingIntentHandler.handlePendingIntent(pendingIntent)
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
                .setCreatorPackage("org.robolectric.default")
                .setType(ACTIVITY)
                .setRequestCode(PENDING_INTENT_REQUEST_CODE)
                .setIntent(
                  IntentProto.newBuilder()
                    .setAction("action")
                    .setData("http://google.com")
                    .maybeSetIdentifier("identifier")
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
  fun alarmSetWithIntent_withType() {
    val alarmHandler = inspectorRule.inspector.alarmHandler
    val intent =
      intent(
        action = "action",
        data = null,
        type = "type",
        identifier = null,
        `package` = null,
        component = null,
        categories = emptySet(),
        extras = null,
        flags = 0,
      )
    val pendingIntent =
      PendingIntent.getActivity(context, PENDING_INTENT_REQUEST_CODE, intent, PENDING_INTENT_FLAGS)
    pendingIntentHandler.handlePendingIntent(pendingIntent)
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
      assertThat(alarmSet.operation.intent)
        .isEqualTo(IntentProto.newBuilder().setAction("action").setType("type").build())
    }
  }

  @Test
  fun alarmSetWithIntent_nullValues() {
    val alarmHandler = inspectorRule.inspector.alarmHandler
    val intent =
      intent(
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
    val pendingIntent =
      PendingIntent.getActivity(context, PENDING_INTENT_REQUEST_CODE, intent, PENDING_INTENT_FLAGS)
    pendingIntentHandler.handlePendingIntent(pendingIntent)
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
                .setCreatorPackage("org.robolectric.default")
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
    val operation = pendingIntent()
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
    val operation = pendingIntent()
    alarmHandler.onAlarmSet(RTC_WAKEUP, 2, 3, 4, operation, null, null)
    alarmHandler.onAlarmCancelled(operation)
    inspectorRule.connection.consume { assertThat(hasAlarmCancelled()).isTrue() }
  }

  @Test
  fun alarmFiredWithIntent() {
    val alarmHandler = inspectorRule.inspector.alarmHandler
    val intent = Intent()
    val operation =
      PendingIntent.getActivity(context, PENDING_INTENT_REQUEST_CODE, intent, PENDING_INTENT_FLAGS)

    pendingIntentHandler.handlePendingIntent(operation)

    alarmHandler.onAlarmSet(RTC_WAKEUP, 2, 3, 4, operation, null, null)

    val data = ReceiverData(intent, 0, "", Bundle(), true, true, null, 0)
    pendingIntentHandler.onReceiverDataCreated(data)
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

  private fun PendingIntentHandler.handlePendingIntent(pendingIntent: PendingIntent) {
    val shadow = Shadows.shadowOf(pendingIntent)
    val type =
      when {
        shadow.isActivity -> ACTIVITY
        shadow.isBroadcast -> BROADCAST
        shadow.isService -> SERVICE
        else -> throw IllegalStateException()
      }
    onIntentCapturedEntry(type, shadow.requestCode, shadow.savedIntent, shadow.flags)
    onIntentCapturedExit(pendingIntent)
  }
}

// Actual values of requestCode, intent & flags are not used by the test
private fun pendingIntent(): PendingIntent =
  PendingIntent.getActivity(RuntimeEnvironment.getApplication(), 0, Intent(), 0)

@Suppress("SameParameterValue")
private fun intent(
  action: String? = "action",
  data: Uri? = Uri.EMPTY,
  type: String? = "type",
  identifier: String? = "identifier",
  `package`: String? = "package",
  component: ComponentName? = ComponentName("package", "classname"),
  categories: Set<String>? = setOf("cat1", "cat2"),
  flags: Int = 0x121,
  extras: Bundle? = Bundle(),
): Intent {
  if (data != null && type != null) {
    throw IllegalArgumentException("Only one of `data` and `type` can be set")
  }
  val intent = Intent(action, data)
  if (type != null) {
    intent.type = type
  }
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    intent.identifier = identifier
  }
  intent.`package` = `package`
  intent.component = component
  categories?.forEach { intent.addCategory(it) }
  intent.flags = flags
  if (extras != null) {
    intent.putExtras(extras)
  }
  return intent
}

private fun IntentProtoBuilder.maybeSetIdentifier(identifier: String): IntentProtoBuilder {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    setIdentifier(identifier)
  }
  return this
}
