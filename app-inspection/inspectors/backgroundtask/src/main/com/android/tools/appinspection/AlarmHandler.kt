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

package com.android.tools.appinspection

import android.app.AlarmManager
import android.app.AlarmManager.OnAlarmListener
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.inspection.Connection
import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.AlarmCancelled
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.AlarmFired
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.AlarmListener
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.AlarmSet
import com.android.tools.appinspection.BackgroundTaskUtil.sendBackgroundTaskEvent
import com.android.tools.appinspection.common.getStackTrace
import java.util.concurrent.ConcurrentHashMap

typealias PendingIntentProto = BackgroundTaskInspectorProtocol.PendingIntent

typealias IntentProto = BackgroundTaskInspectorProtocol.Intent

typealias IntentProtoBuilder = BackgroundTaskInspectorProtocol.Intent.Builder

typealias ComponentNameProto = BackgroundTaskInspectorProtocol.ComponentName

typealias ComponentNameProtoBuilder = BackgroundTaskInspectorProtocol.ComponentName.Builder

/** A handler class that adds necessary hooks to track alarm related events. */
interface AlarmHandler {

  fun onAlarmSet(
    type: Int,
    triggerMs: Long,
    windowMs: Long,
    intervalMs: Long,
    operation: PendingIntent?,
    listener: OnAlarmListener?,
    listenerTag: String?,
  )

  fun onAlarmCancelled(operation: PendingIntent)

  fun onAlarmCancelled(listener: OnAlarmListener)

  fun onAlarmFired(listener: OnAlarmListener)

  fun onAlarmFired(operation: PendingIntent)
}

private const val TAG = "BackgroundInspector"

class AlarmHandlerImpl(
  private val connection: Connection,
  private val intentRegistry: IntentRegistry,
) : AlarmHandler {

  private val operationIdMap = ConcurrentHashMap<PendingIntent, Long>()
  private val listenerIdMap = ConcurrentHashMap<OnAlarmListener, Long>()

  override fun onAlarmSet(
    type: Int,
    triggerMs: Long,
    windowMs: Long,
    intervalMs: Long,
    operation: PendingIntent?,
    listener: OnAlarmListener?,
    listenerTag: String?,
  ) {
    try {
      val alarmType =
        when (type) {
          AlarmManager.RTC_WAKEUP -> AlarmSet.Type.RTC_WAKEUP
          AlarmManager.RTC -> AlarmSet.Type.RTC
          AlarmManager.ELAPSED_REALTIME_WAKEUP -> AlarmSet.Type.ELAPSED_REALTIME_WAKEUP
          AlarmManager.ELAPSED_REALTIME -> AlarmSet.Type.ELAPSED_REALTIME
          else -> {
            Log.w(TAG, "Invalid Alarm type: $type")
            return
          }
        }
      val builder =
        AlarmSet.newBuilder()
          .setType(alarmType)
          .setTriggerMs(triggerMs)
          .setWindowMs(windowMs)
          .setIntervalMs(intervalMs)

      val taskId =
        when {
          operation != null -> builder.setPendingIntent(operation)
          listener != null -> builder.setListener(listener, listenerTag)
          else ->
            throw IllegalStateException("Invalid alarm: neither operation or listener is set.")
        }

      connection.sendBackgroundTaskEvent(taskId) {
        stacktrace = getStackTrace(1)
        alarmSet = builder.build()
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Error handling Alarm", t)
    }
  }

  override fun onAlarmCancelled(operation: PendingIntent) {
    val taskId = operationIdMap[operation] ?: return
    connection.sendBackgroundTaskEvent(taskId) {
      stacktrace = getStackTrace(1)
      alarmCancelled = AlarmCancelled.getDefaultInstance()
    }
  }

  override fun onAlarmCancelled(listener: OnAlarmListener) {
    val taskId = listenerIdMap[listener] ?: return
    connection.sendBackgroundTaskEvent(taskId) {
      stacktrace = getStackTrace(1)
      alarmCancelled = AlarmCancelled.getDefaultInstance()
    }
  }

  override fun onAlarmFired(listener: OnAlarmListener) {
    val taskId = listenerIdMap[listener] ?: return
    connection.sendBackgroundTaskEvent(taskId) { alarmFired = AlarmFired.getDefaultInstance() }
  }

  override fun onAlarmFired(operation: PendingIntent) {
    val taskId = operationIdMap[operation] ?: return
    connection.sendBackgroundTaskEvent(taskId) { alarmFired = AlarmFired.getDefaultInstance() }
  }

  private fun AlarmSet.Builder.setPendingIntent(pendingIntent: PendingIntent): Long {
    val builder =
      PendingIntentProto.newBuilder()
        .setCreatorPackage(pendingIntent.creatorPackage)
        .setCreatorUid(pendingIntent.creatorUid)
    val info = intentRegistry.getPendingIntentInfo(pendingIntent)
    if (info != null) {
      builder
        .setType(info.type)
        .setRequestCode(info.requestCode)
        .setIntent(info.intent.toProto())
        .setFlags(info.flags)
    }
    setOperation(builder)
    return operationIdMap.getOrPut(pendingIntent) { BackgroundTaskUtil.nextId() }
  }

  private fun AlarmSet.Builder.setListener(listener: OnAlarmListener, listenerTag: String?): Long {
    setListener(AlarmListener.newBuilder().setTag(listenerTag))
    return listenerIdMap.getOrPut(listener) { BackgroundTaskUtil.nextId() }
  }
}

private fun Intent.toProto(): IntentProtoBuilder {
  val builder = IntentProto.newBuilder().setFlags(flags).addAllCategories(categories ?: emptySet())
  action?.let { builder.setAction(it) }
  component?.let { builder.setComponentName(it.toProto()) }
  data?.let { builder.setData(it.toString()) }
  identifier?.let { builder.setIdentifier(it) }
  `package`?.let { builder.setPackage(it) }
  type?.let { builder.setType(it) }
  extras?.let { builder.setExtras(it.toDisplayString()) }
  return builder
}

private fun Bundle.toDisplayString(indentLevel: Int = 0): String {
  return buildString {
      keySet().sorted().forEach { key ->
        @Suppress("DEPRECATION")
        val line =
          when (val value = this@toDisplayString.get(key)) {
            is Bundle -> "$key:\n${value.toDisplayString(indentLevel + 1)}\n"
            else -> "$key: $value\n"
          }
        append(line)
      }
    }
    .trimEnd()
    .prependIndent(" ".repeat(indentLevel * 4))
}

private fun ComponentName.toProto(): ComponentNameProtoBuilder =
  ComponentNameProto.newBuilder().setPackageName(packageName).setClassName(className)
