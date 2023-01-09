import com.android.jdwppacket.Location
import com.android.jdwppacket.MessageReader
import com.android.jdwppacket.MessageWriter
import com.android.jdwppacket.threadreference.FramesReply
import org.junit.Assert
import org.junit.Test

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
class ThreadReferenceTest {

  @Test
  fun testFramesReply() {
    var frames = FramesReply(listOf(FramesReply.Frame(0, Location(1, 2, 3, 4))))
    val writer = MessageWriter()

    // Generate expected serialized bytebuffer
    val expected = frames.toByteBuffer(writer)

    // Parse and serialize again to generate actual bytebuffer
    val reader = MessageReader()
    reader.setBuffer(expected.duplicate())
    val parsedFrame = FramesReply.parse(reader)
    val actual = parsedFrame.toByteBuffer(writer)

    Assert.assertEquals(expected, actual)
  }
}
