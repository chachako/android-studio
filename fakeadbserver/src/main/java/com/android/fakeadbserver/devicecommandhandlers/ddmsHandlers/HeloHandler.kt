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
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

class HeloHandler : DDMPacketHandler {

    override fun handlePacket(
        device: DeviceState,
        client: ClientState,
        packet: DdmPacket,
        oStream: OutputStream
    ): Boolean {
        // ADB has an issue of reporting the process name instead of the real not reporting the real package name.
        val appName = client.processName
        val deviceApiLevel = device.apiLevel

        // UserID starts at API 18
        val writeUserId = deviceApiLevel >= 18

        // ABI starts at API 21
        val writeAbi = deviceApiLevel >= 21
        val abi = device.cpuAbi

        // JvmFlags starts at API 21
        val writeJvmFlags = deviceApiLevel >= 21
        val jvmFlags = JVM_FLAGS

        // native debuggable starts at API 24
        val writeNativeDebuggable = deviceApiLevel >= 24

        // package name starts at API 30
        val writePackageName = deviceApiLevel >= 30
        val packageName = client.packageName

        val payloadLength = (HELO_CHUNK_HEADER_LENGTH + (VM_IDENTIFIER.length + appName.length) * 2
                + (if (writeUserId) 4 else 0)
                + (if (writeAbi) 4 + abi.length * 2 else 0)
                + (if (writeJvmFlags) 4 + jvmFlags.length * 2 else 0)
                + (if (writeNativeDebuggable) 1 else 0)
                + if (writePackageName) 4 + packageName.length * 2 else 0)
        val payload = ByteArray(payloadLength)
        val payloadBuffer = ByteBuffer.wrap(payload)
        payloadBuffer.putInt(VERSION)
        payloadBuffer.putInt(client.pid)
        payloadBuffer.putInt(VM_IDENTIFIER.length)
        payloadBuffer.putInt(appName.length)
        for (c in VM_IDENTIFIER.toCharArray()) {
            payloadBuffer.putChar(c)
        }
        for (c in appName.toCharArray()) {
            payloadBuffer.putChar(c)
        }
        if (writeUserId) {
            payloadBuffer.putInt(client.uid)
        }
        if (writeAbi) {
            payloadBuffer.putInt(abi.length)
            for (c in abi.toCharArray()) {
                payloadBuffer.putChar(c)
            }
        }
        if (writeJvmFlags) {
            payloadBuffer.putInt(jvmFlags.length)
            for (c in jvmFlags.toCharArray()) {
                payloadBuffer.putChar(c)
            }
        }
        if (writeNativeDebuggable) {
            payloadBuffer.put(0.toByte())
        }
        if (writePackageName) {
            payloadBuffer.putInt(packageName.length)
            for (c in packageName.toCharArray()) {
                payloadBuffer.putChar(c)
            }
        }

        val responsePacket = DdmPacket.createResponse(packet.id, CHUNK_TYPE, payload)
        try {
            responsePacket.write(oStream)
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }

        // Send "APMN" command packet as to simulate Android behavior
        try {
            val apnmPayloadLength = (4 + appName.length * 2
                    + (if (writeUserId) 4 else 0)
                    + if (writePackageName) 4 + packageName.length * 2 else 0)
            val apmnPayload = ByteBuffer.allocate(apnmPayloadLength)

            // Process Name
            apmnPayload.putInt(appName.length)
            for (c in appName.toCharArray()) {
                apmnPayload.putChar(c)
            }

            // User ID
            if (writeUserId) {
                apmnPayload.putInt(client.uid)
            }

            // Package Name
            if (writePackageName) {
                apmnPayload.putInt(packageName.length)
                for (c in packageName.toCharArray()) {
                    apmnPayload.putChar(c)
                }
            }

            val apnmPacket = DdmPacket.createCommand(
                client.nextDdmsCommandId(),
                DdmPacket.encodeChunkType("APNM"),
                apmnPayload.array()
            )
            apnmPacket.write(oStream)
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }

        // Send "WAIT" packet if needed
        if (client.isWaiting) {
            val waitPayload = ByteArray(1)
            val waitPacket = DdmPacket.createCommand(
                client.nextDdmsCommandId(),
                DdmPacket.encodeChunkType("WAIT"),
                waitPayload
            )
            try {
                waitPacket.write(oStream)
            } catch (e: IOException) {
                e.printStackTrace()
                return false
            }
        }
        return true
    }

    companion object {

        @JvmField
        val CHUNK_TYPE = DdmPacket.encodeChunkType("HELO")
        private const val VM_IDENTIFIER = "FakeVM"
        private const val JVM_FLAGS = "-jvmflag=true"
        private const val HELO_CHUNK_HEADER_LENGTH = 16
        private const val VERSION = 9999
    }
}
