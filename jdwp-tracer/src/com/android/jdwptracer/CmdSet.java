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

class CmdSet {

    @NonNull private Map<Integer, Cmd> cmds = new HashMap<>();

    @NonNull private final String name;
    protected final int id;

    protected CmdSet(int id, @NonNull String name) {
        this.id = id;
        this.name = name;
    }

    int id() {
        return id;
    }

    @NonNull
    String name() {
        return name;
    }

    @NonNull
    String cmdName(int cmdID) {
        if (!cmds.containsKey(cmdID)) {
            return "UNKNOWN(" + cmdID + ")";
        }
        return cmds.get(cmdID).getName();
    }

    void add(
            int cmdID,
            @NonNull String name,
            @NonNull PacketParser cmdParser,
            @NonNull PacketParser replyParser) {
        cmds.put(cmdID, new Cmd(cmdID, name, cmdParser, replyParser));
    }

    void add(int cmdID, @NonNull String name) {
        cmds.put(
                cmdID,
                new Cmd(cmdID, name, Message::defaultMessageParser, Message::defaultMessageParser));
    }

    @NonNull
    Cmd getCmd(int cmdId) {
        if (!cmds.containsKey(cmdId)) {
            return new UnknownCommand(cmdId);
        }
        return cmds.get(cmdId);
    }

    public static class UnknownCommand extends Cmd {
        public UnknownCommand(int cmdID) {
            super(cmdID, "UNKNOWN", Message::defaultMessageParser, Message::defaultMessageParser);
        }
    }
}
