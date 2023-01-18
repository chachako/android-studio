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
package com.android.jdwptracer;

import java.nio.ByteBuffer;
import org.junit.Test;

public class DDMSetTest {

    @Test
    public void emptyDDM() {
        CmdSetDdm ddm = new CmdSetDdm();

        MessageReader messageReader = new MessageReader();

        ByteBuffer fakeDDMPacket = ByteBuffer.allocate(8);
        fakeDDMPacket.putInt(CmdSetDdm.typeFromName(CmdSetDdm.HELO_CHUNK));
        fakeDDMPacket.putInt(0);
        fakeDDMPacket.rewind();
        messageReader.setBuffer(fakeDDMPacket);

        ddm.parseDdmReply(messageReader, new Session(new Log()));
        // We don't assert anything. Reaching the end of the test without BufferUnderflowException
        // is enough to validate we detect an empty DDM packet.
    }
}
