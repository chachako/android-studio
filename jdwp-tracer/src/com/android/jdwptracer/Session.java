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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// This is the "brain" of the tracing library which gather events, associate them together in the
// case of cmd/reply pairs, and convert them to "Event" which is the rendition's elementary unit.
class Session {

    private final MessageReader messageReader = new MessageReader();

    private final HashMap<Integer, Transmission> idToTransmission = new HashMap<>();

    private final List<Event> events = new ArrayList<>();

    private Map<Long, DdmJDWPTiming> timings = new HashMap<>();

    Session() {}

    // private
    boolean addPacket(@NonNull ByteBuffer packet) {
        long now_ns = System.nanoTime();

        int length = packet.getInt();
        int id = packet.getInt();
        byte flag = packet.get();

        if (Packet.isReply(flag)) {
            return processReplyPacket(now_ns, length, id, packet);
        } else {
            return processCmdPacket(now_ns, length, id, packet);
        }
    }

    private boolean processCmdPacket(long time_ns, int length, int id, @NonNull ByteBuffer packet) {
        int cmdSetID = packet.get() & 0xFF; // Convert from unsigned byte to signed int.
        int cmdID = packet.get() & 0xFF; // Convert from unsigned byte to signed int.

        // From here, we parse the packet with the message reader.
        messageReader.setBuffer(packet);

        CmdSet cmdSet = CmdSets.get(cmdSetID);
        Message message = cmdSet.getCmd(cmdID).getCmdParser().parse(messageReader, this);
        Command command = new Command(id, cmdSetID, cmdID, length, time_ns, message);

        Transmission t = new Transmission(command, id, events.size());
        idToTransmission.put(id, t);
        events.add(t);

        return false;
    }

    private boolean processReplyPacket(
            long time_ns, int length, int id, @NonNull ByteBuffer packet) {
        short error = packet.getShort();

        if (!idToTransmission.containsKey(id)) {
            String msg = String.format(Locale.US, "Found reply id=%d packet without a cmd", id);
            System.out.println("Warning: " + msg);
            return false;
        }
        Transmission t = idToTransmission.get(id);

        int cmdSetID = t.cmd().cmdSetID();
        int cmdID = t.cmd().cmdID();

        // From here, we parse the packet with the message reader.
        messageReader.setBuffer(packet);

        // Make a Reply
        CmdSet cmdSet = CmdSets.get(cmdSetID);
        Message message = cmdSet.getCmd(cmdID).getReplyParser().parse(messageReader, this);
        Reply reply = new Reply(id, length, error, time_ns, message);
        t.addReply(reply);

        // Was it the last packet in the session (Reply to Command VM (1), Exit (10).
        return cmdSetID == CmdSetVM.ID && cmdID == CmdSetVM.Cmd.EXIT.ID;
    }

    void addEvent(@NonNull String name) {
        long now = System.nanoTime();
        events.add(new NamedEvent(name, now, events.size()));
    }

    @NonNull
    List<Event> events() {
        return events;
    }

    @NonNull
    Map<Long, DdmJDWPTiming> timings() {
        return timings;
    }

    void addTimings(@NonNull Map<Long, DdmJDWPTiming> timings) {
        this.timings.putAll(timings);
    }
}
