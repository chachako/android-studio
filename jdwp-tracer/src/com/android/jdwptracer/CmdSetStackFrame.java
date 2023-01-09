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
import com.android.jdwppacket.MessageReader;
import com.android.jdwppacket.stackframe.GetValuesCmd;
import com.android.jdwppacket.stackframe.GetValuesReply;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class CmdSetStackFrame extends CmdSet {

    protected CmdSetStackFrame() {
        super(16, "STACK_FRAME");

        add(
                1,
                "GetValues",
                CmdSetStackFrame::parseGetValuesCmd,
                CmdSetStackFrame::parseGetValuesReply);
        add(2, "SetValues");
        add(3, "ThisObject");
        add(4, "PopFrames");
    }

    private static Message parseGetValuesCmd(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);

        GetValuesCmd reply = GetValuesCmd.parse(reader);
        message.addArg("threadID", reply.getThreadID());
        message.addArg("frameID", reply.getFrameID());

        JsonArray slots = new JsonArray();
        for (GetValuesCmd.Slot slot : reply.getSlots()) {
            JsonObject slotEntry = new JsonObject();
            slotEntry.addProperty("slot", slot.getSlot());
            slotEntry.addProperty("sigbyte", slot.getSibByte());
            slots.add(slotEntry);
        }
        message.addArg("slots", slots);

        return message;
    }

    private static Message parseGetValuesReply(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);

        GetValuesReply reply = GetValuesReply.parse(reader);
        message.addArg("values", reply.getNumValues());

        return message;
    }
}
