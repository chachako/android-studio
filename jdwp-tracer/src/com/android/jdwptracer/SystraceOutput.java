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
import com.android.annotations.Nullable;
import com.google.gson.JsonObject;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

// The renderer which takes a set of events and turn them into systrace format.
class SystraceOutput {

    static void genOutput(@NonNull List<Event> events, @NonNull Path path) {
        try {
            FileWriter out = new FileWriter(path.toAbsolutePath().toString());
            out.write("[ ");
            for (int i = 0; i < events.size(); i++) {
                Event event = events.get(i);
                if (event instanceof Transmission) {
                    processTransmission(out, (Transmission) event, i);
                } else if (event instanceof NamedEvent) {
                    processNamedEvent(out, (NamedEvent) event, i);
                } else {
                    throw new IllegalStateException("Unknown type of event");
                }
            }

            out.write("{}");
            out.write("]");
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void processNamedEvent(@NonNull FileWriter out, @NonNull NamedEvent event, int i)
            throws IOException {
        String name = event.name();
        emitInstantEvent(out, event.time() / 1000, i, name);
        nameThread(out, i, name);
    }

    private static void processTransmission(
            @NonNull FileWriter out, @NonNull Transmission transmission, int i) throws IOException {
        Command command = transmission.cmd();

        CmdSet cmdset = CmdSets.get(transmission.cmd().cmdSetID());
        String name = cmdset.name() + ":" + cmdset.cmdName(transmission.cmd().cmdID());
        if (!command.message().name().isEmpty()) {
            name += ":" + command.message().name();
        }

        if (transmission.reply().isPresent()) {
            Reply reply = transmission.reply().get();
            long duration = reply.time() - command.time();

            JsonObject args = new JsonObject();
            args.add("cmd", makeMessagePayload(command.message()));
            args.add("reply", makeMessagePayload(reply.message()));
            emitCompleteEvent(out, command.time() / 1000, duration / 1000, i, args);
        } else {
            emitInstantEvent(
                    out, command.time() / 1000, i, name, makeMessagePayload(command.message()));
        }

        // Write the thread dictionary via MetaData Event
        nameThread(out, i, name);
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
            @NonNull FileWriter out, long startTime, long duration, int i, @NonNull JsonObject args)
            throws IOException {
        JsonObject part = new JsonObject();
        part.addProperty("name", "duration");
        part.addProperty("cat", "foo");
        part.addProperty("ph", "X");
        part.addProperty("ts", startTime);
        part.addProperty("dur", duration);
        part.addProperty("pid", 0);
        part.addProperty("tid", i);
        part.add("args", args);

        out.write(part.toString());
        out.write(",\n");
    }

    private static void emitInstantEvent(
            @NonNull FileWriter out, long time, int threadID, @NonNull String name)
            throws IOException {
        emitInstantEvent(out, time, threadID, name, null);
    }

    private static void emitInstantEvent(
            @NonNull FileWriter out,
            long time,
            int threadID,
            @NonNull String name,
            @Nullable JsonObject args)
            throws IOException {
        JsonObject part = new JsonObject();
        part.addProperty("name", name);
        part.addProperty("ph", "i");
        part.addProperty("ts", time);
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
}
