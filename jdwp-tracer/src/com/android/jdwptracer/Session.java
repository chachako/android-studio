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

// This is the "brain" of the tracing library which gather events, associate them together in the
// case of cmd/reply pairs, and convert them to "Event" which is the rendition's elementary unit.
class Session {

    private MessageReader messageReader = new MessageReader();

    private final HashMap<Integer, Transmission> idToTransmission = new HashMap<>();
    List<Event> events = new ArrayList<>();

    Session() {}

    // private
    boolean addPacket(@NonNull ByteBuffer packet) {
        long now = System.nanoTime();

        int length = packet.getInt();
        int id = packet.getInt();
        byte flag = packet.get();

        if (Packet.isReply(flag)) {
            short error = packet.getShort();
            // Get cmdSet and cmd from id.
            if (!idToTransmission.containsKey(id)) {
                String msg = String.format(Locale.US, "Found reply id=%d packet without a cmd", id);
                System.out.println("Warning: " + msg);
                return false;
                // throw new IllegalStateException(msg);
                // return false;
            }
            Transmission t = idToTransmission.get(id);

            int cmdSetID = t.cmd().cmdSetID();
            int cmdID = t.cmd().cmdID();

            // Make a Reply
            CmdSet cmdSet = CmdSets.get(cmdSetID);
            Message message = cmdSet.getCmd(cmdID).getReplyParser().parse(packet, messageReader);
            Reply reply = new Reply(id, length, error, now, message);
            t.addReply(reply);

            // Was it the last packet in the session (Reply to Command VM (1), Exit (10).
            return cmdSetID == CmdSetVM.ID && cmdID == CmdSetVM.Cmd.EXIT.ID;
        } else {
            int cmdSetID = packet.get() & 0xFF; // Convert from unsigned byte to signed int.
            int cmdID = packet.get() & 0xFF; // Convert from unsigned byte to signed int.

            // Make a command
            CmdSet cmdSet = CmdSets.get(cmdSetID);
            // System.out.println(cmdSet.name() + ":" + cmdSet.getCmd(cmdID).getName());
            Message message = cmdSet.getCmd(cmdID).getCmdParser().parse(packet, messageReader);
            Command command = new Command(id, cmdSetID, cmdID, length, now, message);
            Transmission t = new Transmission(command, id);
            idToTransmission.put(id, t);
            events.add(t);
            return false;
        }
    }

    void addEvent(@NonNull String name) {
        long now = System.nanoTime();
        events.add(new NamedEvent(name, now));
    }

    @NonNull
    List<Event> events() {
        return events;
    }
}
