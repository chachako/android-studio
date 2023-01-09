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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

// The payload of a packet (cmd or reply). For now all fields are stored in a hashmap.
// Some fields like payload_length are synthetic (they do not correspond to an actual field in
// the packet payload.
class Message {
    private final JsonObject args = new JsonObject();

    private String name = "";

    private final int payloadLength;

    Message(@NonNull MessageReader reader) {
        this.payloadLength = reader.remaining();
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

    void addArg(@NonNull String key, @NonNull String value) {
        addArg(key, new JsonPrimitive(value));
    }

    void addArg(@NonNull String key, @NonNull Number value) {
        addArg(key, new JsonPrimitive(value));
    }

    void addArg(@NonNull String key, @NonNull JsonElement value) {
        args.add(key, value);
    }

    @NonNull
    JsonObject args() {
        return args;
    }

    @NonNull
    static Message defaultMessageParser(@NonNull MessageReader reader, @NonNull Session unused) {
        return new Message(reader);
    }
}
