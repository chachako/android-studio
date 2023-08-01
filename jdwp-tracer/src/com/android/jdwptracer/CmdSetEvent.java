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
import com.android.jdwppacket.EventKind;
import com.android.jdwppacket.MessageReader;
import com.android.jdwppacket.SuspendPolicy;
import com.android.jdwppacket.event.CompositeCmd;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class CmdSetEvent extends CmdSet {

    protected CmdSetEvent() {
        super(64, "EVENT");
        add(100, "Compo", CmdSetEvent::parseCmdComposite, CmdSetEvent::parseReplyComposite);
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
        message.addArg("suspendPolicy", SuspendPolicy.fromID(cmd.getSuspendPolicy()).name());
        message.addArg("numEvents", Integer.toString(cmd.getEvents().size()));

        JsonArray eventsJson = new JsonArray();
        message.addArg("events", eventsJson);
        for (CompositeCmd.Event event : cmd.getEvents()) {
            JsonObject eventJson = new JsonObject();
            eventsJson.add(eventJson);

            EventKind kind = event.getKind();
            eventJson.addProperty("eventKind", kind.name());
            message.setName(kind.name());

            switch (kind) {
                case CLASS_PREPARE:
                    {
                        CompositeCmd.EventClassPrepare cp = (CompositeCmd.EventClassPrepare) event;
                        eventJson.addProperty("thread", cp.getThreadID());
                        eventJson.addProperty("refTypeTag", cp.getTypeTag());
                        eventJson.addProperty("typeID", cp.getReferenceTypeID());
                        eventJson.addProperty("signature", cp.getSignature());
                        eventJson.addProperty("status", cp.getStatus());
                    }
                    break;
                case BREAKPOINT:
                    {
                        CompositeCmd.EventThreadLocation se =
                                (CompositeCmd.EventThreadLocation) event;
                        eventJson.addProperty("threadID", se.getThreadID());
                        eventJson.add("location", JsonLocation.get(se.getLocation()));
                    }
                    break;
                default:
                    break;
            }
        }

        if (cmd.getEvents().size() != 1) {
            message.setName("multiple");
        }

        return message;
    }
}
