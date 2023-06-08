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
package com.android.adblib.tools.debugging.packets

import com.android.adblib.tools.debugging.impl.EphemeralJdwpPacket
import com.android.adblib.tools.debugging.packets.JdwpCommands.CmdSet.SET_THREADREF
import com.android.adblib.tools.debugging.packets.JdwpCommands.ThreadRefCmd.CMD_THREADREF_NAME
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class EphemeralJdwpPacketTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @Test
    fun testPacketCommandProperties() {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 1_000_000
        sourcePacket.id = -10
        sourcePacket.cmdSet = 160
        sourcePacket.cmd = 240

        // Act
        val packet = EphemeralJdwpPacket.fromPacket(sourcePacket, AdbBufferedInputChannel.empty())

        // Assert
        assertEquals(1_000_000, packet.length)
        assertEquals(-10, packet.id)
        assertEquals(0, packet.flags)
        assertTrue(packet.isCommand)
        assertFalse(packet.isReply)
        assertEquals(160, packet.cmdSet)
        assertEquals(240, packet.cmd)
    }

    @Test
    fun testPacketReplyProperties() {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 1_000_000
        sourcePacket.id = -10
        sourcePacket.isReply = true
        sourcePacket.errorCode = 1_000

        // Act
        val packet = EphemeralJdwpPacket.fromPacket(sourcePacket, AdbBufferedInputChannel.empty())

        // Assert
        assertEquals(1_000_000, packet.length)
        assertEquals(-10, packet.id)
        assertFalse(packet.isCommand)
        assertTrue(packet.isReply)
        assertEquals(1_000, packet.errorCode)
    }

    @Test
    fun testPacketThrowsIfInvalidLength() {
        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        EphemeralJdwpPacket(id = 5,
                            length = 5,
                            flags = 0,
                            cmdSet = 0,
                            cmd = 0,
                            errorCode = 0,
                            PayloadProvider.emptyPayload())

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketThrowsIfInvalidCmdSet() {
        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        EphemeralJdwpPacket(id = 5,
                            length = 5,
                            flags = 0,
                            cmdSet = -10,
                            cmd = 0,
                            errorCode = 0,
                            PayloadProvider.emptyPayload())

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketThrowsIfInvalidCmd() {
        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        EphemeralJdwpPacket(id = 5,
                            length = 5,
                            flags = 0,
                            cmdSet = 0,
                            cmd = -10,
                            errorCode = 0,
                            PayloadProvider.emptyPayload())

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketThrowsIfInvalidErrorCode() {
        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        EphemeralJdwpPacket(id = 5,
                            length = 5,
                            flags = 0,
                            cmdSet = 0,
                            cmd = 0,
                            errorCode = -10,
                            PayloadProvider.emptyPayload())

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketThrowsIfInvalidFlags() {
        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        EphemeralJdwpPacket(id = 5,
                            length = 5,
                            flags = -127,
                            cmdSet = 0,
                            cmd = 0,
                            errorCode = 0,
                            PayloadProvider.emptyPayload())

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketThrowsOnErrorCodeIfCommand() {
        // Prepare
        val packet = EphemeralJdwpPacket.Command(
            id = 10,
            length = 20,
            cmdSet = 10,
            cmd = 10,
            payloadProvider = PayloadProvider.emptyPayload()
        )

        // Act
        exceptionRule.expect(IllegalStateException::class.java)
        @Suppress("UNUSED_VARIABLE")
        val errorCode = packet.errorCode

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketThrowsOnCmdSetIfReply() {
        // Prepare
        val packet = EphemeralJdwpPacket.Reply(
            id = 10,
            length = 20,
            errorCode = 10,
            payloadProvider = PayloadProvider.emptyPayload()
        )

        // Act
        exceptionRule.expect(IllegalStateException::class.java)
        @Suppress("UNUSED_VARIABLE")
        val cmdSet = packet.cmdSet

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketThrowsOnCmdIfReply() {
        // Prepare
        val packet = EphemeralJdwpPacket.Reply(
            id = 10,
            length = 20,
            errorCode = 10,
            payloadProvider = PayloadProvider.emptyPayload()
        )

        // Act
        exceptionRule.expect(IllegalStateException::class.java)
        @Suppress("UNUSED_VARIABLE")
        val cmd = packet.cmd

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketToStringForCommandPacket() {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 11
        sourcePacket.id = 10
        sourcePacket.cmdSet = SET_THREADREF.value
        sourcePacket.cmd = CMD_THREADREF_NAME.value

        // Act
        val packet = EphemeralJdwpPacket.fromPacket(sourcePacket, PayloadProvider.emptyPayload())
        val text = packet.toString()

        // Assert
        assertEquals("JdwpPacket(id=10, length=11, flags=0x00, isCommand=true, cmdSet=SET_THREADREF[11], cmd=CMD_THREADREF_NAME[1])", text)
    }

    @Test
    fun testPacketToStringForReplyPacket() {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 11
        sourcePacket.id = 10
        sourcePacket.isReply = true
        sourcePacket.errorCode = 67

        // Act
        val packet = EphemeralJdwpPacket.fromPacket(sourcePacket, PayloadProvider.emptyPayload())
        val text = packet.toString()

        // Assert
        assertEquals("JdwpPacket(id=10, length=11, flags=0x80, isReply=true, errorCode=DELETE_METHOD_NOT_IMPLEMENTED[67])", text)
    }

}
