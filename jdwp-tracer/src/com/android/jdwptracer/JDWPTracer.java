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

    private final Log log;

    public JDWPTracer(boolean enabled) {
        this(enabled, Paths.get(System.getProperty("java.io.tmpdir")));
    }

    public JDWPTracer(boolean enabled, @NonNull Path folder) {
        this(enabled, folder, new Log());
    }

    public JDWPTracer(boolean enabled, @NonNull Log log) {
        this(enabled, Paths.get(System.getProperty("java.io.tmpdir")), log);
    }

    JDWPTracer(boolean enabled, @NonNull Path folder, @NonNull Log log) {
        this.enabled = enabled;
        this.outputFolder = folder;
        if (enabled) {
            session = new Session(log);
        }
        this.log = log;
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

        ByteBuffer packet = buffer.duplicate();
        packet.order(ByteOrder.BIG_ENDIAN);

        try {
            session.addPacket(packet);
        } catch (Exception e) {
            // We don't log the exception to avoid being misleading in idea.log. Add
            // a log.warn(..., e) if it turns out this is not enough data to debug.
            log.warn("Unable to trace packet: " + session.details(buffer.duplicate()), e);
        }
    }

    public synchronized void close() {
        if (!enabled) {
            return;
        }

        Path outputPath = outputFolder.resolve("perfetto-trace-" + session.name() + ".json");
        SystraceOutput.genOutput(session, outputPath);
        log.info("JDWTrace written to '" + outputPath.toAbsolutePath() + "'");
        session = new Session(log);
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
