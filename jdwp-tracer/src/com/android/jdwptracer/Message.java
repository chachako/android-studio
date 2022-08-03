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
import java.util.HashMap;
import java.util.Map;

// The payload of a packet (cmd or reply). For now all fields are stored in a hashmap.
// Some fields like payload_length are synthetic (they do not correspond to an actual field in
// the packet payload.
class Message {
    private final Map<String, String> args = new HashMap<>();

    private String name = "";

    private Message() {}

    // A name to be diplayed in the summary UI
    String name() {
        return name;
    }

    void setName(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    static Message cmdMessage(@NonNull ByteBuffer byteBuffer) {
        Message message = new Message();
        message.addCmdArg("payload_length", Integer.toString(byteBuffer.remaining()));
        return message;
    }

    @NonNull
    static Message replyMessage(@NonNull ByteBuffer byteBuffer) {
        Message message = new Message();
        message.addReplyArg("payload_length", Integer.toString(byteBuffer.remaining()));
        return message;
    }

    void addCmdArg(@NonNull String key, @NonNull String value) {
        args.put("cmd." + key, value);
    }

    void addReplyArg(@NonNull String key, @NonNull String value) {
        args.put("reply." + key, value);
    }

    @NonNull
    Map<String, String> args() {
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
