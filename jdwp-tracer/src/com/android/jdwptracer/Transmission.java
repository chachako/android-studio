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
import java.util.Optional;

/**
 * A Transmission is a level above the JDWP protocol in the sense the is associate cmd/reply pairs.
 * It is the format given to the renderer.
 *
 * <p>A transmission represents either: - A JDWP Command along with it matching Reply. Usually a
 * debugger cmd and a debuggee reply. - A JDWP Command without a Reply which is an Event sent from
 * the debuggee to the debugger.
 */
class Transmission extends Event {

    @NonNull private final Command cmd;

    @NonNull private Optional<Reply> reply;

    private final int jdwpId;

    Transmission(@NonNull Command cmd, int jdwpId, int line) {
        super(line);
        this.cmd = cmd;
        this.reply = Optional.empty();
        this.jdwpId = jdwpId;
    }

    void addReply(@NonNull Reply reply) {
        this.reply = Optional.of(reply);
    }

    @NonNull
    public Command cmd() {
        return cmd;
    }

    @NonNull
    Optional<Reply> reply() {
        return reply;
    }

    int jdpwId() {
        return jdwpId;
    }
}
