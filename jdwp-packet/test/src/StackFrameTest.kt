import com.android.jdwppacket.stackframe.GetValuesCmd
import com.android.jdwppacket.stackframe.GetValuesReply
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
class StackFrameTest {

  @Test
  fun testGetValuesCmd() {
    val slots = listOf(GetValuesCmd.Slot(Integer.MAX_VALUE, Byte.MAX_VALUE))
    val packet = GetValuesCmd(Long.MAX_VALUE - 1, Long.MAX_VALUE - 2, slots)
    assertJDWPObjectAndWireEquals(packet, GetValuesCmd::parse)
  }

  @Test
  fun testGetValuesReply() {
    val packet = GetValuesReply(1)
    assertJDWPObjectAndWireEquals(packet, GetValuesReply::parse)
  }
}
