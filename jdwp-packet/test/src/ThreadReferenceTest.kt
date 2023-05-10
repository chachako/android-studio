import com.android.jdwppacket.Location
import com.android.jdwppacket.threadreference.FramesCmd
import com.android.jdwppacket.threadreference.FramesCountCmd
import com.android.jdwppacket.threadreference.FramesCountReply
import com.android.jdwppacket.threadreference.FramesReply
import com.android.jdwppacket.threadreference.NameCmd
import com.android.jdwppacket.threadreference.NameReply
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
  fun testFramesCmd() {
    val packet = FramesCmd(Long.MAX_VALUE, Int.MAX_VALUE, Int.MIN_VALUE)
    assertJDWPObjectAndWireEquals(packet, FramesCmd::parse)
  }

  @Test
  fun testFramesReply() {
    val packet = FramesReply(listOf(FramesReply.Frame(0, Location(1, 2, 3, 4))))
    assertJDWPObjectAndWireEquals(packet, FramesReply::parse)
  }

  @Test
  fun testFramesCountCmd() {
    val packet = FramesCountCmd(Long.MAX_VALUE)
    assertJDWPObjectAndWireEquals(packet, FramesCountCmd::parse)
  }

  @Test
  fun testFramesCountReply() {
    val packet = FramesCountReply(Int.MAX_VALUE)
    assertJDWPObjectAndWireEquals(packet, FramesCountReply::parse)
  }

  @Test
  fun testNameCmd() {
    val packet = NameCmd(Long.MAX_VALUE)
    assertJDWPObjectAndWireEquals(packet, NameCmd::parse)
  }

  @Test
  fun testNameReply() {
    val packet = NameReply("foo")
    assertJDWPObjectAndWireEquals(packet, NameReply::parse)
  }
}
