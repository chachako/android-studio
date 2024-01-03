/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon

import com.android.adblib.AdbOutputChannel

/**
 * A helper that writes responses to the adbd socket, behaving as a service running on the device.
 */
internal class ResponseWriter(
  private val adbOutputChannel: AdbOutputChannel,
  private val needsCrc32: Boolean
) {
  suspend fun writeStringResponse(streamId: Int, output: String) =
    writeResponse(streamId, output.withHexLengthPrefix())

  suspend fun writeOkayResponse(streamId: Int, output: String? = null) =
    writeResponse(streamId, "OKAY${output?.withHexLengthPrefix() ?: ""}")

  suspend fun writeFailResponse(streamId: Int, failureReason: String) =
    writeResponse(streamId, "FAIL${failureReason.withHexLengthPrefix()}")

  private suspend fun writeResponse(streamId: Int, output: String) {
    OkayCommand(streamId, streamId).writeTo(adbOutputChannel, needsCrc32)
    WriteCommand(streamId, streamId, output.toByteArray()).writeTo(adbOutputChannel, needsCrc32)
    CloseCommand(streamId, streamId).writeTo(adbOutputChannel, needsCrc32)
  }

  private fun String.withHexLengthPrefix(): String = String.format("%04X", length) + this
}
