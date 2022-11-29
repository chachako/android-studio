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

class CmdSetEvent extends CmdSet {

    protected CmdSetEvent() {
        super(64, "EVENT");
        add(100, "Composite", CmdSetEvent::parseCmdComposite, CmdSetEvent::parseReplyComposite);
    }

    @NonNull
    private static Message parseReplyComposite(
            @NonNull MessageReader reader, @NonNull Session session) {
        return new Message(reader);
    }

    @NonNull
    private static Message parseCmdComposite(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);

        byte suspendPolicy = reader.getByte();
        message.addArg("suspendPolicy", Byte.toString(suspendPolicy));

        int numEvents = reader.getInt();
        message.addArg("numEvents", Integer.toString(numEvents));

        for (int i = 0; i < numEvents; i++) {
            byte eventKind = reader.getByte();
            EventKind kind = EventKind.fromID(eventKind);
            message.addArg("EventKing[" + i + "]", kind.name());
            message.setName(kind.name());

            reader.getInt(); // requestID
            switch (kind) {
                case SINGLE_STEP:
                case BREAKPOINT:
                case METHOD_ENTRY:
                case METHOD_EXIT:
                    {
                        reader.getThreadID();
                        reader.getLocation();
                    }
                    break;
                case METHOD_EXIT_WITH_RETURN_VALUE:
                    {
                        reader.getThreadID(); // threadID
                        reader.getLocation();
                        reader.getValue();
                    }
                    break;
                case MONITOR_CONTENDED_ENTER:
                case MONITOR_CONTENDED_ENTERED:
                    {
                        reader.getThreadID(); // threadID
                        reader.getTaggedObjectID();
                        reader.getLocation();
                    }
                    break;
                case MONITOR_WAIT:
                    {
                        reader.getThreadID();
                        reader.getTaggedObjectID();
                        reader.getLocation();
                        reader.getLong(); // timeout
                    }
                    break;
                case MONITOR_WAITED:
                    {
                        reader.getThreadID();
                        reader.getTaggedObjectID();
                        reader.getLocation();
                        reader.getBoolean(); // timeout
                    }
                    break;
                case EXCEPTION:
                    {
                        reader.getThreadID(); // threadID
                        reader.getLocation(); // location
                        reader.getTaggedObjectID();
                        reader.getLocation(); // catch location
                    }
                    break;
                case VM_START:
                case THREAD_START:
                case THREAD_DEATH:
                    {
                        reader.getThreadID();
                    }
                    break;
                case CLASS_PREPARE:
                    {
                        message.addArg("thread", reader.getThreadID());
                        message.addArg("refTypeTag", reader.getTypeTag());
                        message.addArg("typeID", reader.getReferenceTypeID());
                        message.addArg("signature", reader.getString());
                        message.addArg("status", reader.getInt());
                    }
                    break;
                case CLASS_UNLOAD:
                    {
                        reader.getString(); // signature
                    }
                    break;
                case FIELD_ACCESS:
                    {
                        reader.getThreadID(); // threadID
                        reader.getLocation();
                        reader.getByte(); // refTypeTag
                        reader.getReferenceTypeID(); // referenceID
                        reader.getFieldID();
                        reader.getTaggedObjectID();
                    }
                    break;
                case FIELD_MODIFICATION:
                    {
                        reader.getThreadID(); // threadID
                        reader.getLocation();
                        reader.getByte(); // refTypeTag
                        reader.getObjectID(); // referenceID
                        reader.getFieldID();
                        reader.getTaggedObjectID();
                        reader.getValue();
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
