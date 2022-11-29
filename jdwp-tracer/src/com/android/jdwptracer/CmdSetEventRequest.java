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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class CmdSetEventRequest extends CmdSet {

    protected CmdSetEventRequest() {
        super(15, "EVENT_REQ");

        add(1, "Set", CmdSetEventRequest::parseCmd, CmdSetEventRequest::parseReply);
        add(2, "Clear");
        add(3, "ClearAllBreakpoints");
    }

    @NonNull
    private static Message parseReply(@NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        message.addArg("requestID", reader.getInt());
        return message;
    }

    @NonNull
    private static Message parseCmd(@NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);

        byte eventKind = reader.getByte();
        String eventName = EventKind.fromID(eventKind).name();
        message.addArg("eventKind", eventName);
        message.setName(eventName);

        byte suspendPolicy = reader.getByte();
        message.addArg("suspendPolicy", SuspendPolicy.fromID(suspendPolicy).name());

        int numModifiers = reader.getInt();
        message.addArg("numModifiers", Integer.toString(numModifiers));

        JsonArray modifiers = new JsonArray();
        message.addArg("modifiers", modifiers);

        for (int n = 0; n < numModifiers; n++) {
            ModKind kind = ModKind.fromID(reader.getByte());
            JsonObject modifier = new JsonObject();
            modifiers.add(modifier);

            modifier.addProperty("modKind", kind.name());
            switch (kind) {
                case COUNT:
                    {
                        int count = reader.getInt();
                        modifier.addProperty("count", count);
                    }
                    break;
                case CONDITIONAL:
                    {
                        modifier.addProperty("exprID", reader.getInt());
                    }
                    break;
                case THREAD_ONLY:
                    {
                        modifier.addProperty("threadID", reader.getThreadID());
                    }
                    break;
                case CLASS_ONLY:
                    {
                        modifier.addProperty("clazz", reader.getReferenceTypeID());
                    }
                    break;
                case CLASS_MATCH:
                case CLASS_EXCLUDE:
                    {
                        modifier.addProperty("classPattern", reader.getString());
                    }
                    break;
                case LOCATION_ONLY:
                    {
                        modifier.add("loc", reader.getLocation());
                    }
                    break;
                case EXCEPTION_ONLY:
                    {
                        modifier.addProperty("exceptionOrNull", reader.getReferenceTypeID());
                        modifier.addProperty("caught", reader.getBoolean());
                        modifier.addProperty("uncaught", reader.getBoolean());
                    }
                    break;
                case FIELD_ONLY:
                    {
                        modifier.addProperty("declaring", reader.getReferenceTypeID());
                        modifier.addProperty("fieldID", reader.getFieldID());
                    }
                    break;
                case STEP:
                    {
                        modifier.addProperty("thread", reader.getThreadID());
                        modifier.addProperty("size", reader.getInt());
                        modifier.addProperty("depth", reader.getInt());
                    }
                    break;
                case INSTANCE_ONLY:
                    {
                        modifier.addProperty("instance", reader.getObjectID());
                    }
                    break;
                case SOURCE_NAME_MATCH:
                    {
                        modifier.addProperty("sourceNamePattern", reader.getString());
                    }
                    break;
            }
        }

        return message;
    }
}
