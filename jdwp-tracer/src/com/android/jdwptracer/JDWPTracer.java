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
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This is the ONE exposed interface to the Tracer lib. Everything else should be package private.
 * All public methods MUST be synchronized.
 */
public class JDWPTracer {

    private Session session;

    private final boolean enabled;
    private final Path outputFolder;

    public JDWPTracer(boolean enabled) {
        this(enabled, Paths.get(System.getProperty("java.io.tmpdir")));
    }

    public JDWPTracer(boolean enabled, @NonNull Path folder) {
        this.enabled = enabled;
        this.outputFolder = folder;
    }

    /**
     * Add a packet [buffer] to the session [id]. A session ID associate all packets together and is
     * used to associate cmds and replies.
     *
     * @param id The identifier of the current session. Socket.hashCode is a good candidate.
     * @param buffer The full JDWP packet including header.
     */
    public synchronized void addPacket(@NonNull ByteBuffer buffer) {
        if (!enabled) {
            return;
        }

        buffer = buffer.duplicate();
        buffer.order(ByteOrder.BIG_ENDIAN);

        // VM_EXIT reply should be the latest packet in a session.
        // When we see it, it is time to write the trace to storage.
        boolean exitDetected = session.addPacket(buffer);
        if (exitDetected) {
            Path outputPath = outputFolder.resolve("perfetto-trace.json");
            SystraceOutput.genOutput(session.events(), outputPath);
            System.out.println("JDWTrace written to '" + outputPath.toAbsolutePath() + "'");
            session = new Session();
        }
    }

    /**
     * Beside packets, JDWPTracer also support named events.
     *
     * @param name The name of the event
     */
    public synchronized void addEvent(@NonNull String name) {
        if (!enabled) {
            return;
        }
        session.addEvent(name);
    }
}
