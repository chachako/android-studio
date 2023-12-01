/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.adblib.tools.debugging.utils

import com.android.adblib.AdbLogger
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import java.io.EOFException
import java.io.IOException
import java.nio.channels.ClosedChannelException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Logs a [Throwable] related to an operation performing I/O, at an appropriate [AdbLogger.Level]
 * depending on the severity of the error, with the assumption [AdbLogger.Level.INFO] is
 * the default [AdbLogger] level.
 */
fun AdbLogger.logIOCompletionErrors(throwable: Throwable) {
    when (throwable) {
        // Cancellation is always expected to happen
        is CancellationException -> {
            debug(throwable) { "Completion due to cancellation" }
        }
        // Errors related to EOF, end of connection or end of Channel are expected to happen
        is EOFException,
        is ClosedChannelException,
        is ClosedSendChannelException,
        is ClosedReceiveChannelException -> {
            info { "Completion due to EOF or connection closed ($throwable)"}
            debug(throwable) { "-- Associated exception" }
        }
        // Other type of I/O errors are expected to happen, but rarely, so logging the stacktrace
        // can be useful.
        is IOException -> {
            info(throwable) { "Completion due to I/O Exception" }
        }
        // Other errors are not expected, log a warning
        else -> {
            warn(throwable, "Completion due to unexpected exception")
        }
    }
}
