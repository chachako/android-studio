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

class CmdSetEvent extends CmdSet {

    protected CmdSetEvent() {
        super(64, "EVENT");
        add(100, "Composite", CmdSetEvent::parseCmdComposite, CmdSetEvent::parseReplyComposite);
    }

    @NonNull
    private static Message parseReplyComposite(
            @NonNull ByteBuffer byteBuffer, @NonNull MessageReader reader) {
        return new Message(byteBuffer);
    }

    @NonNull
    private static Message parseCmdComposite(
            @NonNull ByteBuffer byteBuffer, @NonNull MessageReader reader) {
        Message message = new Message(byteBuffer);

        byte suspendPolicy = reader.getByte(byteBuffer);
        message.addArg("suspendPolicy", Byte.toString(suspendPolicy));

        int numEvents = reader.getInt(byteBuffer);
        message.addArg("numEvents", Integer.toString(numEvents));

        for (int i = 0; i < numEvents; i++) {
            byte eventKind = reader.getByte(byteBuffer);
            EventKind kind = EventKind.fromID(eventKind);
            message.addArg("EventKing[" + i + "]", kind.name());
            message.setName(kind.name());

            reader.getInt(byteBuffer); // requestID
            switch (kind) {
                case SINGLE_STEP:
                case BREAKPOINT:
                case METHOD_ENTRY:
                case METHOD_EXIT:
                    {
                        reader.getThreadID(byteBuffer);
                        reader.getLocation(byteBuffer);
                    }
                    break;
                case METHOD_EXIT_WITH_RETURN_VALUE:
                    {
                        reader.getThreadID(byteBuffer); // threadID
                        reader.getLocation(byteBuffer);
                        reader.getValue(byteBuffer);
                    }
                    break;
                case MONITOR_CONTENDED_ENTER:
                case MONITOR_CONTENDED_ENTERED:
                    {
                        reader.getThreadID(byteBuffer); // threadID
                        reader.getTaggedObjectID(byteBuffer);
                        reader.getLocation(byteBuffer);
                    }
                    break;
                case MONITOR_WAIT:
                    {
                        reader.getThreadID(byteBuffer);
                        reader.getTaggedObjectID(byteBuffer);
                        reader.getLocation(byteBuffer);
                        reader.getLong(byteBuffer); // timeout
                    }
                    break;
                case MONITOR_WAITED:
                    {
                        reader.getThreadID(byteBuffer);
                        reader.getTaggedObjectID(byteBuffer);
                        reader.getLocation(byteBuffer);
                        reader.getBoolean(byteBuffer); // timeout
                    }
                    break;
                case EXCEPTION:
                    {
                        reader.getThreadID(byteBuffer); // threadID
                        reader.getLocation(byteBuffer); // location
                        reader.getTaggedObjectID(byteBuffer);
                        reader.getLocation(byteBuffer); // catch location
                    }
                    break;
                case VM_START:
                case THREAD_START:
                case THREAD_DEATH:
                    {
                        reader.getThreadID(byteBuffer);
                    }
                    break;
                case CLASS_PREPARE:
                    {
                        message.addArg("thread", reader.getThreadID(byteBuffer));
                        message.addArg("refTypeTag", reader.getTypeTag(byteBuffer));
                        message.addArg("typeID", reader.getReferenceTypeID(byteBuffer));
                        message.addArg("signature", reader.getString(byteBuffer));
                        message.addArg("status", reader.getInt(byteBuffer));
                    }
                    break;
                case CLASS_UNLOAD:
                    {
                        reader.getString(byteBuffer); // signature
                    }
                    break;
                case FIELD_ACCESS:
                    {
                        reader.getThreadID(byteBuffer); // threadID
                        reader.getLocation(byteBuffer);
                        reader.getByte(byteBuffer); // refTypeTag
                        reader.getReferenceTypeID(byteBuffer); // referenceID
                        reader.getFieldID(byteBuffer);
                        reader.getTaggedObjectID(byteBuffer);
                    }
                    break;
                case FIELD_MODIFICATION:
                    {
                        reader.getThreadID(byteBuffer); // threadID
                        reader.getLocation(byteBuffer);
                        reader.getByte(byteBuffer); // refTypeTag
                        reader.getObjectID(byteBuffer); // referenceID
                        reader.getFieldID(byteBuffer);
                        reader.getTaggedObjectID(byteBuffer);
                        reader.getValue(byteBuffer);
                    }
                    break;
                default:
                    throw new IllegalStateException("Unprocessed Kind" + kind.name());
            }
        }

        if (numEvents != 1) {
            message.setName("multiple");
        }

        return message;
    }
}
