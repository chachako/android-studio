/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers

import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class JdwpVmIdSizesHandler : JdwpPacketHandler {

    override fun handlePacket(
        device: DeviceState,
        client: ClientState,
        packet: JdwpPacket,
        oStream: OutputStream
    ): Boolean {
        // See https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp/jdwp-protocol.html#JDWP_VirtualMachine_IDSizes
        // int	fieldIDSize	fieldID size in bytes
        // int	methodIDSize	methodID size in bytes
        // int	objectIDSize	objectID size in bytes
        // int	referenceTypeIDSize	referenceTypeID size in bytes
        // int	frameIDSize	frameID size in bytes
        val payloadStream = ByteArrayOutputStream()
        writeJdwpInt(payloadStream, 10)
        writeJdwpInt(payloadStream, 10)
        writeJdwpInt(payloadStream, 10)
        writeJdwpInt(payloadStream, 10)
        writeJdwpInt(payloadStream, 10)

        val payload = payloadStream.toByteArray()
        val replyPacket = JdwpPacket.createResponse(packet.id, payload, packet.cmdSet, packet.cmd)
        replyPacket.write(oStream)

        return true // don't close connection
    }

    private fun writeJdwpInt(oStream: OutputStream, value: Int) {
        // See https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/jdwp-spec.html
        // "All fields and data sent via JDWP should be in big-endian format"
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(value)
        writeJdwpBytes(oStream, buffer.array())
    }

    private fun writeJdwpBytes(oStream: OutputStream, bytes: ByteArray) {
        oStream.write(bytes)
    }

    companion object {

        val commandId =
            JdwpCommandId(JdwpCommands.CmdSet.SET_VM.value, JdwpCommands.VmCmd.CMD_VM_IDSIZES.value)
    }
}
