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

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.properties
import com.android.adblib.tools.testutils.AdbLibToolsJdwpTestBase
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class JdwpSessionProxyTest : AdbLibToolsJdwpTestBase() {

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
        val process = registerCloseable(JdwpProcessFactory.create(connectedDevice, 10))
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
        val jdwpSession = createJdwpProxySession(pid = 10).debuggerJdwpSession

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
    fun externalDebuggerCanDetachAndReAttach() = runBlockingWithTimeout {
        // Prepare
        val jdwpSessionInfo = createJdwpProxySession(pid = 11)
        val jdwpProcess = jdwpSessionInfo.process
        val debuggerSocketAddress = jdwpProcess.properties.jdwpSessionProxyStatus.socketAddress

        // Act
        val jdwpSession1 = jdwpSessionInfo.debuggerJdwpSession
        val reply1 = sendVmVersionPacket(jdwpSession1)

        // Close JDWP session and wait for process to reflect new status
        jdwpSession1.close()
        yieldUntil {
            !jdwpProcess.properties.jdwpSessionProxyStatus.isExternalDebuggerAttached
        }
        val debuggerSocketAddress2 = jdwpProcess.properties.jdwpSessionProxyStatus.socketAddress

        // Open 2nd session
        val jdwpSession2 = attachDebuggerSession(jdwpProcess)
        val reply2 = sendVmVersionPacket(jdwpSession2)

        // Assert
        assertTrue(jdwpProcess.properties.jdwpSessionProxyStatus.isExternalDebuggerAttached)
        assertEquals(debuggerSocketAddress, debuggerSocketAddress2)
        assertTrue(reply1.isReply)
        assertTrue(reply2.isReply)
    }

    @Test
    fun debuggerSessionEndsWhenDeviceDisconnects(): Unit = runBlockingWithTimeout {
        // Prepare
        val jdwpSessionInfo = createJdwpProxySession(pid = 12)
        val jdwpSession = jdwpSessionInfo.debuggerJdwpSession
        sendVmVersionPacket(jdwpSession)

        // Act
        fakeAdb.disconnectDevice(jdwpSessionInfo.fakeDevice.deviceId)

        // Assert
        // Note: On Windows, we get "java.io.IOException: The specified network name is no longer available"
        //       On Linux/Mac, we get "java.io.EOFException: Unexpected end of channel"
        exceptionRule.expect(IOException::class.java)
        jdwpSession.receivePacket()
    }

    @Test
    fun proxyFiltersDdmsCommandPackets() = runBlockingWithTimeout {
        // Prepare
        val jdwpSession = createJdwpProxySession(pid = 13).debuggerJdwpSession

        // Act
        val ddmsPacket = createDdmsHeloPacket(jdwpSession)
        val vmVersionPacket = createVmVersionPacket(jdwpSession)

        val repliesAsync = async {
            jdwpSession.receivedPacketFlow()
                .transformWhile {
                    emit(it.toOffline())
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
}
