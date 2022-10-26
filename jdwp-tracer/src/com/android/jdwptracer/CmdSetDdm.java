/*
 * Copyright (C) 2022 The Android Open Source Project
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
import java.util.HashMap;
import java.util.Map;

class CmdSetDdm extends CmdSet {

    private static Map<Integer, String> ddmTypes = new HashMap<>();

    static {
        addType("APNM");
        addType("EXIT");
        addType("HELO");
        addType("FEAT");
        addType("TEST");
        addType("WAIT");

        // Profiling
        addType("MPRS");
        addType("MPSS");
        addType("MPSE");
        addType("MPRQ");
        addType("MPRQ");
        addType("SPSS");
        addType("SPSE");

        // Heap Status
        addType("HPIF");
        addType("HPST");
        addType("HPEN");
        addType("HPSG");
        addType("HPGC");
        addType("HPDU");
        addType("HPDS");
        addType("REAE");
        addType("REAQ");
        addType("REAL");
    }

    // Source debugmon.html
    protected CmdSetDdm() {
        super(0xc7, "DDM");

        // Add DDM cmd use the same cmd. The type is part of the payload.
        add(1, "Packet", this::parseDdmCmd, this::parseDdmReply);
    }

    private Message parseDdmReply(ByteBuffer byteBuffer, MessageReader reader) {
        Message msg = new Message(byteBuffer);
        return msg;
    }

    private Message parseDdmCmd(ByteBuffer byteBuffer, MessageReader reader) {
        Message msg = new Message(byteBuffer);
        int type = reader.getInt(byteBuffer);
        int length = reader.getInt(byteBuffer);

        if (ddmTypes.containsKey(type)) {
            msg.setName(ddmTypes.get(type));
        } else {
            msg.setName("UNKNOWN(" + typeToName(type) + ")");
        }

        return msg;
    }

    private static void addType(String name) {
        ddmTypes.put(typeFromName(name), name);
    }

    private static String typeToName(int type) {
        char[] ascii = new char[4];

        ascii[0] = (char) ((type >> 24) & 0xff);
        ascii[1] = (char) ((type >> 16) & 0xff);
        ascii[2] = (char) ((type >> 8) & 0xff);
        ascii[3] = (char) (type & 0xff);

        return new String(ascii);
    }

    private static int typeFromName(String name) {
        if (name.length() != 4) {
            throw new RuntimeException("DDM Type name must be 4 letter long");
        }

        int val = 0;
        for (int i = 0; i < 4; i++) {
            val <<= 8;
            val |= (byte) name.charAt(i);
        }
        return val;
    }
}
