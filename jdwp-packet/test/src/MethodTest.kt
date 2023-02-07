import com.android.jdwppacket.IsObsoleteCmd
import com.android.jdwppacket.IsObsoleteReply
import com.android.jdwppacket.LineTableCmd
import com.android.jdwppacket.LineTableReply
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
class MethodTest {

  @Test
  fun testIsObsoleteCmd() {
    val packet = IsObsoleteCmd(Long.MAX_VALUE - 1, Long.MAX_VALUE - 2)
    assertJDWPObjectAndWireEquals(packet, IsObsoleteCmd::parse)
  }

  fun testIsObsoleteReply() {
    val packetTrue = IsObsoleteReply(true)
    assertJDWPObjectAndWireEquals(packetTrue, IsObsoleteCmd::parse)

    val packetFalse = IsObsoleteReply(false)
    assertJDWPObjectAndWireEquals(packetFalse, IsObsoleteCmd::parse)
  }

  @Test
  fun testLineTableCmd() {
    val packet = LineTableCmd(Long.MAX_VALUE - 1, Long.MAX_VALUE - 2)
    assertJDWPObjectAndWireEquals(packet, LineTableCmd::parse)
  }

  @Test
  fun testLineTableReply() {
    val packet =
      LineTableReply(
        Long.MAX_VALUE - 1,
        Long.MAX_VALUE - 2,
        listOf(LineTableReply.Line(Long.MAX_VALUE, Int.MAX_VALUE))
      )
    assertJDWPObjectAndWireEquals(packet, LineTableReply::parse)
  }
}
