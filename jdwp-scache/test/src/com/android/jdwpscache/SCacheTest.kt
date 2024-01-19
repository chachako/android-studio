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
import com.android.jdwppacket.EventKind
import com.android.jdwppacket.IDSizes
import com.android.jdwppacket.Location
import com.android.jdwppacket.MessageReader
import com.android.jdwppacket.PacketHeader
import com.android.jdwppacket.ReferenceType
import com.android.jdwppacket.SourceFileCmd
import com.android.jdwppacket.SourceFileReply
import com.android.jdwppacket.SuperClassCmd
import com.android.jdwppacket.event.CompositeCmd
import com.android.jdwppacket.threadreference.FramesCmd
import com.android.jdwppacket.threadreference.FramesReply
import com.android.jdwppacket.vm.AllClassesCmd
import com.android.jdwppacket.vm.AllClassesReply
import com.android.jdwppacket.vm.AllClassesWithGenericsCmd
import com.android.jdwppacket.vm.AllClassesWithGenericsReply
import com.android.jdwppacket.vm.IDSizesCmd
import com.android.jdwppacket.vm.IDSizesReply
import java.nio.ByteBuffer
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
    Assert.assertTrue("SCache disabled", scache.enabled)

    // Try again with id size resulting in buffer underflow (1 byte fields)
    // This should fail
    scache.onUpstreamPacket(SuperClassCmd(0).toPacket(id.get(), IDSizes(1)))
    Assert.assertFalse("SCache is enabled", scache.enabled)
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

  class DebuggedClass(val id: Long, val signature: String)

  // Test that we catch CLASS_PREPARE and clear the cache accordingly
  @Test
  fun testOnAllClassesWithGenerics() {
    val scache = SCache(true, SCacheTestLogger())
    val id = IDGenerator()
    val idSizes = IDSizes()

    val stringClass = DebuggedClass(15, "java/lang/String")
    val vectorClass = DebuggedClass(16, "java/lang/Vector")

    // Send an allClassesWithGenerics request
    scache.onUpstreamPacket(AllClassesWithGenericsCmd().toPacket(id.get(), idSizes))

    // Receive an allClassesWithGenerics reply
    val class1 = AllClassesWithGenericsReply.Class(0, stringClass.id, stringClass.signature, "", 0)
    val class2 = AllClassesWithGenericsReply.Class(0, vectorClass.id, vectorClass.signature, "", 0)
    scache.onDownstreamPacket(
      AllClassesWithGenericsReply(listOf(class1, class2)).toPacket(id.get(), idSizes)
    )

    // Fake a Frame cmd
    scache.onUpstreamPacket(FramesCmd(0, 0, 0).toPacket(id.get(), idSizes))
    val frame0 = FramesReply.Frame(0, Location(0, stringClass.id, 0, 0))
    val frame1 = FramesReply.Frame(0, Location(0, vectorClass.id, 0, 0))
    val frames = listOf(frame0, frame1)
    val frameSpeculators =
      scache.onDownstreamPacket(FramesReply(frames).toPacket(id.getLast(), idSizes))
    Assert.assertEquals(
      "Not speculating on all frames",
      frameSpeculators.edict.upstreamList.size,
      16,
    )

    // Find the SourceFile speculation
    var syntheticIDs = mutableListOf<Int>()
    frameSpeculators.edict.toUpstream.forEach {
      val reader = MessageReader(idSizes, it)
      val header = PacketHeader(reader)
      if (header.isA(CmdSet.ReferenceType, ReferenceType.SourceFile.id)) {
        syntheticIDs.add(header.id)
      }
    }
    Assert.assertEquals("Not speculating on SourceFile", 2, syntheticIDs.size)

    // Fake speculation replies
    scache.onDownstreamPacket(SourceFileReply("foo").toPacket(syntheticIDs[0], idSizes))
    scache.onDownstreamPacket(SourceFileReply("bar").toPacket(syntheticIDs[1], idSizes))

    // This should be a cache hit
    val hit1 = scache.onUpstreamPacket(SourceFileCmd(stringClass.id).toPacket(id.get(), idSizes))
    Assert.assertEquals("Not cache hit on SourceFile", 1, hit1.edict.toDownstream.size)
    Assert.assertEquals("Forwarding on cache hit on SourceFile", 0, hit1.edict.toUpstream.size)

    // This should also be a cache hit
    val hit2 = scache.onUpstreamPacket(SourceFileCmd(vectorClass.id).toPacket(id.get(), idSizes))
    Assert.assertEquals("Not cache hit on SourceFile", 1, hit2.edict.toDownstream.size)
    Assert.assertEquals("Forwarding on cache hit on SourceFile", 0, hit2.edict.toUpstream.size)

    // Unload the String class now. This should reset the speculator cache for this entry
    val cu = CompositeCmd.EventClassUnload(requestID = 0, signature = stringClass.signature)
    scache.onDownstreamPacket(CompositeCmd(0, listOf(cu)).toPacket(id.get(), idSizes))

    val miss = scache.onUpstreamPacket(SourceFileCmd(stringClass.id).toPacket(id.get(), idSizes))
    Assert.assertEquals("No cache miss after ClassUnload", 1, miss.edict.toDownstream.size)
    Assert.assertEquals("Not packet forwadr after ClassUnload", 0, miss.edict.toUpstream.size)
  }

  // Test that we catch CLASS_PREPARE and clear the cache accordingly
  @Test
  fun testOnAllClasses() {
    val scache = SCache(true, SCacheTestLogger())
    val id = IDGenerator()
    val idSizes = IDSizes()

    val stringClass = DebuggedClass(15, "java/lang/String")
    val vectorClass = DebuggedClass(16, "java/lang/Vector")

    // Send an allClasses request
    scache.onUpstreamPacket(AllClassesCmd().toPacket(id.get(), idSizes))

    // Receive an allClasse reply
    val class1 = AllClassesReply.Class(0, stringClass.id, stringClass.signature, 0)
    val class2 = AllClassesReply.Class(0, vectorClass.id, vectorClass.signature, 0)
    scache.onDownstreamPacket(AllClassesReply(listOf(class1, class2)).toPacket(id.get(), idSizes))

    // Fake a Frame cmd
    scache.onUpstreamPacket(FramesCmd(0, 0, 0).toPacket(id.get(), idSizes))
    val frame0 = FramesReply.Frame(0, Location(0, stringClass.id, 0, 0))
    val frame1 = FramesReply.Frame(0, Location(0, vectorClass.id, 0, 0))
    val frames = listOf(frame0, frame1)
    val frameSpeculators =
      scache.onDownstreamPacket(FramesReply(frames).toPacket(id.getLast(), idSizes))
    Assert.assertEquals(
      "Not speculating on all frames",
      frameSpeculators.edict.upstreamList.size,
      16,
    )

    // Find the SourceFile speculation
    var syntheticIDs = mutableListOf<Int>()
    frameSpeculators.edict.toUpstream.forEach {
      val reader = MessageReader(idSizes, it)
      val header = PacketHeader(reader)
      if (header.isA(CmdSet.ReferenceType, ReferenceType.SourceFile.id)) {
        syntheticIDs.add(header.id)
      }
    }
    Assert.assertEquals("Not speculating on SourceFile", 2, syntheticIDs.size)

    // Fake speculation replies
    scache.onDownstreamPacket(SourceFileReply("foo").toPacket(syntheticIDs[0], idSizes))
    scache.onDownstreamPacket(SourceFileReply("bar").toPacket(syntheticIDs[1], idSizes))

    // This should be a cache hit
    val hit1 = scache.onUpstreamPacket(SourceFileCmd(stringClass.id).toPacket(id.get(), idSizes))
    Assert.assertEquals("Not cache hit on SourceFile", 1, hit1.edict.toDownstream.size)
    Assert.assertEquals("Forwarding on cache hit on SourceFile", 0, hit1.edict.toUpstream.size)

    // This should also be a cache hit
    val hit2 = scache.onUpstreamPacket(SourceFileCmd(vectorClass.id).toPacket(id.get(), idSizes))
    Assert.assertEquals("Not cache hit on SourceFile", 1, hit2.edict.toDownstream.size)
    Assert.assertEquals("Forwarding on cache hit on SourceFile", 0, hit2.edict.toUpstream.size)

    // Unload the String class now. This should reset the speculator cache for this entry
    val cu = CompositeCmd.EventClassUnload(requestID = 0, signature = stringClass.signature)
    scache.onDownstreamPacket(CompositeCmd(0, listOf(cu)).toPacket(id.get(), idSizes))

    val miss = scache.onUpstreamPacket(SourceFileCmd(stringClass.id).toPacket(id.get(), idSizes))
    Assert.assertEquals("No cache miss after ClassUnload", 1, miss.edict.toDownstream.size)
    Assert.assertEquals("Not packet forwadr after ClassUnload", 0, miss.edict.toUpstream.size)
  }

  // Test that we catch CLASS_PREPARE and clear the cache accordingly
  @Test
  fun testOnClassPrepare() {
    val scache = SCache(true, SCacheTestLogger())
    val id = IDGenerator()
    val idSizes = IDSizes()

    val stringClass = DebuggedClass(15, "java/lang/String")
    val vectorClass = DebuggedClass(16, "java/lang/Vector")

    // Load the classes via CLASS_PREPARE
    val cpS =
      CompositeCmd.EventClassPrepare(
        EventKind.CLASS_PREPARE,
        0,
        0,
        0,
        stringClass.id,
        stringClass.signature,
        0,
      )
    scache.onDownstreamPacket(CompositeCmd(0, listOf(cpS)).toPacket(id.get(), idSizes))

    val cpV =
      CompositeCmd.EventClassPrepare(
        EventKind.CLASS_PREPARE,
        0,
        0,
        0,
        vectorClass.id,
        vectorClass.signature,
        0,
      )
    scache.onDownstreamPacket(CompositeCmd(0, listOf(cpV)).toPacket(id.get(), idSizes))

    // Fake a Frame cmd
    scache.onUpstreamPacket(FramesCmd(0, 0, 0).toPacket(id.get(), idSizes))
    val frame0 = FramesReply.Frame(0, Location(0, stringClass.id, 0, 0))
    val frame1 = FramesReply.Frame(0, Location(0, vectorClass.id, 0, 0))
    val frames = listOf(frame0, frame1)
    val frameSpeculators =
      scache.onDownstreamPacket(FramesReply(frames).toPacket(id.getLast(), idSizes))
    Assert.assertEquals(
      "Not speculating on all frames",
      frameSpeculators.edict.upstreamList.size,
      16,
    )

    // Find the SourceFile speculation
    var syntheticIDs = mutableListOf<Int>()
    frameSpeculators.edict.toUpstream.forEach {
      val reader = MessageReader(idSizes, it)
      val header = PacketHeader(reader)
      if (header.isA(CmdSet.ReferenceType, ReferenceType.SourceFile.id)) {
        syntheticIDs.add(header.id)
      }
    }
    Assert.assertEquals("Not speculating on SourceFile", 2, syntheticIDs.size)

    // Fake speculation replies
    scache.onDownstreamPacket(SourceFileReply("foo").toPacket(syntheticIDs[0], idSizes))
    scache.onDownstreamPacket(SourceFileReply("bar").toPacket(syntheticIDs[1], idSizes))

    // This should be a cache hit
    val hit1 = scache.onUpstreamPacket(SourceFileCmd(stringClass.id).toPacket(id.get(), idSizes))
    Assert.assertEquals("Not cache hit on SourceFile", 1, hit1.edict.toDownstream.size)
    Assert.assertEquals("Forwarding on cache hit on SourceFile", 0, hit1.edict.toUpstream.size)

    // This should also be a cache hit
    val hit2 = scache.onUpstreamPacket(SourceFileCmd(vectorClass.id).toPacket(id.get(), idSizes))
    Assert.assertEquals("Not cache hit on SourceFile", 1, hit2.edict.toDownstream.size)
    Assert.assertEquals("Forwarding on cache hit on SourceFile", 0, hit2.edict.toUpstream.size)

    // Unload the String class now. This should reset the speculator cache for this entry
    val cu = CompositeCmd.EventClassUnload(requestID = 0, signature = stringClass.signature)
    scache.onDownstreamPacket(CompositeCmd(0, listOf(cu)).toPacket(id.get(), idSizes))

    val miss = scache.onUpstreamPacket(SourceFileCmd(stringClass.id).toPacket(id.get(), idSizes))
    Assert.assertEquals("No cache miss after ClassUnload", 0, miss.edict.toDownstream.size)
    Assert.assertEquals("Not packet forwadr after ClassUnload", 1, miss.edict.toUpstream.size)
  }

  // Test CLASS_UNLOAD without CLASS_PREPARE
  @Test
  fun testUnknownClassUnload() {
    val scache = SCache(true, SCacheTestLogger())
    val id = IDGenerator()
    val idSizes = IDSizes()

    val stringClass = DebuggedClass(15, "java/lang/String")
    val vectorClass = DebuggedClass(16, "java/lang/Vector")

    // Fake a Frame cmd
    scache.onUpstreamPacket(FramesCmd(0, 0, 0).toPacket(id.get(), idSizes))
    val frame0 = FramesReply.Frame(0, Location(0, stringClass.id, 0, 0))
    val frame1 = FramesReply.Frame(0, Location(0, vectorClass.id, 0, 0))
    val frames = listOf(frame0, frame1)
    val frameSpeculators =
      scache.onDownstreamPacket(FramesReply(frames).toPacket(id.getLast(), idSizes))
    Assert.assertEquals(
      "Not speculating on all frames",
      frameSpeculators.edict.upstreamList.size,
      16,
    )

    // Find the SourceFile speculation
    var syntheticIDs = mutableListOf<Int>()
    frameSpeculators.edict.toUpstream.forEach {
      val reader = MessageReader(idSizes, it)
      val header = PacketHeader(reader)
      if (header.isA(CmdSet.ReferenceType, ReferenceType.SourceFile.id)) {
        syntheticIDs.add(header.id)
      }
    }
    Assert.assertEquals("Not speculating on SourceFile", 2, syntheticIDs.size)

    // Fake speculation replies
    scache.onDownstreamPacket(SourceFileReply("foo").toPacket(syntheticIDs[0], idSizes))
    scache.onDownstreamPacket(SourceFileReply("bar").toPacket(syntheticIDs[1], idSizes))

    // This should be a cache hit
    val hit1 = scache.onUpstreamPacket(SourceFileCmd(stringClass.id).toPacket(id.get(), idSizes))
    Assert.assertEquals("Not cache hit on SourceFile", 1, hit1.edict.toDownstream.size)
    Assert.assertEquals("Forwarding on cache hit on SourceFile", 0, hit1.edict.toUpstream.size)

    // This should also be a cache hit
    val hit2 = scache.onUpstreamPacket(SourceFileCmd(vectorClass.id).toPacket(id.get(), idSizes))
    Assert.assertEquals("Not cache hit on SourceFile", 1, hit2.edict.toDownstream.size)
    Assert.assertEquals("Forwarding on cache hit on SourceFile", 0, hit2.edict.toUpstream.size)

    // Unload the String class now. This should reset the speculator cache for this entry
    val cu = CompositeCmd.EventClassUnload(requestID = 0, signature = stringClass.signature)
    scache.onDownstreamPacket(CompositeCmd(0, listOf(cu)).toPacket(id.get(), idSizes))

    val miss = scache.onUpstreamPacket(SourceFileCmd(stringClass.id).toPacket(id.get(), idSizes))
    Assert.assertEquals("No cache miss after ClassUnload", 1, miss.edict.toDownstream.size)
    Assert.assertEquals("Not packet forward after ClassUnload", 0, miss.edict.toUpstream.size)
  }

  // Check that internal errors resulting in Exception are not surfaced and that scache disable
  // itself upon error.
  @Test
  fun testError() {
    val scache = SCache(true, SCacheTestLogger())
    val id = IDGenerator()
    val idSizes = IDSizes()

    val stringClass = DebuggedClass(15, "java/lang/String")

    // Send an allClasses request
    scache.onUpstreamPacket(AllClassesCmd().toPacket(id.get(), idSizes))

    // Send a buggy cmd with bad size, this should throw a buffer underflow
    val goodPacket = AllClassesCmd().toPacket(id.get(), idSizes)
    val badPacket = ByteBuffer.wrap(goodPacket.array(), 0, goodPacket.capacity() - 2)
    scache.onUpstreamPacket(badPacket)

    // At this point, scache should have disabled itself.
    Assert.assertFalse("SCache not disabled", scache.enabled)

    // Receive an allClasse reply
    val class1 = AllClassesReply.Class(0, stringClass.id, stringClass.signature, 0)
    scache.onDownstreamPacket(AllClassesReply(listOf(class1)).toPacket(id.get(), idSizes))

    // Fake a Frame cmd/reply
    scache.onUpstreamPacket(FramesCmd(0, 0, 0).toPacket(id.get(), idSizes))
    val frame0 = FramesReply.Frame(0, Location(0, stringClass.id, 0, 0))
    val frames = listOf(frame0)
    val frameSpeculators =
      scache.onDownstreamPacket(FramesReply(frames).toPacket(id.getLast(), idSizes))

    // Scache should not have speculated
    Assert.assertEquals(
      "Not speculating on all frames",
      1,
      frameSpeculators.edict.downstreamList.size,
    )
  }
}
