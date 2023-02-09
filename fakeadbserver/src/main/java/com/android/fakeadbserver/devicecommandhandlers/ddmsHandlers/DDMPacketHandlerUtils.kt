/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:Suppress("UnusedReceiverParameter")

package com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds the payload of a DDM packet by invoking [block] with a [DdmPayloadWriter]
 */
@Suppress("TestFunctionName")
internal fun DDMPacketHandler.DdmPayload(block: DdmPayloadWriter.() -> Unit): ByteArray {
    val writer = DdmPayloadWriter()
    writer.block()
    return writer.bytes()
}

internal class DdmPayloadWriter {
    val stream = ByteArrayOutputStream()

    fun bytes(): ByteArray {
        return stream.toByteArray()
    }

    fun writeByte(byte: Byte) {
        stream.write(byte.toInt())
    }

    fun writeString(string: String) {
        val byteBuffer = ByteBuffer.allocate(string.ddmByteCount()).order(ByteOrder.BIG_ENDIAN)
        byteBuffer.putDdmString(string)
        stream.writeBytes(byteBuffer.array())
    }
}
