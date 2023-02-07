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
import com.android.jdwppacket.SourceFileCmd;
import com.android.jdwppacket.SourceFileReply;
import com.android.jdwppacket.referencetype.MethodsCmd;
import com.android.jdwppacket.referencetype.MethodsReply;
import com.android.jdwppacket.referencetype.MethodsWithGenericsCmd;
import com.android.jdwppacket.referencetype.MethodsWithGenericsReply;
import com.android.jdwppacket.referencetype.SignatureCmd;
import com.android.jdwppacket.referencetype.SignatureReply;
import com.android.jdwppacket.referencetype.SignatureWithGenericCmd;
import com.android.jdwppacket.referencetype.SignatureWithGenericReply;
import com.android.jdwppacket.referencetype.SourceDebugExtensionCmd;
import com.android.jdwppacket.referencetype.SourceDebugExtensionReply;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class CmdSetReferenceType extends CmdSet {

    protected CmdSetReferenceType() {
        super(2, "REF_TYPE");

        add(
                1,
                "Signature",
                CmdSetReferenceType::parseSignatureCmd,
                CmdSetReferenceType::parseSignatureReply);
        add(2, "Classloader");
        add(3, "Modifiers");
        add(4, "Fields");
        add(
                5,
                "Methods",
                CmdSetReferenceType::parseMethodsCmd,
                CmdSetReferenceType::parseMethodsReply);
        add(6, "GetValues");
        add(
                7,
                "SourceFile",
                CmdSetReferenceType::parseSourceFileCmd,
                CmdSetReferenceType::parseSourceFileReply);
        add(8, "NestedTypes");
        add(9, "Status");
        add(10, "Interfaces");
        add(11, "ClassObject");
        add(
                12,
                "SourceDebugExtension",
                CmdSetReferenceType::parseSourceDebugExtensionCmd,
                CmdSetReferenceType::parseSourceDebugExtensionReply);
        add(
                13,
                "SignatureWithGenerics",
                CmdSetReferenceType::parseSignatureWithGenericCmd,
                CmdSetReferenceType::parseSignatureWithGenericReply);
        add(14, "FieldWithGenerics");
        add(
                15,
                "MethodWithGenerics",
                CmdSetReferenceType::parseMethodsWithGenericsCmd,
                CmdSetReferenceType::parseMethodsWithGenericsReply);
        add(16, "Instances");
        add(17, "ClassFileVersion");
        add(18, "ConstantPool");
    }

    private static Message parseSignatureCmd(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        SignatureCmd cmd = SignatureCmd.parse(reader);
        message.addArg("refType", cmd.getRefType());
        return message;
    }

    private static Message parseSignatureReply(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        SignatureReply reply = SignatureReply.parse(reader);
        message.addArg("signature", reply.getSignature());
        return message;
    }

    private static Message parseSourceDebugExtensionCmd(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        SourceDebugExtensionCmd cmd = SourceDebugExtensionCmd.parse(reader);
        message.addArg("refType", cmd.getRefType());
        return message;
    }

    private static Message parseSourceDebugExtensionReply(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        SourceDebugExtensionReply reply = SourceDebugExtensionReply.parse(reader);
        message.addArg("extension", reply.getExtension());
        return message;
    }

    private static Message parseSignatureWithGenericCmd(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        SignatureWithGenericCmd cmd = SignatureWithGenericCmd.parse(reader);
        message.addArg("refType", cmd.getRefType());
        return message;
    }

    private static Message parseSignatureWithGenericReply(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        SignatureWithGenericReply reply = SignatureWithGenericReply.parse(reader);
        message.addArg("signature", reply.getSignature());
        message.addArg("genericSignature", reply.getGenericSignature());
        return message;
    }

    private static Message parseMethodsCmd(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        MethodsCmd cmd = MethodsCmd.parse(reader);
        message.addArg("refType", cmd.getRefType());
        return message;
    }

    private static Message parseMethodsReply(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        MethodsReply reply = MethodsReply.parse(reader);

        message.addArg("declared", reply.getMethods().size());

        JsonArray methods = new JsonArray();
        for (MethodsReply.Method method : reply.getMethods()) {
            JsonObject methodJson = new JsonObject();
            methodJson.addProperty("methodID", method.getMethodID());
            methodJson.addProperty("name", method.getName());
            methodJson.addProperty("signature", method.getSignature());
            methodJson.addProperty("modBits", method.getModBits());
            methods.add(methodJson);
        }
        message.addArg("methods", methods);

        return message;
    }

    private static Message parseMethodsWithGenericsCmd(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        MethodsWithGenericsCmd cmd = MethodsWithGenericsCmd.parse(reader);
        message.addArg("refType", cmd.getRefType());
        return message;
    }

    private static Message parseMethodsWithGenericsReply(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        MethodsWithGenericsReply reply = MethodsWithGenericsReply.parse(reader);

        message.addArg("declared", reply.getMethods().size());

        JsonArray methods = new JsonArray();
        for (MethodsWithGenericsReply.Method method : reply.getMethods()) {
            JsonObject methodJson = new JsonObject();
            methodJson.addProperty("methodID", method.getMethodID());
            methodJson.addProperty("name", method.getName());
            methodJson.addProperty("signature", method.getSignature());
            methodJson.addProperty("genericSignature", method.getGenericSignature());
            methodJson.addProperty("modBits", method.getModBits());
            methods.add(methodJson);
        }
        message.addArg("methods", methods);

        return message;
    }

    private static Message parseSourceFileCmd(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        SourceFileCmd cmd = SourceFileCmd.parse(reader);
        message.addArg("refType", cmd.getRefType());
        return message;
    }

    private static Message parseSourceFileReply(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        SourceFileReply reply = SourceFileReply.parse(reader);
        message.addArg("sourceFile", reply.getSourceFile());
        return message;
    }
}
