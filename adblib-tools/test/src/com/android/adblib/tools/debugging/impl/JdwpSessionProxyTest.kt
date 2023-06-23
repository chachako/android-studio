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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpCommands
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.PayloadProvider
import com.android.adblib.tools.debugging.packets.clone
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkType
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants
import com.android.adblib.tools.debugging.packets.ddms.EphemeralDdmsChunk
import com.android.adblib.tools.debugging.packets.ddms.writeToChannel
import com.android.adblib.tools.debugging.properties
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import com.android.adblib.utils.ResizableBuffer
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException

class JdwpSessionProxyTest : AdbLibToolsTestBase() {

    @Test
    fun socketAddressIsAssignedAutomatically() = runBlockingWithTimeout {
        val deviceID = "1234"
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "30", // SDK >= 30 is required for abb_exec feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice = waitForOnlineConnectedDevice(session, fakeDevice.deviceId)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val process = registerCloseable(JdwpProcessImpl(session, connectedDevice,  10))
        process.startMonitoring()
        yieldUntil {
            process.properties.jdwpSessionProxyStatus.socketAddress != null
        }

        // Assert
        assertFalse(process.properties.jdwpSessionProxyStatus.isExternalDebuggerAttached)
    }

    @Test
    fun socketAddressSupportsJdwpSession() = runBlockingWithTimeout {
        // Prepare
        val jdwpSession = createJdwpProxySession()

        // Act
        val packet = createVmVersionPacket(jdwpSession)

        val reply = async {
            val replyPacket: JdwpPacketView
            while (true) {
                val r = jdwpSession.receivePacket()
                if (r.id == packet.id) {
                    replyPacket = r
                    break
                }
            }
            replyPacket
        }

        jdwpSession.sendPacket(packet)

        // Assert
        assertTrue(reply.await().isReply)
        assertEquals(packet.id, reply.await().id)
    }

    @Test
    fun proxyFiltersDdmsCommandPackets() = runBlockingWithTimeout {
        // Prepare
        val jdwpSession = createJdwpProxySession()

        // Act
        val ddmsPacket = createDdmsHeloPacket(jdwpSession)
        val vmVersionPacket = createVmVersionPacket(jdwpSession)

        val repliesAsync = async {
            jdwpSession.receivedPacketFlow()
                .transformWhile {
                    emit(it.clone())
                    it.id != vmVersionPacket.id
                }
                .toList()
        }

        jdwpSession.sendPacket(ddmsPacket)
        jdwpSession.sendPacket(vmVersionPacket)

        // Assert: We should receive the "VM_VERSION" JDWP packet, but not the DDMS "HELO" packet
        val replies = repliesAsync.await()
        assertEquals(1, replies.size)
        assertEquals(vmVersionPacket.id, replies[0].id)
    }

    private suspend fun createJdwpProxySession(): JdwpSession {
        val deviceID = "1234"
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "30", // SDK >= 30 is required for abb_exec feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice = waitForOnlineConnectedDevice(session, fakeDevice.deviceId)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        val process = registerCloseable(JdwpProcessImpl(session, connectedDevice, 10))
        process.startMonitoring()
        yieldUntil {
            process.properties.jdwpSessionProxyStatus.socketAddress != null &&
                    process.properties.processName != null
        }
        val clientSocket = registerCloseable(
            session.channelFactory.connectSocket(
                process.properties.jdwpSessionProxyStatus.socketAddress!!
            )
        )
        return registerCloseable(
            JdwpSession.wrapSocketChannel(
                connectedDevice,
                clientSocket,
                10,
                2_000
            )
        )
    }

    private fun createVmVersionPacket(jdwpSession: JdwpSession): MutableJdwpPacket {
        val packet = MutableJdwpPacket()
        packet.id = jdwpSession.nextPacketId()
        packet.length = 11
        packet.isCommand = true
        packet.cmdSet = JdwpCommands.CmdSet.SET_VM.value
        packet.cmd = JdwpCommands.VmCmd.CMD_VM_VERSION.value
        return packet
    }

    private suspend fun createDdmsHeloPacket(jdwpSession: JdwpSession): MutableJdwpPacket {
        val heloChunk = EphemeralDdmsChunk(
            type = DdmsChunkType.HELO,
            length = 0,
            payloadProvider = PayloadProvider.emptyPayload()
        )

        val packet = MutableJdwpPacket()
        packet.id = jdwpSession.nextPacketId()
        packet.length = 11 + 8
        packet.isCommand = true
        packet.cmdSet = DdmsPacketConstants.DDMS_CMD_SET
        packet.cmd = DdmsPacketConstants.DDMS_CMD
        packet.payloadProvider = PayloadProvider.forInputChannel(heloChunk.toBufferedInputChannel())
        return packet
    }

    private suspend fun DdmsChunkView.toBufferedInputChannel(): AdbBufferedInputChannel {
        val workBuffer = ResizableBuffer()
        val outputChannel = ByteBufferAdbOutputChannel(workBuffer)
        this.writeToChannel(outputChannel)
        val serializedChunk = workBuffer.forChannelWrite()
        return AdbBufferedInputChannel.forByteBuffer(serializedChunk)
    }

    private fun JdwpSession.receivedPacketFlow() = flow {
        while (true) {
            val packet = try {
                this@receivedPacketFlow.receivePacket()
            } catch (e: EOFException) {
                // Reached EOF, flow terminates
                break
            }
            emit(packet)
        }
    }
}
