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

import com.android.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

class CmdSetDdm extends CmdSet {

    private static Map<Integer, DDMChunkHandler> ddmHandlers = new HashMap<>();

    private static final String ART_TIMING_CHUNK = "ARTT";
    private static final String HELO_CHUNK = "HELO";

    private static final DDMChunkHandler defaultDDMHandler = new DDMChunkHandler();

    static {
        ddmHandlers.put(typeFromName("APNM"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("EXIT"), defaultDDMHandler);
        ddmHandlers.put(
                typeFromName("HELO"),
                new DDMChunkHandler(CmdSetDdm::parseHELOCmd, CmdSetDdm::parseHELOReply));
        ddmHandlers.put(typeFromName("FEAT"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("TEST"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("WAIT"), defaultDDMHandler);

        // Profiling
        ddmHandlers.put(typeFromName("MPRS"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("MPSS"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("MPSE"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("MPRQ"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("SPSS"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("SPSE"), defaultDDMHandler);

        // Heap Status
        ddmHandlers.put(typeFromName("HPIF"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("HPST"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("HPEN"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("HPSG"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("HPGC"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("HPDU"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("HPDS"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("REAE"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("REAQ"), defaultDDMHandler);
        ddmHandlers.put(typeFromName("REAL"), defaultDDMHandler);

        ddmHandlers.put(
                typeFromName("ARTT"),
                new DDMChunkHandler(
                        CmdSetDdm::parseArtMetricsCmd, CmdSetDdm::parseArtMetricsReply));
    }

    // Source debugmon.html
    protected CmdSetDdm() {
        super(0xc7, "DDM");

        // All DDM cmd use the same cmd. The type is part of the payload.
        add(1, "Packet", this::parseDdmCmd, this::parseDdmReply);
    }

    private Message parseDdmReply(@NonNull MessageReader reader, @NonNull Session session) {
        Message msg = new Message(reader);

        int type = reader.getInt();
        int length = reader.getInt();

        if (ddmHandlers.containsKey(type)) {
            ddmHandlers.get(type).getReplyParser().parse(reader, session, msg);
        }

        return msg;
    }

    private Message parseDdmCmd(@NonNull MessageReader reader, @NonNull Session session) {
        Message msg = new Message(reader);

        int type = reader.getInt();
        int length = reader.getInt();

        msg.setName(typeToName(type));

        if (ddmHandlers.containsKey(type)) {
            ddmHandlers.get(type).getCmdParser().parse(reader, session, msg);
        } else {
            msg.setName("UNKNOWN(" + typeToName(type) + ")");
        }

        return msg;
    }

    @NotNull
    private static void parseArtMetricsCmd(
            @NonNull MessageReader reader, @NonNull Session session, @NonNull Message msg) {
        // These are the timing from art processing on-device.
        int version = reader.getInt(); // Not used for now. Switch parsing upon new version.

        int numTimings = reader.getInt();
        msg.addArg("numTimings", numTimings);

        Map<Long, DdmJDWPTiming> timings = new HashMap<>();
        for (int i = 0; i < numTimings; i++) {
            int id = reader.getInt();
            int cmdset = reader.getInt();
            int cmd = reader.getInt();
            long start_ns = reader.getLong();
            long duration_ns = reader.getLong();
            timings.put((long) id, new DdmJDWPTiming(id, cmdset, cmd, start_ns, duration_ns));
        }
        session.addTimings(timings);
    }

    @NotNull
    private static void parseArtMetricsReply(
            @NonNull MessageReader reader, @NonNull Session session, @NonNull Message msg) {}

    @NonNull
    private static void parseHELOCmd(
            @NonNull MessageReader reader, @NonNull Session session, @NonNull Message msg) {}

    @NonNull
    private static void parseHELOReply(
            @NonNull MessageReader reader, @NonNull Session session, @NonNull Message msg) {
        msg.addArg("version", reader.getInt());
        msg.addArg("pid", reader.getInt());

        int vmIdentLen = reader.getInt();
        int appNameLen = reader.getInt();

        msg.addArg("vmIdent", reader.getCharString(vmIdentLen));
        msg.addArg("processName", reader.getCharString(appNameLen));

        if (!reader.hasRemaining()) {
            return;
        }
        msg.addArg("userid", reader.getInt());

        if (!reader.hasRemaining()) {
            return;
        }
        // check if the VM has reported information about the ABI
        int abiLength = reader.getInt();
        msg.addArg("abi", reader.getCharString(abiLength));

        if (!reader.hasRemaining()) {
            return;
        }
        int jvmFlagsLength = reader.getInt();
        msg.addArg("jvmFlags", reader.getCharString(jvmFlagsLength));

        if (!reader.hasRemaining()) {
            return;
        }
        byte nativeDebuggableByte = reader.getByte();
        msg.addArg("nativeDebuggable", nativeDebuggableByte);

        if (!reader.hasRemaining()) {
            return;
        }
        int packageNameLength = reader.getInt();
        msg.addArg("packageName", reader.getCharString(packageNameLength));
    }

    static String typeToName(int type) {
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
