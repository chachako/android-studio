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

    byte getByte(@NonNull ByteBuffer buffer) {
        return buffer.get();
    }

    String getTypeTag(@NonNull ByteBuffer buffer) {
        byte type = getByte(buffer);
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

    boolean getBoolean(@NonNull ByteBuffer buffer) {
        return buffer.get() != 0;
    }

    short getShort(@NonNull ByteBuffer buffer) {
        return buffer.getShort();
    }

    int getInt(@NonNull ByteBuffer buffer) {
        return buffer.getInt();
    }

    long getLong(@NonNull ByteBuffer buffer) {
        return buffer.getLong();
    }

    JsonObject getLocation(@NonNull ByteBuffer buffer) {
        JsonObject location = new JsonObject();
        location.addProperty("type", getTypeTag(buffer));
        location.addProperty("classID", getClassID(buffer));
        location.addProperty("methodID", getMethodID(buffer));
        location.addProperty("index", getLong(buffer));

        return location;
    }

    void getValue(@NonNull ByteBuffer buffer) {
        byte tag = getByte(buffer); // tag
        switch (tag) {
            case '[':
                getObjectID(buffer);
                break; // Array Object
            case 'B':
                getByte(buffer);
                break; // byte
            case 'C':
                getShort(buffer);
                break; // Character
            case 'L':
                getObjectID(buffer);
                break; // Object
            case 'F':
                getInt(buffer);
                break; // Float
            case 'D':
                getLong(buffer);
                break; // Double
            case 'I':
                getInt(buffer);
                break; // Int
            case 'J':
                getLong(buffer);
                break; // Long
            case 'S':
                getShort(buffer);
                break; // Short
            case 'V':
                break; // Void
            case 'Z':
                getByte(buffer);
                break; // Boolean
            case 's':
                getObjectID(buffer);
                break; // String object
            case 't':
                getObjectID(buffer);
                break; // Thread object
            case 'g':
                getObjectID(buffer);
                break; // Thread Group object
            case 'l':
                getObjectID(buffer);
                break; // Classloader object
            case 'c':
                getObjectID(buffer);
                break; // Class object
            default:
                break;
        }
    }

    long getFieldID(@NonNull ByteBuffer buffer) {
        return getID(buffer, fieldIDSize);
    }

    long getMethodID(@NonNull ByteBuffer buffer) {
        return getID(buffer, methodIDSize);
    }

    long getClassID(@NonNull ByteBuffer buffer) {
        return getReferenceTypeID(buffer);
    }

    long getObjectID(@NonNull ByteBuffer buffer) {
        return getID(buffer, objectIDSize);
    }

    long getThreadID(@NonNull ByteBuffer byteBuffer) {
        return getObjectID(byteBuffer);
    }

    void getTaggedObjectID(@NonNull ByteBuffer buffer) {
        getObjectID(buffer);
        getByte(buffer);
    }

    long getReferenceTypeID(@NonNull ByteBuffer buffer) {
        return getID(buffer, referenceTypeIDSize);
    }

    long getFrameID(@NonNull ByteBuffer buffer) {
        return getID(buffer, frameIDSize);
    }

    public String getString(@NonNull ByteBuffer buffer) {
        byte[] bytes = new byte[getInt(buffer)];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private long getID(@NonNull ByteBuffer buffer, int size) {
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
}
