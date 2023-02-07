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

import com.android.jdwppacket.IsObsoleteCmd;
import com.android.jdwppacket.IsObsoleteReply;
import com.android.jdwppacket.LineTableCmd;
import com.android.jdwppacket.LineTableReply;
import com.android.jdwppacket.MessageReader;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class CmdSetMethod extends CmdSet {

    protected CmdSetMethod() {
        super(6, "METHOD");

        add(1, "LineTable", CmdSetMethod::parseLineTableCmd, CmdSetMethod::parseLineTableReply);
        add(2, "VariableTable");
        add(3, "Bytecodes");
        add(4, "IsObsolete", CmdSetMethod::parseIsObsoleteCmd, CmdSetMethod::parseIsObsoleteReply);
        add(5, "VariableTableWithGenerics");
    }

    private static Message parseIsObsoleteReply(MessageReader reader, Session session) {
        Message message = new Message(reader);
        IsObsoleteReply reply = IsObsoleteReply.parse(reader);
        message.addArg("isObsolete", Boolean.toString(reply.getObsolete()));
        return message;
    }

    private static Message parseIsObsoleteCmd(MessageReader reader, Session session) {
        Message message = new Message(reader);
        IsObsoleteCmd cmd = IsObsoleteCmd.parse(reader);
        message.addArg("refType", cmd.getRefType());
        message.addArg("methodID", cmd.getMethodID());
        return message;
    }

    private static Message parseLineTableReply(MessageReader reader, Session session) {
        Message message = new Message(reader);
        LineTableReply reply = LineTableReply.parse(reader);
        message.addArg("start", reply.getStart());
        message.addArg("end", reply.getEnd());

        JsonArray linesJson = new JsonArray();
        message.addArg("lines", linesJson);
        for (LineTableReply.Line line : reply.getLines()) {
            JsonObject lineJson = new JsonObject();
            linesJson.add(lineJson);
            lineJson.addProperty("lineCodeIndex", line.getLineCodeIndex());
            lineJson.addProperty("lineNumber", line.getLineNumber());
        }
        return message;
    }

    private static Message parseLineTableCmd(MessageReader reader, Session session) {
        Message message = new Message(reader);
        LineTableCmd cmd = LineTableCmd.parse(reader);
        message.addArg("refType", cmd.getRefType());
        message.addArg("methodID", cmd.getMethodID());
        return message;
    }
}
