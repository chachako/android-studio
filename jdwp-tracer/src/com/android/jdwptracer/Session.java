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
import com.android.jdwppacket.IDSizes;
import com.android.jdwppacket.MessageReader;
import com.android.jdwppacket.PacketHeader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// This is the "brain" of the tracing library which gather events, associate them together in the
// case of cmd/reply pairs, and convert them to "Event" which is the rendition's elementary unit.
class Session {

    private IDSizes idSizes = new IDSizes();

    private final List<Event> events = new ArrayList<>();

    private final HashMap<Integer, Transmission> upStreamTransmissions = new HashMap<>();

    private Map<Integer, DdmJDWPTiming> timings = new HashMap<>();

    private String name = "unknown";

    private final Log log;

    Session(Log log) {
        this.log = log;
    }

    void addPacket(@NonNull ByteBuffer packet, Direction direction) {
        long now_ns = System.nanoTime();

        MessageReader messageReader = new MessageReader(idSizes, packet);
        PacketHeader header = new PacketHeader(messageReader);

        if (header.isReply()) {
            processReplyPacket(now_ns, header, packet);
        } else {
            processCmdPacket(now_ns, header, packet, direction);
        }
    }

    private void processCmdPacket(
            long time_ns,
            @NonNull PacketHeader header,
            @NonNull ByteBuffer packet,
            Direction direction) {
        // From here, we parse the packet with the message reader.
        MessageReader messageReader = new MessageReader(idSizes, packet);

        CmdSet cmdSet = CmdSets.get(header.getCmdSet());
        Message message = cmdSet.getCmd(header.getCmd()).getCmdParser().parse(messageReader, this);
        Command command = new Command(header, time_ns, message);

        Transmission t = new Transmission(command, header.getId(), events.size());

        // We only track upstream commands. Downstream commands are event without a reply
        if (direction == Direction.UPSTREAM) {
            upStreamTransmissions.put(header.getId(), t);
        }
        events.add(t);
    }

    private void processReplyPacket(
            long time_ns, @NonNull PacketHeader header, @NonNull ByteBuffer packet) {
        if (!upStreamTransmissions.containsKey(header.getId())) {
            String msg =
                    String.format(
                            Locale.US,
                            "Found reply id=%s packet without a cmd",
                            Integer.toHexString(header.getId()));
            log.warn(msg);
            return;
        }
        Transmission t = upStreamTransmissions.get(header.getId());
        upStreamTransmissions.remove(header.getId());

        int cmdSetID = t.cmd().cmdSetID();
        int cmdID = t.cmd().cmdID();

        // From here, we parse the packet with the message reader.
        MessageReader messageReader = new MessageReader(idSizes, packet);

        // If the reply errored, we should not try to parse it.
        if (header.getError() != 0) {
            Message message = new Message(messageReader);
            Reply reply = new Reply(header, time_ns, message);
            t.addReply(reply);
            t.cmd().message().prefixName("ERROR:");
            return;
        }

        // Make a Reply
        CmdSet cmdSet = CmdSets.get(cmdSetID);
        Message message = cmdSet.getCmd(cmdID).getReplyParser().parse(messageReader, this);
        Reply reply = new Reply(header, time_ns, message);
        t.addReply(reply);
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
    Map<Integer, DdmJDWPTiming> timings() {
        return timings;
    }

    void addTimings(@NonNull Map<Integer, DdmJDWPTiming> timings) {
        this.timings.putAll(timings);
    }

    void setName(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    String name() {
        return name;
    }

    @NonNull
    String details(@NonNull ByteBuffer buffer) {
        String details = "";
        if (buffer.remaining() < Integer.BYTES) {
            details += "Empty";
            return details;
        }
        details += "Length=" + buffer.getInt();

        if (buffer.remaining() < Integer.BYTES) {
            details += ", no ID";
            return details;
        }
        details += ", ID=" + buffer.getInt();

        if (buffer.remaining() < Byte.BYTES) {
            details += ", no FLAGS";
            return details;
        }
        byte flag = buffer.get();
        details += ", flags=0x" + Integer.toHexString(flag);

        if (Packet.isReply(flag)) {
            details += detailsReply(buffer);

        } else {
            details += detailsCmd(buffer);
        }

        return details;
    }

    private String detailsCmd(ByteBuffer buffer) {
        String details = "";

        if (buffer.remaining() < Byte.BYTES) {
            details += ", no cmdset";
            return details;
        }
        details += ", cmdset=" + buffer.get();

        if (buffer.remaining() < Byte.BYTES) {
            details += ", no cmd";
            return details;
        }
        details += ", cmd=" + buffer.get();

        return details;
    }

    private String detailsReply(ByteBuffer buffer) {
        String details = "";
        if (buffer.remaining() < Short.BYTES) {
            details += ", no errorCode";
            return details;
        }
        details += ", errorCode=" + buffer.getShort();

        return details;
    }

    void setIDSizes(@NonNull IDSizes idSizes) {
        this.idSizes = idSizes;
    }
}
