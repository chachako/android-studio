/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.appinspection.network.testing

import android.util.Log
import org.robolectric.shadows.ShadowLog

private val LOG_LEVELS =
  mapOf(
    Log.VERBOSE to "VERBOSE",
    Log.DEBUG to "DEBUG",
    Log.INFO to "INFO",
    Log.WARN to "WARN",
    Log.ERROR to "ERROR",
    Log.ASSERT to "ASSERT",
  )

private fun ShadowLog.LogItem.getLevel() = LOG_LEVELS[type]

internal fun getVisibleLogLines() = ShadowLog.getLogsForTag("Network Inspector").map { it.toLine() }

internal fun getHiddenLogLines() = ShadowLog.getLogsForTag("studio.inspectors").map { it.toLine() }

internal fun getLogLines() = getHiddenLogLines() + getVisibleLogLines()

private fun ShadowLog.LogItem.toLine() = "${getLevel()}: $tag: $msg"
