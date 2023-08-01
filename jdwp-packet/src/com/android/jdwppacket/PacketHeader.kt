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

class PacketHeader(reader: MessageReader) {
  companion object {
    private val HEADER_SIZE = 11
    internal val REPLY_BIT: Int = 0x80
  }

  // Common fields
  val length: Long
  val id: Int
  val flags: Byte
  // Cmd fields
  val cmd: Int
  val cmdSet: Int
  // Reply fields
  val error: Short

  init {
    if (reader.remaining() < HEADER_SIZE) {
      throw IllegalStateException("Not enough byte to parse PacketHeader")
    }
    // Parse common fields
    length = reader.getInt().toUInt().toLong()
    id = reader.getInt()
    flags = reader.getByte()
    if (isReply()) {
      error = reader.getShort()
      cmdSet = CmdSet.NoSet.id
      cmd = 0
    } else {
      error = 0
      cmdSet = reader.getByte().toUByte().toInt()
      cmd = reader.getByte().toUByte().toInt()
    }
  }

  override fun toString(): String {
    if (isReply()) {
      return "reply(0x${Integer.toHexString(id)})"
    } else {
      return "cmd(0x${Integer.toHexString(id)}) $cmdSet-$cmd"
    }
  }

  fun isReply(): Boolean = (flags.toInt() and REPLY_BIT) == REPLY_BIT
  fun isCmd(): Boolean = !isReply()

  fun isA(cmdSet: Int, cmd: Int): Boolean = this.cmdSet == cmdSet && this.cmd == cmd

  fun isA(cmdSet: CmdSet, cmd: Int): Boolean = isA(cmdSet.id, cmd)
}
