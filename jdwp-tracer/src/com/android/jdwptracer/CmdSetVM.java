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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class CmdSetVM extends CmdSet {

    static final int ID = 1;

    enum Cmd {
        VERSION(1, "Version"),
        CLASSES_BY_SIGNATURE(2, "ClassesBySignature"),
        ALL_CLASSES(3, "AllClasses"),
        ALL_THREADS(4, "AllThreads"),
        TOP_LEVEL_GROUP_THREADS(5, "TopLevelGroupThreads"),
        DISPOSE(6, "Dispose"),
        ID_SIZES(7, "IdSizes"),
        SUSPEND(8, "Suspend"),
        RESUME(9, "Resume"),
        EXIT(10, "Exit"),
        CREATE_STRING(11, "CreateString"),
        CAPABILITIES(12, "Capabilities"),
        CLASSPATH(13, "ClassPaths"),
        DISPOSE_OBJECTS(14, "DisposeObjects"),
        HOLD_EVENTS(15, "HoldEvents"),
        RELEASE_EVENTS(16, "ReleaseEvents"),
        CAPACITIES_NEW(17, "CapabilitiesNew"),
        REDEFINE_CLASSES(18, "RedefineClasses"),
        SET_DEFAULT_STRATUM(19, "SetDefaultStratum"),
        ALL_CLASSES_WITH_GENERIC(20, "AllClassesWithGeneric"),
        INSTANCE_COUNTS(21, "InstanceCounts");

        public final int ID;
        public final String NAME;

        Cmd(int id, @NonNull String name) {
            this.ID = id;
            this.NAME = name;
        }
    }

    CmdSetVM() {
        super(ID, "VM");

        add(Cmd.VERSION);
        add(
                Cmd.CLASSES_BY_SIGNATURE,
                CmdSetVM::parseClassesBySignatureCmd,
                CmdSetVM::parseClassesBySignatureReply);
        add(Cmd.ALL_CLASSES);
        add(Cmd.ALL_THREADS);
        add(Cmd.TOP_LEVEL_GROUP_THREADS);
        add(Cmd.DISPOSE);
        add(Cmd.ID_SIZES, CmdSetVM::parseCmdIdSizes, CmdSetVM::parseReplyIdSizes);
        add(Cmd.SUSPEND);
        add(Cmd.RESUME);
        add(Cmd.EXIT);
        add(Cmd.CREATE_STRING);
        add(Cmd.CAPABILITIES);
        add(Cmd.CLASSPATH);
        add(Cmd.DISPOSE_OBJECTS);
        add(Cmd.HOLD_EVENTS);
        add(Cmd.RELEASE_EVENTS);
        add(Cmd.CAPACITIES_NEW);
        add(Cmd.REDEFINE_CLASSES);
        add(Cmd.SET_DEFAULT_STRATUM);
        add(
                Cmd.ALL_CLASSES_WITH_GENERIC,
                CmdSetVM::parseAllClassesWithGenericsCmd,
                CmdSetVM::parseAllClassesWithGenericsReply);
        add(Cmd.INSTANCE_COUNTS);
    }

    private void add(@NonNull Cmd cmd) {
        add(cmd.ID, cmd.NAME);
    }

    private void add(@NonNull Cmd cmd, @NonNull PacketParser onCmd, @NonNull PacketParser onReply) {
        add(cmd.ID, cmd.NAME, onCmd, onReply);
    }

    @NonNull
    private static Message parseReplyIdSizes(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);

        int fieldIDSize = reader.getInt();
        reader.setFieldIDSize(fieldIDSize);
        message.addArg("FieldIDSize", Integer.toString(fieldIDSize));

        int methodIDSize = reader.getInt();
        reader.setMethodIDSize(methodIDSize);
        message.addArg("methodIDSize", Integer.toString(methodIDSize));

        int objectIDSize = reader.getInt();
        reader.setObjectIDSize(objectIDSize);
        message.addArg("objectIDSize", Integer.toString(objectIDSize));

        int referenceTypeIDSize = reader.getInt();
        reader.setReferenceTypeIDSize(referenceTypeIDSize);
        message.addArg("referenceTypeID", Integer.toString(referenceTypeIDSize));

        int frameIDSize = reader.getInt();
        reader.setFrameIDSize(frameIDSize);
        message.addArg("frameIDSize", Integer.toString(frameIDSize));

        return message;
    }

    @NonNull
    private static Message parseCmdIdSizes(
            @NonNull MessageReader reader, @NonNull Session session) {
        return Message.defaultMessageParser(reader, session);
    }

    private static Message parseClassesBySignatureCmd(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = Message.defaultMessageParser(reader, session);

        message.addArg("signature", reader.getString());

        return message;
    }

    private static Message parseClassesBySignatureReply(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);

        int classes = reader.getInt();

        message.addArg("classes", classes);

        JsonArray classList = new JsonArray();
        for (int i = 0; i < classes; i++) {
            JsonObject classEntry = new JsonObject();
            classEntry.addProperty("refTypeTag", reader.getByte());
            classEntry.addProperty("typeID", reader.getReferenceTypeID());
            classEntry.addProperty("status", reader.getInt());

            classList.add(classEntry);
        }

        message.addArg("classList", classList);

        return message;
    }

    private static Message parseAllClassesWithGenericsCmd(
            @NonNull MessageReader reader, @NonNull Session session) {
        return Message.defaultMessageParser(reader, session);
    }

    private static Message parseAllClassesWithGenericsReply(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);

        int classes = reader.getInt();
        message.addArg("classes", Integer.toString(classes));

        JsonArray classList = new JsonArray();
        for (int i = 0; i < classes; i++) {
            JsonObject classEntry = new JsonObject();
            classEntry.addProperty("refTypeTag", reader.getByte());
            classEntry.addProperty("typeID", reader.getReferenceTypeID());
            classEntry.addProperty("signature", reader.getString());
            classEntry.addProperty("genericSignature", reader.getString());
            classEntry.addProperty("status", reader.getInt());

            classList.add(classEntry);
        }

        message.addArg("classList", classList);

        return message;
    }
}
