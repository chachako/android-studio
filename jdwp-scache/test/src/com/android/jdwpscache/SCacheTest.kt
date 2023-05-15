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
import com.android.jdwppacket.Location
import com.android.jdwppacket.MessageReader
import com.android.jdwppacket.PacketHeader
import com.android.jdwppacket.ReferenceType
import com.android.jdwppacket.SourceFileCmd
import com.android.jdwppacket.SourceFileReply
import com.android.jdwppacket.SuperClassCmd
import com.android.jdwppacket.threadreference.FramesCmd
import com.android.jdwppacket.threadreference.FramesReply
import com.android.jdwppacket.vm.IDSizesCmd
import com.android.jdwppacket.vm.IDSizesReply
import com.android.jdwppacket.vm.ResumeCmd
import java.nio.BufferUnderflowException
import org.junit.Assert
import org.junit.Test

class SCacheTest {

  // Make sure we catch IDSize reply
  @Test
  fun testIDSizeReply() {
    val scache = SCache(true, SCacheTestLogger())
    val id = IDGenerator()
    val idSizes = IDSizes(8) // All fields are 8 bytes long

    // Make sure scache sees that all fields are 8 bytes long
    scache.onUpstreamPacket(IDSizesCmd().toPacket(id.get(), IDSizes()))
    scache.onDownstreamPacket(IDSizesReply(idSizes).toPacket(id.getLast(), idSizes))

    // Send a packet featuring threadID (objectID) and frameID. Write it with 8 byte long field.
    // This should work
    scache.onUpstreamPacket(SuperClassCmd(0).toPacket(id.get(), IDSizes(8)))
    try {
      // Try again with id size resulting in buffer underflow (1 byte fields)
      // This should fail
      scache.onUpstreamPacket(SuperClassCmd(0).toPacket(id.get(), IDSizes(1)))
      Assert.fail("Failed to detect buffer underflow because of threadId/frameID")
    } catch (_: BufferUnderflowException) {}
  }

  // Make sure we trigger on Frames
  @Test
  fun testFrames() {
    val scache = SCache(true, SCacheTestLogger())
    val id = IDGenerator()
    val idSizes = IDSizes(8) // All fields are 8 bytes long

    scache.onUpstreamPacket(FramesCmd(0, 0, 0).toPacket(id.get(), idSizes))

    val frames = listOf(FramesReply.Frame(0, Location(0, 0, 0, 0)))
    val rep = scache.onDownstreamPacket(FramesReply(frames).toPacket(id.getLast(), idSizes))
    Assert.assertEquals("Not speculating on Frames", rep.edict.toUpstream.size, 8)
  }

  // Make sur we reset on VM Resume
  @Test
  fun testResumeVM() {
    val scache = SCache(true, SCacheTestLogger())
    val id = IDGenerator()
    val idSizes = IDSizes()

    // Fake a Frame cmd and then a reply
    scache.onUpstreamPacket(FramesCmd(0, 0, 0).toPacket(id.get(), idSizes))
    val frames = listOf(FramesReply.Frame(0, Location(0, 0, 0, 0)))
    val frameSpeculators =
      scache.onDownstreamPacket(FramesReply(frames).toPacket(id.getLast(), idSizes))

    // Find the SourceFile speculation
    var syntheticID = -1
    frameSpeculators.edict.toUpstream.forEach {
      val reader = MessageReader(idSizes, it)
      val header = PacketHeader(reader)
      if (header.cmdSet == CmdSet.ReferenceType.id && header.cmd == ReferenceType.SourceFile.id) {
        syntheticID = header.id
      }
    }
    Assert.assertNotEquals("Not speculating on SourceFile", -1, syntheticID)

    // Fake a speculation reply
    scache.onDownstreamPacket(SourceFileReply("foo").toPacket(syntheticID, idSizes))

    // This should be a cache hit
    val hit = scache.onUpstreamPacket(SourceFileCmd(0).toPacket(id.get(), idSizes))
    Assert.assertEquals("Not cache hit on SourceFile", 1, hit.edict.toDownstream.size)

    // Resume WHOLE VM now. This should reset the speculator cache
    scache.onUpstreamPacket(ResumeCmd().toPacket(id.get(), idSizes))

    val miss = scache.onUpstreamPacket(SourceFileCmd(0).toPacket(id.get(), idSizes))
    Assert.assertEquals("Not cache miss after VM.Resume", 0, miss.edict.toDownstream.size)
  }

  // Make sur we reset on Thread Resume
  @Test
  fun testResumeThread() {
    val scache = SCache(true, SCacheTestLogger())
    val id = IDGenerator()
    val idSizes = IDSizes()

    // Fake a Frame cmd and then a reply
    scache.onUpstreamPacket(FramesCmd(0, 0, 0).toPacket(id.get(), idSizes))
    val frames = listOf(FramesReply.Frame(0, Location(0, 0, 0, 0)))
    val frameSpeculators =
      scache.onDownstreamPacket(FramesReply(frames).toPacket(id.getLast(), idSizes))

    // Find the SourceFile speculation
    var syntheticID = -1
    frameSpeculators.edict.toUpstream.forEach {
      val reader = MessageReader(idSizes, it)
      val header = PacketHeader(reader)
      if (header.cmdSet == CmdSet.ReferenceType.id && header.cmd == ReferenceType.SourceFile.id) {
        syntheticID = header.id
      }
    }
    Assert.assertNotEquals("Not speculating on SourceFile", -1, syntheticID)

    // Fake a speculation reply
    scache.onDownstreamPacket(SourceFileReply("foo").toPacket(syntheticID, idSizes))

    // This should be a cache hit
    val hit = scache.onUpstreamPacket(SourceFileCmd(0).toPacket(id.get(), idSizes))
    Assert.assertEquals("Not cache hit on SourceFile", 1, hit.edict.toDownstream.size)

    // Resume THREAD now. This should reset the speculator cache
    scache.onUpstreamPacket(
      com.android.jdwppacket.threadreference.ResumeCmd(0).toPacket(id.get(), idSizes)
    )

    val miss = scache.onUpstreamPacket(SourceFileCmd(0).toPacket(id.get(), idSizes))
    Assert.assertEquals(
      "Not cache miss after ThreadReference.Resume",
      0,
      miss.edict.toDownstream.size
    )
  }
}
