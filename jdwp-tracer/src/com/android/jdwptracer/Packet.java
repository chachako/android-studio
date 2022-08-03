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

abstract class Packet {

    private static final int PACKET_REPLY_FLAG = 0x80;

    private final long time;
    private final long length;
    private final int id;

    @NonNull private final Message message;

    Packet(int id, long length, long time, @NonNull Message message) {
        this.id = id;
        this.length = length;
        this.time = time;
        this.message = message;
    }

    abstract boolean isReply();

    long id() {
        return id;
    }

    long length() {
        return length;
    }

    long time() {
        return time;
    }

    static boolean isReply(byte flags) {
        return (flags & PACKET_REPLY_FLAG) == PACKET_REPLY_FLAG;
    }

    @NonNull
    Message message() {
        return message;
    }
}
