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
package com.android.jdwpscache

import java.nio.ByteBuffer

internal fun ByteBuffer.retag(id: Int): ByteBuffer {
  // To retag, we directly overwrite the id in a JDWP packet
  val ID_OFFSET = 4
  putInt(ID_OFFSET, id)
  return this
}

internal fun ByteBuffer.deepCopy(): ByteBuffer {
  val clone = ByteBuffer.allocate(limit())
  // For some weird reason, kotlinc things we want to use the Array extension
  // slice if we do it in one line
  //   clone.put(duplicate().rewind().slice()) // Error
  val duplicate: ByteBuffer = duplicate().rewind() as ByteBuffer
  clone.put(duplicate.slice())
  clone.flip()
  return clone
}

internal fun packCmd(cmdSet: Int, cmd: Int): PackedCmdSetCmd {
  return cmdSet.shl(8) or cmd
}
