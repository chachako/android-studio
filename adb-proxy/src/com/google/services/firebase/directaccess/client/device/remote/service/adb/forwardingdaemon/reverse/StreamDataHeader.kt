/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse

import java.nio.ByteBuffer

/** The header sent between the service and the daemon. */
class StreamDataHeader(val type: MessageType, val streamId: Int, val len: Int) {
  constructor(
    buffer: ByteBuffer
  ) : this(MessageType.fromConstant(buffer.getInt(0)), buffer.getInt(4), buffer.getInt(8))

  fun toByteArray(): ByteArray {
    return ByteBuffer.allocate(12).putInt(type.const).putInt(streamId).putInt(len).array()
  }
}
