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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

            Map<String, String> args = new HashMap<>();
            args.putAll(command.message().args());
            args.putAll(reply.message().args());
            emitCompleteEvent(out, command.time() / 1000, duration / 1000, i, args);
        } else {

            emitInstantEvent(out, command.time() / 1000, i, name, command.message().args());
        }

        // Write the thread dictionary via MetaData Event
        nameThread(out, i, name);
    }

    private static void emitCompleteEvent(
            @NonNull FileWriter out,
            long startTime,
            long duration,
            int i,
            @NonNull Map<String, String> args)
            throws IOException {
        String part =
                String.format(
                        Locale.US,
                        "{\"name\": \"duration\", \"cat\": \"foo\", \"ph\": \"X\", \"ts\": %d, \"dur\": %d, \"pid\": 0, \"tid\": %d,\n"
                                + "   \"args\": { \" \" : \" \" \n",
                        startTime,
                        duration,
                        i);

        for (String key : args.keySet()) {
            part += ",\"" + key + "\" : \"" + args.get(key) + "\"\n";
        }
        part += " }\n },\n";
        out.write(part);
    }

    private static void emitInstantEvent(
            @NonNull FileWriter out, long time, int threadID, @NonNull String name)
            throws IOException {
        emitInstantEvent(out, time, threadID, name, Collections.emptyMap());
    }

    private static void emitInstantEvent(
            @NonNull FileWriter out,
            long time,
            int threadID,
            @NonNull String name,
            @NonNull Map<String, String> args)
            throws IOException {
        String part =
                String.format(
                        Locale.US,
                        "{\"name\": \"%s\", \"ph\": \"i\", \"ts\": %d, \"pid\": 0, \"tid\": %d, \"s\": \"t\",\n"
                                + "   \"args\": { \" \" : \" \" \n",
                        name,
                        time,
                        threadID);

        for (String key : args.keySet()) {
            part += ",\"" + key + "\" : \"" + args.get(key) + "\"\n";
        }
        part += " }\n },\n";
        out.write(part);
    }

    private static void nameThread(@NonNull FileWriter out, int threadID, @NonNull String name)
            throws IOException {
        String part =
                String.format(
                        Locale.US,
                        "{\"name\": \"thread_name\", \"ph\": \"M\", \"pid\": 0, \"tid\": %d,\n"
                                + " \"args\": {\n"
                                + "  \"name\" : \"%s\"\n"
                                + " }\n"
                                + "},\n",
                        threadID,
                        name);
        out.write(part);
    }
}
