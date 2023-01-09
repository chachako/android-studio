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
import com.android.jdwppacket.ModKind;
import com.android.jdwppacket.eventrequest.SetCmd;
import com.android.jdwppacket.eventrequest.SetReply;
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
        SetReply reply = SetReply.parse(reader);
        message.addArg("requestID", reply.getRequestID());
        return message;
    }

    // TODO: If/When we convert this lib to kotlin, we should rewrite the switch as follows:
    // when(mod) {
    //  is SetCmd.ModifierCount -> {
    //    modJson.addProperty("count", mod.getCount()) // No cast needed
    //  ...
    //  }
    // }
    @NonNull
    private static Message parseCmd(@NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);

        SetCmd cmd = SetCmd.parse(reader);
        message.addArg("eventKind", cmd.getKind().name());
        message.addArg("suspendPolicy", SuspendPolicy.fromID(cmd.getSuspendPolicy()).name());
        message.addArg("numModifiers", cmd.getModifiers().size());

        JsonArray modifiers = new JsonArray();
        message.addArg("modifiers", modifiers);

        for (SetCmd.Modifier modifier : cmd.getModifiers()) {
            ModKind kind = modifier.getKind();
            JsonObject modJson = new JsonObject();
            modifiers.add(modJson);

            modJson.addProperty("modKind", kind.name());
            switch (kind) {
                case COUNT:
                    {
                        SetCmd.ModifierCount mod = (SetCmd.ModifierCount) modifier;
                        modJson.addProperty("count", mod.getCount());
                    }
                    break;
                case CONDITIONAL:
                    {
                        SetCmd.ModifierConditional mod = (SetCmd.ModifierConditional) modifier;
                        modJson.addProperty("exprID", mod.getExprID());
                    }
                    break;
                case THREAD_ONLY:
                    {
                        SetCmd.ModifierThreadOnly mod = (SetCmd.ModifierThreadOnly) modifier;
                        modJson.addProperty("threadID", mod.getThreadID());
                    }
                    break;
                case CLASS_ONLY:
                    {
                        SetCmd.ModifierClassOnly mod = (SetCmd.ModifierClassOnly) modifier;
                        modJson.addProperty("clazz", mod.getReferenceTypeID());
                    }
                    break;
                case CLASS_MATCH:
                    {
                        SetCmd.ModifierClassMatch mod = (SetCmd.ModifierClassMatch) modifier;
                        modJson.addProperty("classMatch", mod.getPattern());
                    }
                    break;
                case CLASS_EXCLUDE:
                    {
                        SetCmd.ModifierClassExclude mod = (SetCmd.ModifierClassExclude) modifier;
                        modJson.addProperty("classExclude", mod.getPattern());
                    }
                    break;
                case LOCATION_ONLY:
                    {
                        SetCmd.ModifierLocationOnly mod = (SetCmd.ModifierLocationOnly) modifier;
                        modJson.add("loc", JsonLocation.get(mod.getLocation()));
                    }
                    break;
                case EXCEPTION_ONLY:
                    {
                        SetCmd.ModifierExceptionOnly mod = (SetCmd.ModifierExceptionOnly) modifier;
                        modJson.addProperty("exceptionOrNull", mod.getExceptionOrNull());
                        modJson.addProperty("caught", mod.getCaught());
                        modJson.addProperty("uncaught", mod.getUncaught());
                    }
                    break;
                case FIELD_ONLY:
                    {
                        SetCmd.ModifierFieldOnly mod = (SetCmd.ModifierFieldOnly) modifier;
                        modJson.addProperty("declaring", mod.getDeclaring());
                        modJson.addProperty("fieldID", mod.getFieldID());
                    }
                    break;
                case STEP:
                    {
                        SetCmd.ModifierStep mod = (SetCmd.ModifierStep) modifier;
                        modJson.addProperty("thread", mod.getThreadID());
                        modJson.addProperty("size", mod.getSize());
                        modJson.addProperty("depth", mod.getDepth());
                    }
                    break;
                case INSTANCE_ONLY:
                    {
                        SetCmd.ModifierInstanceOnly mod = (SetCmd.ModifierInstanceOnly) modifier;
                        modJson.addProperty("instance", mod.getInstance());
                    }
                    break;
                case SOURCE_NAME_MATCH:
                    {
                        SetCmd.ModifierSourceNameMatch mod =
                                (SetCmd.ModifierSourceNameMatch) modifier;
                        modJson.addProperty("sourceNamePattern", mod.getSourceNameMatch());
                    }
                    break;
            }
        }

        return message;
    }
}
