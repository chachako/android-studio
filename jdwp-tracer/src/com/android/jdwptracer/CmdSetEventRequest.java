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

class CmdSetEventRequest extends CmdSet {

    protected CmdSetEventRequest() {
        super(15, "EVENT_REQ");

        add(1, "Set", CmdSetEventRequest::parseCmd, CmdSetEventRequest::parseReply);
        add(2, "Clear");
        add(3, "ClearAllBreakpoints");
    }

    @NonNull
    private static Message parseReply(
            @NonNull ByteBuffer byteBuffer, @NonNull MessageReader reader) {
        return new Message(byteBuffer);
    }

    @NonNull
    private static Message parseCmd(@NonNull ByteBuffer byteBuffer, @NonNull MessageReader reader) {
        Message message = new Message(byteBuffer);

        byte eventKind = reader.getByte(byteBuffer);
        String eventName = EventKind.fromID(eventKind).name();
        message.addArg("eventKind", eventName);
        message.setName(eventName);

        byte suspendPolicy = reader.getByte(byteBuffer);
        message.addArg("suspendPolicy", SuspendPolicy.fromID(suspendPolicy).name());

        int numModifiers = reader.getInt(byteBuffer);
        message.addArg("numModifiers", Integer.toString(numModifiers));

        return message;
    }
}
