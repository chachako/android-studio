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

import com.android.jdwppacket.CmdSet
import com.android.jdwppacket.IDSizes
import com.android.jdwppacket.MessageReader
import com.android.jdwppacket.PacketHeader
import com.android.jdwppacket.ThreadReference
import com.android.jdwppacket.VirtualMachine
import com.android.jdwppacket.vm.IDSizesReply
import java.nio.ByteBuffer

typealias CmdKey = String

/**
 * JDWP packet flow in both direction between a debugger and a debuggee. SCache uses the terms
 * "upstream" and "downstream" as follows.
 *
 *     DEBUGGER                    DEBUGGEE
 *         |       ->  upstream ->    |
 *         |       <- downstream<-    |
 *         |                          |
 *
 * Two cases of comm:
 * 1. Debugger sends a command and the Debuggee sends a reply.
 * 2. Debugee sends an event (a.ka. a command without a reply).
 *
 * TODO: Optimize with "belay". If a JWDP request arrives but we already speculated on it, it should
 *   be "belayed" and not sent for forwarding. Instead we "wait" for the reply to arrive and send a
 *   retagged reply.
 */
internal class SCache(private val enabled: Boolean = true, private val logger: SCacheLogger) :
  AutoCloseable {

  private val triggerManager = TriggerManager()

  private var speculator = Speculator(triggerManager, logger)

  private var idSizes = IDSizes()

  init {
    logger.info("scache status is ${if (enabled) { "enabled"} else { "disabled"}}")

    // VirtualMachine.IDSizes trigger to know the format of types in JDWP packet
    triggerManager.registerReplyTrigger(
      CmdSet.Vm.id,
      VirtualMachine.IDSizes.id,
      object : Handler {
        override fun handle(reader: MessageReader, response: SCacheResponse) {
          onIDSizesReply(reader, response)
        }
      }
    )

    // VirtualMachime.Resume to reset everything upon resume
    triggerManager.registerCmdTrigger(
      CmdSet.Vm.id,
      VirtualMachine.Resume.id,
      object : Handler {
        override fun handle(reader: MessageReader, response: SCacheResponse) {
          onResumeCommand(reader, response)
        }
      }
    )

    // ThreadReference.Resume to reset everything upon resume
    triggerManager.registerCmdTrigger(
      CmdSet.ThreadReference.id,
      ThreadReference.Resume.id,
      object : Handler {
        override fun handle(reader: MessageReader, response: SCacheResponse) {
          onResumeCommand(reader, response)
        }
      }
    )
  }

  fun onUpstreamPacket(originalPacket: ByteBuffer): SCacheResponse {
    val response = SCacheResponse()

    if (!enabled) {
      response.addToUpstream(originalPacket)
      return response
    }

    val packet = originalPacket.duplicate()
    val reader = MessageReader(idSizes, packet)
    val header = PacketHeader(reader)

    triggerManager.handle(header, reader.duplicate(), response)

    if (header.isReply()) {
      // Replying to the debuggee should never happen. Neither JDWP events or DDM events
      // involves the debugger having to send a reply to a command. If that was to somehow
      // be introduced, we fallback to a simple forward.
      logger.warn("Replying to VM?? With (${Integer.toHexString(header.id)})")
      response.addToUpstream(originalPacket)
      return response
    }

    // From here we are dealing with a cmd from the debugger to the ART vm

    // Parse the command. If it is keyable, see if we already have a response for it in our cache.
    val speculatedReply = speculator.getCachedReply(header, reader.duplicate())
    if (speculatedReply != null) {
      // This a cache it. We don't forward the cmd. We only add the cache reply to the receive list.
      val reply = speculatedReply.retag(header.id)
      response.addToDownstream(reply)
      response.addToUpstreamJournal(originalPacket)

      // Allow us to trigger on a synthetic reply
      onDownstreamPacket(reply)

      return response
    }

    // Fallback to simple bridge which forwards the packet.
    response.addToUpstream(originalPacket)
    return response
  }

  fun onDownstreamPacket(originalPacket: ByteBuffer): SCacheResponse {
    val response = SCacheResponse()

    if (!enabled) {
      response.addToDownstream(originalPacket)
      return response
    }

    val packet = originalPacket.duplicate()
    val reader = MessageReader(idSizes, packet)
    val header = PacketHeader(reader)

    if (header.isCmd()) {
      // Events cmds and DDM cmds are simply forwarded
      response.addToDownstream(originalPacket)
      return response
    }

    // From here the packet is a reply from ART to the debugger

    // Is this a response to a synthetic command?
    if (speculator.isSyntheticReply(header)) {
      speculator.handleSyntheticReply(header, packet)

      // Don't forward anything. This synthetic reply is "absorbed" by scache.
      // We only log it in the journal.
      response.addToDownstreamJournal(originalPacket)
      return response
    }

    if (header.error.toInt() != 0) {
      response.addToDownstream(originalPacket)
      return response
    }

    triggerManager.handle(header, reader.duplicate(), response)

    response.addToDownstream(originalPacket)
    return response
  }

  private fun onIDSizesReply(reader: MessageReader, response: SCacheResponse) {
    val s = IDSizesReply.parse(reader)
    idSizes =
      IDSizes(s.fieldIDSize, s.methodIDSize, s.objectIDSize, s.referenceTypeIDSize, s.frameIDSize)
  }

  private fun onResumeCommand(reader: MessageReader, response: SCacheResponse) {
    reset()
  }

  private fun reset() {
    speculator = Speculator(triggerManager, logger)
  }

  override fun close() {
    reset()
  }
}
