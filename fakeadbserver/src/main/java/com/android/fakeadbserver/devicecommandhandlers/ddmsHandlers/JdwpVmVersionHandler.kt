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

class JdwpVmVersionHandler : JdwpPacketHandler {

    override fun handlePacket(
        device: DeviceState,
        client: ClientState,
        packet: JdwpPacket,
        jdwpHandlerOutput: JdwpHandlerOutput
    ): Boolean {
        // See https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp/jdwp-protocol.html#JDWP_VirtualMachine_Version
        // string description	Text information on the VM version
        // int    jdwpMajor     Major JDWP Version number
        // int    jdwpMinor     Minor JDWP Version number
        // string vmVersion     Target VM JRE version, as in the java.version property
        // string vmName        Target VM name, as in the java.vm.name property
        val payloadStream = ByteArrayOutputStream()
        writeJdwpString(payloadStream, "FakeAdbServer")
        writeJdwpInt(payloadStream, 1)
        writeJdwpInt(payloadStream, 2)
        writeJdwpString(payloadStream, "1.8")
        writeJdwpString(payloadStream, "FakeVM")

        val payload = payloadStream.toByteArray()
        val replyPacket = JdwpPacket.createResponse(packet.id, payload, packet.cmdSet, packet.cmd)
        replyPacket.write(jdwpHandlerOutput)

        return true // don't close connection
    }

    private fun writeJdwpString(oStream: OutputStream, value: String) {
        // See https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/jdwp-spec.html
        //  "String: A UTF-8 encoded string, not zero terminated, preceded by a four-byte
        //  integer length."
        writeJdwpInt(oStream, value.length)
        writeJdwpBytes(oStream, value.toByteArray(Charsets.UTF_8))
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
            JdwpCommandId(JdwpCommands.CmdSet.SET_VM.value, JdwpCommands.VmCmd.CMD_VM_VERSION.value)
    }
}
