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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.ByteBuffer;

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

    private static Message parseGetValuesCmd(ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.cmdMessage(byteBuffer);

        long threadID = reader.getThreadID(byteBuffer);
        long frameID = reader.getFrameID(byteBuffer);

        message.addCmdArg("threadID", threadID);
        message.addCmdArg("frameID", frameID);

        int numSlots = reader.getInt(byteBuffer);
        JsonArray slots = new JsonArray();
        for (int i = 0; i < numSlots; i++) {
            int slot = reader.getInt(byteBuffer);
            byte sigbyte = reader.getByte(byteBuffer);

            JsonObject slotEntry = new JsonObject();
            slotEntry.addProperty("slot", slot);
            slotEntry.addProperty("sigbyte", sigbyte);

            slots.add(slotEntry);
        }

        message.addCmdArg("slots", slots);

        return message;
    }

    private static Message parseGetValuesReply(ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.replyMessage(byteBuffer);

        int values = reader.getInt(byteBuffer);
        message.addReplyArg("values", values);

        return message;
    }
}
