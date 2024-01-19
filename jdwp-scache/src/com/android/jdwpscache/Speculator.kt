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

import com.android.jdwppacket.ClassType
import com.android.jdwppacket.Cmd
import com.android.jdwppacket.CmdSet
import com.android.jdwppacket.IDSizes
import com.android.jdwppacket.IsObsoleteCmd
import com.android.jdwppacket.Keyable
import com.android.jdwppacket.LineTableCmd
import com.android.jdwppacket.MessageReader
import com.android.jdwppacket.Method
import com.android.jdwppacket.PacketHeader
import com.android.jdwppacket.ReferenceType
import com.android.jdwppacket.SourceFileCmd
import com.android.jdwppacket.SuperClassCmd
import com.android.jdwppacket.ThreadReference
import com.android.jdwppacket.VariableTableWithGenericCmd
import com.android.jdwppacket.VirtualMachine
import com.android.jdwppacket.event.CompositeCmd
import com.android.jdwppacket.referencetype.InterfacesCmd
import com.android.jdwppacket.referencetype.MethodsWithGenericsCmd
import com.android.jdwppacket.referencetype.SourceDebugExtensionCmd
import com.android.jdwppacket.threadreference.FramesReply
import com.android.jdwppacket.vm.AllClassesReply
import com.android.jdwppacket.vm.AllClassesWithGenericsReply
import java.nio.ByteBuffer

typealias Parser = (messageReader: MessageReader) -> Keyable

internal class Speculator(triggerManager: TriggerManager, val logger: SCacheLogger) {

  private val idGenerator = IDGenerator()

  /**
   * Track synthetic cmd jdwp identifier (ID) issued.
   * 1. The keysset allows us to recognize when a reply is the result of a speculated synthetic cmd.
   * 2. The valueset gives a Key for a given ID so we know what to use to cache the synthetic reply.
   */
  private val syntheticCmds = mutableMapOf<Int, CmdKey>()

  /**
   * Cache of the synthetic replies. Before being returned, they need to be retagged (with the ID of
   * the cmd issued by the debugger).
   */
  private val cache = mutableMapOf<CmdKey, ByteBuffer>()

  /**
   * When a cmd arrives, we need to parse it to extract its key and check if we already have a
   * synthesized reply for it. All keyables parsers are here.
   */
  private var keyableParsers: MutableMap<Int, Parser> = HashMap()

  // Stats metrics
  private var speculationCounter = 0
  private var cacheHit = 0

  /**
   * The keeper of all class info we have. The primary purpose is to be able to invalidate the cache
   * when a class is unloaded. We must let this object know when:
   * - We speculate on a class attribute (so we add the speculated key to its list)
   * - A class is prepared (so we associate signature with referenceID)
   * - A class is unloaded (so we evict all speculated reply using the signature)
   */
  private val classesRepo = ClassesRepo()

  init {
    // Install a ThreadReference.Frames trigger.
    triggerManager.registerReplyTrigger(
      CmdSet.ThreadReference.id,
      ThreadReference.Frames.id,
      object : Handler {
        override fun handle(reader: MessageReader, response: SCacheResponse) {
          onFramesReply(reader, response)
        }
      },
    )

    triggerManager.registerReplyTrigger(
      CmdSet.Vm.id,
      VirtualMachine.AllClasses.id,
      object : Handler {
        override fun handle(reader: MessageReader, response: SCacheResponse) {
          onAllClassesReply(reader, response)
        }
      },
    )

    triggerManager.registerReplyTrigger(
      CmdSet.Vm.id,
      VirtualMachine.AllClassesWithGeneric.id,
      object : Handler {
        override fun handle(reader: MessageReader, response: SCacheResponse) {
          onAllClassesWithGenericReply(reader, response)
        }
      },
    )

    keyableParsers[packCmd(CmdSet.ClassType.id, ClassType.Superclass.id)] = SuperClassCmd::parse
    keyableParsers[packCmd(CmdSet.Method.id, Method.LineTable.id)] = LineTableCmd::parse
    keyableParsers[packCmd(CmdSet.ReferenceType.id, ReferenceType.SourceFile.id)] =
      SourceFileCmd::parse
    keyableParsers[packCmd(CmdSet.ReferenceType.id, ReferenceType.Interfaces.id)] =
      InterfacesCmd::parse
    keyableParsers[packCmd(CmdSet.ReferenceType.id, ReferenceType.MethodsWithGeneric.id)] =
      MethodsWithGenericsCmd::parse
    keyableParsers[packCmd(CmdSet.ReferenceType.id, ReferenceType.SourceDebugExtension.id)] =
      SourceDebugExtensionCmd::parse
    keyableParsers[packCmd(CmdSet.Method.id, Method.IsObsolete.id)] = IsObsoleteCmd::parse
    keyableParsers[packCmd(CmdSet.Method.id, Method.VariableTableWithGeneric.id)] =
      VariableTableWithGenericCmd::parse
  }

  private fun speculateClassInfo(classID: Long, command: Cmd, response: SCacheResponse) {
    classesRepo.declareSpeculation(classID, command.key)
    speculate(command, response)
  }

  private fun speculate(command: Cmd, response: SCacheResponse) {
    // Check if this synthetic command has already been issued.
    if (syntheticCmds.containsValue(command.key)) {
      return
    }

    // Check if this synthetic command has already received a reply.
    if (cache.containsKey(command.key)) {
      return
    }

    speculationCounter++
    val packetID = idGenerator.get()
    response.addToUpstream(command.toPacket(packetID, IDSizes()))
    syntheticCmds[packetID] = command.key
  }

  internal fun onFramesReply(reader: MessageReader, response: SCacheResponse) {
    // Generate all the requests for this
    val reply = FramesReply.parse(reader)
    var count = 0
    reply.frames.forEach {
      // We favor perception over completion. Instead of retrieving the whole stack
      // we only retrieve what the user is able to see in the GUI. e.g.: 40 elements.
      if (count > 40) return@forEach

      val loc = it.location
      speculateClassInfo(loc.classID, SuperClassCmd(loc.classID), response)
      speculateClassInfo(loc.classID, LineTableCmd(loc.classID, loc.methodID), response)
      speculateClassInfo(loc.classID, SourceFileCmd(loc.classID), response)
      speculateClassInfo(loc.classID, IsObsoleteCmd(loc.classID, loc.methodID), response)
      speculateClassInfo(loc.classID, SourceDebugExtensionCmd(loc.classID), response)
      speculateClassInfo(loc.classID, InterfacesCmd(loc.classID), response)
      speculateClassInfo(
        loc.classID,
        VariableTableWithGenericCmd(loc.classID, loc.methodID),
        response,
      )
      speculateClassInfo(loc.classID, MethodsWithGenericsCmd(loc.classID), response)

      count++
    }
  }

  private fun parseKeyableCmd(cmdSet: Int, cmd: Int, reader: MessageReader): Keyable? {
    val packedCmd = packCmd(cmdSet, cmd)

    if (!keyableParsers.containsKey(packedCmd)) {
      return null
    }

    return keyableParsers[packedCmd]!!.invoke(reader)
  }

  internal fun isSyntheticReply(header: PacketHeader): Boolean {
    return syntheticCmds.contains(header.id)
  }

  internal fun handleSyntheticReply(header: PacketHeader, packet: ByteBuffer) {
    val cmdKey = syntheticCmds[header.id]!!
    cache[cmdKey] = packet.deepCopy()
    syntheticCmds.remove(header.id)
  }

  internal fun getCachedReply(header: PacketHeader, reader: MessageReader): ByteBuffer? {
    val cmd = parseKeyableCmd(header.cmdSet, header.cmd, reader.duplicate())
    if (cmd != null && cache[cmd.key] != null) {
      cacheHit++
      return cache[cmd.key]!!.deepCopy()
    }
    return null
  }

  fun invalidateCache() {
    cache.clear()
  }

  fun onEvent(event: CompositeCmd) {
    event.events.forEach {
      when (it) {
        is CompositeCmd.EventClassUnload -> {
          invalidateCacheForClass(it.signature)
          classesRepo.onClassUnload(it)
        }
        is CompositeCmd.EventClassPrepare -> {
          classesRepo.onClassPrepare(it)
        }
        else -> {}
      }
    }
  }

  private fun invalidateCacheForClass(signature: String) {
    val keys = classesRepo.getSpeculatedFor(signature)
    keys.forEach { cache.remove(it) }
  }

  private fun onAllClassesWithGenericReply(reader: MessageReader, unused: SCacheResponse) {
    classesRepo.onAllClassesWithGenericReply(AllClassesWithGenericsReply.parse(reader))
  }

  private fun onAllClassesReply(reader: MessageReader, unused: SCacheResponse) {
    classesRepo.onAllClassesReply(AllClassesReply.parse(reader))
  }
}
