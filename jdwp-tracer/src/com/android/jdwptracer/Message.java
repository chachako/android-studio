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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.ByteBuffer;

// The payload of a packet (cmd or reply). For now all fields are stored in a hashmap.
// Some fields like payload_length are synthetic (they do not correspond to an actual field in
// the packet payload.
class Message {
    private final JsonObject args = new JsonObject();

    private String name = "";

    private final int payloadLength;

    private Message(int payloadLength) {
        this.payloadLength = payloadLength;
    }

    // A name to be diplayed in the summary UI
    String name() {
        return name;
    }

    int payloadLength() {
        return payloadLength;
    }

    void setName(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    static Message cmdMessage(@NonNull ByteBuffer byteBuffer) {
        return new Message(byteBuffer.remaining());
    }

    @NonNull
    static Message replyMessage(@NonNull ByteBuffer byteBuffer) {
        return new Message(byteBuffer.remaining());
    }

    void addCmdArg(@NonNull String key, @NonNull String value) {
        addCmdArg(key, new JsonPrimitive(value));
    }

    void addCmdArg(@NonNull String key, @NonNull Number value) {
        addCmdArg(key, new JsonPrimitive(value));
    }

    void addCmdArg(@NonNull String key, @NonNull JsonElement value) {
        args.add(key, value);
    }

    void addReplyArg(@NonNull String key, @NonNull String value) {
        addReplyArg(key, new JsonPrimitive(value));
    }

    void addReplyArg(@NonNull String key, @NonNull Number value) {
        addReplyArg(key, new JsonPrimitive(value));
    }

    void addReplyArg(@NonNull String key, @NonNull JsonElement value) {
        args.add(key, value);
    }

    @NonNull
    JsonObject args() {
        return args;
    }

    @NonNull
    static Message defaultCmdParser(@NonNull ByteBuffer buffer, @NonNull MessageReader unused) {
        return Message.cmdMessage(buffer);
    }

    @NonNull
    static Message defaultReplyParser(@NonNull ByteBuffer buffer, @NonNull MessageReader unused) {
        return Message.cmdMessage(buffer);
    }
}
