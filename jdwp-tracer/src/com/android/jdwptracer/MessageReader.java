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

// JDWP packet have fixed size data types such as int (4  bytes), byte (1 byte), and even variable
// length like "string" (4 bytes length + UTF-8 payload). However some data types sizes are specific
// to a VM.
//
// Components fieldID, methodID, objectID, referenceTypeID, and frameID size are retrieved
// at runtime with a command VM.IDSizes which this class holds. All messages (packet payload)
// parsing
// MUST be done through this class.

import com.android.annotations.NonNull;
import com.google.gson.JsonObject;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

class MessageReader {

    @NonNull private ByteBuffer buffer = ByteBuffer.allocate(0);
    private int fieldIDSize = 8;
    private int methodIDSize = 8;
    private int objectIDSize = 8;
    private int referenceTypeIDSize = 8;
    private int frameIDSize = 8;

    void setFieldIDSize(int size) {
        this.fieldIDSize = size;
    }

    void setMethodIDSize(int size) {
        this.methodIDSize = size;
    }

    void setObjectIDSize(int size) {
        this.objectIDSize = size;
    }

    void setReferenceTypeIDSize(int size) {
        this.referenceTypeIDSize = size;
    }

    void setFrameIDSize(int size) {
        this.frameIDSize = size;
    }

    byte getByte() {
        return buffer.get();
    }

    String getTypeTag() {
        byte type = getByte();
        switch (type) {
            case 1:
                return "CLASS";
            case 2:
                return "INTERFACE";
            case 3:
                return "ARRAY";
            default:
                return "UNKNOWN";
        }
    }

    boolean getBoolean() {
        return buffer.get() != 0;
    }

    short getShort() {
        return buffer.getShort();
    }

    int getInt() {
        return buffer.getInt();
    }

    long getLong() {
        return buffer.getLong();
    }

    JsonObject getLocation() {
        JsonObject location = new JsonObject();
        location.addProperty("type", getTypeTag());
        location.addProperty("classID", getClassID());
        location.addProperty("methodID", getMethodID());
        location.addProperty("index", getLong());

        return location;
    }

    void getValue() {
        byte tag = getByte(); // tag
        switch (tag) {
            case '[':
                getObjectID();
                break; // Array Object
            case 'B':
                getByte();
                break; // byte
            case 'C':
                getShort();
                break; // Character
            case 'L':
                getObjectID();
                break; // Object
            case 'F':
                getInt();
                break; // Float
            case 'D':
                getLong();
                break; // Double
            case 'I':
                getInt();
                break; // Int
            case 'J':
                getLong();
                break; // Long
            case 'S':
                getShort();
                break; // Short
            case 'V':
                break; // Void
            case 'Z':
                getByte();
                break; // Boolean
            case 's':
                getObjectID();
                break; // String object
            case 't':
                getObjectID();
                break; // Thread object
            case 'g':
                getObjectID();
                break; // Thread Group object
            case 'l':
                getObjectID();
                break; // Classloader object
            case 'c':
                getObjectID();
                break; // Class object
            default:
                break;
        }
    }

    long getFieldID() {
        return getID(fieldIDSize);
    }

    long getMethodID() {
        return getID(methodIDSize);
    }

    long getClassID() {
        return getReferenceTypeID();
    }

    long getObjectID() {
        return getID(objectIDSize);
    }

    long getThreadID() {
        return getObjectID();
    }

    void getTaggedObjectID() {
        getObjectID();
        getByte();
    }

    long getReferenceTypeID() {
        return getID(referenceTypeIDSize);
    }

    long getFrameID() {
        return getID(frameIDSize);
    }

    public String getString() {
        byte[] bytes = new byte[getInt()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private long getID(int size) {
        switch (size) {
            case 1:
                return buffer.get();
            case 2:
                return buffer.getShort();
            case 4:
                return buffer.getInt();
            case 8:
                return buffer.getLong();
            default:
                throw new IllegalArgumentException("Unsupported id size: " + size);
        }
    }

    void setBuffer(@NonNull ByteBuffer buffer) {
        this.buffer = buffer;
    }

    int remaining() {
        return buffer.remaining();
    }

    boolean hasRemaining() {
        return buffer.hasRemaining();
    }

    String getCharString(int len) {
        char[] data = new char[len];
        for (int i = 0; i < len; i++) data[i] = buffer.getChar();
        return new String(data);
    }
}
