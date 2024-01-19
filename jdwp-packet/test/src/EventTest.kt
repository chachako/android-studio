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

import com.android.jdwppacket.EventKind
import com.android.jdwppacket.Location
import com.android.jdwppacket.TaggedObjectID
import com.android.jdwppacket.TaggedValue
import com.android.jdwppacket.event.CompositeCmd
import org.junit.Test

class EventTest {
  @Test
  fun testCompositeCmd() {
    val events =
      listOf(
        CompositeCmd.EventThreadLocation(EventKind.SINGLE_STEP, 0, 1, Location(0, 1, 3, 4)),
        CompositeCmd.EventThreadLocation(EventKind.BREAKPOINT, 1, 1, Location(0, 1, 3, 4)),
        CompositeCmd.EventThreadLocation(EventKind.METHOD_ENTRY, 2, 1, Location(0, 1, 3, 4)),
        CompositeCmd.EventThreadLocation(EventKind.METHOD_EXIT, 3, 1, Location(0, 1, 3, 4)),
        CompositeCmd.EventMethodExitReturnValue(
          EventKind.METHOD_EXIT_WITH_RETURN_VALUE,
          4,
          1,
          Location(0, 1, 3, 4),
          TaggedValue('L'.toByte(), 1),
        ),
        CompositeCmd.EventMonitorContended(
          EventKind.MONITOR_CONTENDED_ENTER,
          5,
          1,
          TaggedObjectID(0, 1),
          Location(0, 1, 2, 3),
        ),
        CompositeCmd.EventMonitorContended(
          EventKind.MONITOR_CONTENDED_ENTERED,
          0,
          1,
          TaggedObjectID(1, 3),
          Location(0, 1, 2, 3),
        ),
        CompositeCmd.EventMonitorWait(
          EventKind.MONITOR_WAIT,
          6,
          1,
          TaggedObjectID(0, 1),
          Location(0, 1, 2, 3),
          0,
        ),
        CompositeCmd.EventMonitorWaited(
          EventKind.MONITOR_WAITED,
          0,
          1,
          TaggedObjectID(0, 1),
          Location(0, 1, 2, 3),
          true,
        ),
        CompositeCmd.EventException(
          EventKind.EXCEPTION,
          7,
          1,
          Location(0, 1, 2, 3),
          TaggedObjectID(0, 1),
          Location(0, 1, 2, 3),
        ),
        CompositeCmd.EventLifeCycle(EventKind.VM_START, 8, 1),
        CompositeCmd.EventLifeCycle(EventKind.THREAD_START, 9, 1),
        CompositeCmd.EventLifeCycle(EventKind.THREAD_DEATH, 10, 1),
        CompositeCmd.EventClassPrepare(EventKind.CLASS_PREPARE, 11, 1, 2, 3, "signature", 5),
        CompositeCmd.EventClassUnload(EventKind.CLASS_UNLOAD, 12, "signature"),
        CompositeCmd.EventFieldAccess(
          EventKind.FIELD_ACCESS,
          13,
          1,
          Location(0, 1, 2, 3),
          0,
          1,
          2,
          TaggedObjectID(0, 1),
        ),
        CompositeCmd.EventFieldModification(
          EventKind.FIELD_MODIFICATION,
          13,
          1,
          Location(0, 1, 2, 3),
          0,
          1,
          2,
          TaggedObjectID(0, 1),
          TaggedValue('L'.toByte(), 1),
        ),
      )
    val packet = CompositeCmd(Byte.MAX_VALUE, events)
    assertJDWPObjectAndWireEquals(packet, CompositeCmd::parse)
  }
}
