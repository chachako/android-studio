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
package com.android.jdwppacket

import java.nio.ByteBuffer

abstract class Packetable {

  protected abstract fun writePayload(writer: Writer)

  private fun write(packetSize: Int, id: Int, writer: Writer) {
    // Common JDWP header
    writer.putInt(packetSize)
    writer.putInt(id)
    writer.putByte(getFlags())

    // Header specific to cmd or reply
    writeHeader(writer)
    // Payload time!
    writePayload(writer)
  }

  protected abstract fun writeHeader(writer: Writer)

  protected abstract fun getFlags(): Byte

  fun toPacket(id: Int, idSizes: IDSizes): ByteBuffer {
    // Perform a dry-run to count bytes
    val counter = MessageWriterCounter(idSizes)
    write(0, id, counter)

    // We know the full size of the packet. Actually write it to a buffer
    val writer = MessageWriter(idSizes, counter.bytesCounted)
    write(counter.bytesCounted, id, writer)
    val buffer = writer.finish()
    buffer.rewind()

    return buffer
  }
}
