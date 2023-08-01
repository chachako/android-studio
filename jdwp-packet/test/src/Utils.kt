import com.android.jdwppacket.IDSizes
import com.android.jdwppacket.MessageReader
import com.android.jdwppacket.PacketHeader
import com.android.jdwppacket.Packetable
import org.junit.Assert

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

internal fun assertJDWPObjectAndWireEquals(
  packetable: Packetable,
  parser: (messageReader: MessageReader) -> Packetable
) {
  val idSizes = IDSizes()
  val id = 1234567890

  // Generate expected serialized bytebuffer
  val expectedBytes = packetable.toPacket(id, idSizes)
  Assert.assertEquals(
    "Packet bytebuffer was overallocated",
    expectedBytes.limit(),
    expectedBytes.remaining()
  )

  // Parse and serialize again to generate actual bytebuffer
  val reader = MessageReader(idSizes, expectedBytes.duplicate())
  val header = PacketHeader(reader)
  val parsed = parser(reader)
  Assert.assertEquals("Packet bytebuffer was underread", 0, reader.remaining())

  val actualBytes = parsed.toPacket(header.id, idSizes)

  Assert.assertEquals(expectedBytes, actualBytes)
  Assert.assertEquals(packetable, parsed)
}
