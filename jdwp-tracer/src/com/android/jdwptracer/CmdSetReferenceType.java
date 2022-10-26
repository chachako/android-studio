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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.ByteBuffer;

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
        add(7, "SourceFile");
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

    private static Message parseSignatureCmd(ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.cmdMessage(byteBuffer);

        message.addCmdArg("refType", reader.getReferenceTypeID(byteBuffer));

        return message;
    }

    private static Message parseSignatureReply(ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.replyMessage(byteBuffer);

        message.addReplyArg("signature", reader.getString(byteBuffer));

        return message;
    }

    private static Message parseSourceDebugExtensionCmd(
            ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.cmdMessage(byteBuffer);

        message.addCmdArg("refType", reader.getReferenceTypeID(byteBuffer));

        return message;
    }

    private static Message parseSourceDebugExtensionReply(
            ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.replyMessage(byteBuffer);

        // See
        // https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp/jdwp-protocol.html#JDWP_ReferenceType_SourceDebugExtension.
        // There is supposed to be an extension string argument; but at times I've found that the
        // message just ends. So we need to test for any remaining bytes before trying to read the
        // extension argument.
        if (byteBuffer.hasRemaining()) {
            message.addReplyArg("extension", reader.getString(byteBuffer));
        }

        return message;
    }

    private static Message parseSignatureWithGenericCmd(
            ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.cmdMessage(byteBuffer);

        message.addCmdArg("refType", reader.getReferenceTypeID(byteBuffer));

        return message;
    }

    private static Message parseSignatureWithGenericReply(
            ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.replyMessage(byteBuffer);

        message.addReplyArg("signature", reader.getString(byteBuffer));
        message.addReplyArg("genericSignature", reader.getString(byteBuffer));

        return message;
    }

    private static Message parseMethodsCmd(ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.cmdMessage(byteBuffer);

        message.addCmdArg("refType", reader.getReferenceTypeID(byteBuffer));

        return message;
    }

    private static Message parseMethodsReply(ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.replyMessage(byteBuffer);

        int declared = reader.getInt(byteBuffer);
        JsonArray methods = new JsonArray();
        for (int i = 0; i < declared; i++) {
            JsonObject method = new JsonObject();
            method.addProperty("methodID", reader.getMethodID(byteBuffer));
            method.addProperty("name", reader.getString(byteBuffer));
            method.addProperty("signature", reader.getString(byteBuffer));
            method.addProperty("modBits", reader.getInt(byteBuffer));

            methods.add(method);
        }

        message.addReplyArg("declared", declared);
        message.addReplyArg("methods", methods);

        return message;
    }

    private static Message parseMethodsWithGenericsCmd(
            ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.cmdMessage(byteBuffer);

        message.addCmdArg("refType", reader.getReferenceTypeID(byteBuffer));

        return message;
    }

    private static Message parseMethodsWithGenericsReply(
            ByteBuffer byteBuffer, MessageReader reader) {
        Message message = Message.replyMessage(byteBuffer);

        int declared = reader.getInt(byteBuffer);
        JsonArray methods = new JsonArray();
        for (int i = 0; i < declared; i++) {
            JsonObject method = new JsonObject();
            method.addProperty("methodID", reader.getMethodID(byteBuffer));
            method.addProperty("name", reader.getString(byteBuffer));
            method.addProperty("signature", reader.getString(byteBuffer));
            method.addProperty("genericSignature", reader.getString(byteBuffer));
            method.addProperty("modBits", reader.getInt(byteBuffer));

            methods.add(method);
        }

        message.addReplyArg("declared", declared);
        message.addReplyArg("methods", methods);

        return message;
    }
}
