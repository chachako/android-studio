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

import static com.android.jdwppacket.EventKind.CLASS_PREPARE;

import com.android.annotations.NonNull;
import com.android.jdwppacket.EventKind;
import com.android.jdwppacket.MessageReader;
import com.android.jdwppacket.event.CompositeCmd;

class CmdSetEvent extends CmdSet {

    protected CmdSetEvent() {
        super(64, "EVENT");
        add(100, "Composite", CmdSetEvent::parseCmdComposite, CmdSetEvent::parseReplyComposite);
    }

    @NonNull
    private static Message parseReplyComposite(
            @NonNull MessageReader reader, @NonNull Session session) {
        return new Message(reader);
    }

    @NonNull
    private static Message parseCmdComposite(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);

        CompositeCmd cmd = CompositeCmd.parse(reader);
        message.addArg("suspendPolicy", Byte.toString(cmd.getSuspendPolicy()));
        message.addArg("numEvents", Integer.toString(cmd.getEvents().size()));

        int eventCount = 0;
        for (CompositeCmd.Event event : cmd.getEvents()) {
            EventKind kind = event.getKind();
            message.addArg("EventKind[" + eventCount++ + "]", kind.name());
            message.setName(kind.name());

            if (kind == CLASS_PREPARE) {
                CompositeCmd.ClassPrepareEvent cp = (CompositeCmd.ClassPrepareEvent) event;
                message.addArg("thread", cp.getThreadID());
                message.addArg("refTypeTag", cp.getTypeTag());
                message.addArg("typeID", cp.getReferenceTypeID());
                message.addArg("signature", cp.getSignature());
                message.addArg("status", cp.getStatus());
            }
        }

        if (cmd.getEvents().size() != 1) {
            message.setName("multiple");
        }

        return message;
    }
}
