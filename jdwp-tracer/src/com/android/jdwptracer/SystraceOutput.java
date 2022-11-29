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
import com.google.gson.JsonObject;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// The renderer which takes a set of events and turn them into systrace format.
class SystraceOutput {

    static void genOutput(@NonNull Session session, @NonNull Path path) {
        List<Event> events = session.events();
        try {
            FileWriter out = new FileWriter(path.toAbsolutePath().toString());
            out.write("[ ");
            for (int i = 0; i < events.size(); i++) {
                Event event = events.get(i);
                if (event instanceof Transmission) {
                    processTransmission(out, (Transmission) event);
                } else if (event instanceof NamedEvent) {
                    processNamedEvent(out, (NamedEvent) event);
                } else {
                    throw new IllegalStateException("Unknown type of event");
                }
            }

            // On Android U and above, oj-libjdwp sends how long it took for ART to process a cmd
            // and send a reply. We augment our traces with it.
            addARTTimings(session, out);

            out.write("{}");
            out.write("]");
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void addARTTimings(@NonNull Session session, FileWriter out) throws IOException {
        Map<Long, DdmJDWPTiming> idToArtTimings = session.timings();
        List<Transmission> transmissions = event2Transmission(session.events());

        // To integrate the ART timings with the local JDWP packet timings, we need to synchronize
        // the clocks. We achieve this by finding the tightest fit of an ART timing within a
        // Transaction, center it and use this delta for all ART Timings.
        long minDiff_ns = Long.MAX_VALUE;
        long clockDelta_ns = 0;

        for (Transmission t : transmissions) {
            if (!t.reply().isPresent()) {
                continue;
            }

            if (!idToArtTimings.containsKey(t.jdpwId())) {
                continue;
            }

            DdmJDWPTiming timing = idToArtTimings.get(t.jdpwId());
            long duration_ns = timing.duration_ns();
            long transmDuration_ns = t.reply().get().time_ns() - t.cmd().time_ns();

            long diff_ns = transmDuration_ns - duration_ns;
            if (diff_ns < minDiff_ns) {
                minDiff_ns = diff_ns;
                clockDelta_ns =
                        t.cmd().time_ns()
                                - timing.start_ns()
                                + (transmDuration_ns - duration_ns) / 2;
            }
        }

        for (Transmission t : transmissions) {
            if (!t.reply().isPresent()) {
                continue;
            }

            if (!idToArtTimings.containsKey(t.jdpwId())) {
                continue;
            }

            DdmJDWPTiming timing = idToArtTimings.get(t.jdpwId());

            // Use the delta previously calculated to sync the start time with the JDWP packet
            // times.
            long startTime = timing.start_ns() + clockDelta_ns;
            emitCompleteEvent(out, ns2us(startTime), ns2us(timing.duration_ns()), t.line(), "art");
        }
    }

    private static List<Transmission> event2Transmission(List<Event> events) {
        List<Transmission> transmissions = new ArrayList<>();
        for (Event event : events) {
            if (event instanceof Transmission) {
                transmissions.add((Transmission) event);
            }
        }
        return transmissions;
    }

    private static void processNamedEvent(@NonNull FileWriter out, @NonNull NamedEvent event)
            throws IOException {
        String name = event.name();
        emitInstantEvent(out, ns2us(event.time_ns()), event.line(), name);
        nameThread(out, event.line(), name);
    }

    private static void processTransmission(
            @NonNull FileWriter out, @NonNull Transmission transmission) throws IOException {
        Command command = transmission.cmd();

        CmdSet cmdset = CmdSets.get(transmission.cmd().cmdSetID());
        String name = cmdset.name() + ":" + cmdset.cmdName(transmission.cmd().cmdID());
        if (!command.message().name().isEmpty()) {
            name += ":" + command.message().name();
        }

        JsonObject args = new JsonObject();
        args.addProperty("id", transmission.jdpwId());

        if (transmission.reply().isPresent()) {
            Reply reply = transmission.reply().get();
            long duration = reply.time_ns() - command.time_ns();

            args.add("cmd", makeMessagePayload(command.message()));
            args.add("reply", makeMessagePayload(reply.message()));
            emitCompleteEvent(
                    out,
                    ns2us(command.time_ns()),
                    ns2us(duration),
                    transmission.line(),
                    name,
                    args);
        } else {
            args.add("event", makeMessagePayload(command.message()));
            emitInstantEvent(out, ns2us(command.time_ns()), transmission.line(), name, args);
        }

        // Write the thread dictionary via MetaData Event
        nameThread(out, transmission.line(), name);
    }

    private static JsonObject makeMessagePayload(Message message) {
        JsonObject payloadObject = new JsonObject();
        payloadObject.addProperty("length", message.payloadLength());

        JsonObject arguments = message.args();
        if (arguments.size() > 0) {
            payloadObject.add("payload", arguments);
        }

        return payloadObject;
    }

    private static void emitCompleteEvent(
            @NonNull FileWriter out,
            long startTime_us,
            long duration_us,
            int line,
            @NonNull String name)
            throws IOException {
        emitCompleteEvent(out, startTime_us, duration_us, line, name, new JsonObject());
    }

    private static void emitCompleteEvent(
            @NonNull FileWriter out,
            long startTime,
            long duration_us,
            int i,
            @NonNull String name,
            @NonNull JsonObject args)
            throws IOException {
        JsonObject part = new JsonObject();
        part.addProperty("name", name);
        part.addProperty("cat", "foo");
        part.addProperty("ph", "X");
        part.addProperty("ts", startTime);
        part.addProperty("dur", duration_us);
        part.addProperty("pid", 0);
        part.addProperty("tid", i);
        part.add("args", args);

        out.write(part.toString());
        out.write(",\n");
    }

    private static void emitInstantEvent(
            @NonNull FileWriter out, long time_us, int lineID, @NonNull String name)
            throws IOException {
        emitInstantEvent(out, time_us, lineID, name, new JsonObject());
    }

    private static void emitInstantEvent(
            @NonNull FileWriter out,
            long time_us,
            int threadID,
            @NonNull String name,
            @NonNull JsonObject args)
            throws IOException {
        JsonObject part = new JsonObject();
        part.addProperty("name", name);
        part.addProperty("ph", "i");
        part.addProperty("ts", time_us);
        part.addProperty("pid", 0);
        part.addProperty("tid", threadID);
        part.addProperty("s", "t");
        if (args != null) {
            part.add("args", args);
        }

        out.write(part.toString());
        out.write(",\n");
    }

    private static void nameThread(@NonNull FileWriter out, int threadID, @NonNull String name)
            throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("name", name);

        JsonObject part = new JsonObject();
        part.addProperty("name", "thread_name");
        part.addProperty("ph", "M");
        part.addProperty("pid", 0);
        part.addProperty("tid", threadID);
        part.add("args", args);

        out.write(part.toString());
        out.write(",\n");
    }

    private static long ns2us(long time_ns) {
        return time_ns / 1000;
    }
}
