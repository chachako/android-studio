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
package com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon

import com.android.adblib.AdbChannel
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun createByteBuffer(
  command: Int,
  firstArg: Int = 0,
  secondArg: Int = 0,
  payloadSize: Int = 0,
  payload: String? = null
): ByteBuffer {
  val bufferSize = 24 + payloadSize
  return ByteBuffer.allocate(bufferSize).apply {
    order(ByteOrder.LITTLE_ENDIAN)
    putInt(command)
    putInt(firstArg)
    putInt(secondArg)
    putInt(payloadSize)
    putInt(0) // crc - unused
    putInt(0) // magic - unused
    payload?.let { put(payload.toByteArray()) }
    position(bufferSize)
    flip()
  }
}

suspend fun AdbChannel.assertCommand(vararg values: Int, payload: String? = null) {
  val buffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
  readExactly(buffer)
  // Read payload so that next assertCommand call will read the next command written to channel
  val payloadBuffer = ByteBuffer.allocate(buffer.getInt(12)).order(ByteOrder.LITTLE_ENDIAN)
  readExactly(payloadBuffer)
  payload?.let { assertThat(String(payloadBuffer.array())).isEqualTo(it) }
  values.forEachIndexed { index, value -> assertThat(buffer.getInt(index * 4)).isEqualTo(value) }
}

val String.hexLength: String
  get() = String.format("%04X", length)
