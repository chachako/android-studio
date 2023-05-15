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

import com.android.jdwppacket.MessageReader
import com.android.jdwppacket.PacketHeader

interface Handler {
  fun handle(reader: MessageReader, response: SCacheResponse)
}

// A convenience way to store a cmsetset-cmd key in an Int
typealias PackedCmdSetCmd = Int

class TriggerManager {

  private val cmdHandlers: MutableMap<Int, Handler> = HashMap()
  private val replyHandlers: MutableMap<Int, Handler> = HashMap()

  // To be able to trigger on reply packet which don't feature cmdset/cmd fields, we store this
  // data in a map (JDWP ID) -> Cmdset/cmd.
  private val idToCmds: HashMap<Int, PackedCmdSetCmd> = HashMap()

  internal fun registerCmdTrigger(cmdSet: Int, cmd: Int, callback: Handler) {
    val key = packCmd(cmdSet, cmd)
    cmdHandlers[key] = callback
  }

  internal fun registerReplyTrigger(cmdSet: Int, cmd: Int, callback: Handler) {
    val key = packCmd(cmdSet, cmd)
    replyHandlers[key] = callback
  }

  internal fun handle(header: PacketHeader, reader: MessageReader, response: SCacheResponse) {
    if (header.isCmd()) {
      // Regardless of whether we have a handler, we need to save cmdset/cmd so we can determine
      // what cmdset/cmd a reply is about.
      val packetCmd = packCmd(header.cmdSet, header.cmd)
      idToCmds[header.id] = packetCmd

      // Try to handle it
      if (cmdHandlers.containsKey(packetCmd)) {
        cmdHandlers[packetCmd]!!.handle(reader, response)
      }
    }

    if (header.isReply()) {
      if (idToCmds.containsKey(header.id) && replyHandlers.containsKey(idToCmds[header.id]!!)) {
        replyHandlers[idToCmds[header.id]!!]!!.handle(reader, response)
      }
      idToCmds.remove(header.id)
    }
  }
}
