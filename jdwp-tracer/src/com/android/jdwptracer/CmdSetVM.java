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
        add(Cmd.CLASSES_BY_SIGNATURE);
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
        add(Cmd.ALL_CLASSES_WITH_GENERIC);
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
            @NonNull ByteBuffer byteBuffer, @NonNull MessageReader reader) {
        Message message = Message.replyMessage(byteBuffer);

        int fieldIDSize = reader.getInt(byteBuffer);
        reader.setFieldIDSize(fieldIDSize);
        message.addReplyArg("FieldIDSize", Integer.toString(fieldIDSize));

        int methodIDSize = reader.getInt(byteBuffer);
        reader.setMethodIDSize(methodIDSize);
        message.addReplyArg("methodIDSize", Integer.toString(methodIDSize));

        int objectIDSize = reader.getInt(byteBuffer);
        reader.setObjectIDSize(objectIDSize);
        message.addReplyArg("objectIDSize", Integer.toString(objectIDSize));

        int referenceTypeIDSize = reader.getInt(byteBuffer);
        reader.setReferenceTypeIDSize(referenceTypeIDSize);
        message.addReplyArg("referenceTypeID", Integer.toString(referenceTypeIDSize));

        int frameIDSize = reader.getInt(byteBuffer);
        reader.setFrameIDSize(frameIDSize);
        message.addReplyArg("frameIDSize", Integer.toString(frameIDSize));

        return message;
    }

    @NonNull
    private static Message parseCmdIdSizes(
            @NonNull ByteBuffer byteBuffer, @NonNull MessageReader reader) {
        return Message.defaultCmdParser(byteBuffer, reader);
    }
}
