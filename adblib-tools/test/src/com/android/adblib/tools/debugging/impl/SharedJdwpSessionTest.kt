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

import com.android.adblib.AdbSession
import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.skipRemaining
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.tools.debugging.DdmsCommandException
import com.android.adblib.tools.debugging.DdmsProtocolKind
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSessionMonitor
import com.android.adblib.tools.debugging.SharedJdwpSessionMonitorFactory
import com.android.adblib.tools.debugging.addSharedJdwpSessionMonitorFactory
import com.android.adblib.tools.debugging.ddmsProtocolKind
import com.android.adblib.tools.debugging.handleDdmsCaptureView
import com.android.adblib.tools.debugging.handleDdmsHPGC
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.clone
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants
import com.android.adblib.tools.debugging.packets.ddms.MutableDdmsChunk
import com.android.adblib.tools.debugging.packets.ddms.ddmsChunks
import com.android.adblib.tools.debugging.packets.ddms.isDdmsCommand
import com.android.adblib.tools.debugging.packets.ddms.writeToChannel
import com.android.adblib.tools.debugging.sendDdmsExit
import com.android.adblib.tools.debugging.sendVmExit
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.FakeJdwpCommandProgress
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class SharedJdwpSessionTest : AdbLibToolsTestBase() {

    @Test
    fun nextPacketIdIsThreadSafe() = runBlockingWithTimeout {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
        val threadCount = 100
        val packetCount = 1000
        val ids = (1..threadCount)
            .map {
                async {
                    (1..packetCount).map {
                        jdwpSession.nextPacketId()
                    }.toSet()
                }
            }
            .awaitAll()
            .flatten()
            .toSet()

        // Assert
        assertEquals(threadCount * packetCount, ids.size)
    }

    @Test
    fun sendPacketWithActiveReceiverWorks() = runBlockingWithTimeout {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
        val ready = MutableStateFlow(false)

        // Act
        val packet = async {
            ready.first { isReady -> isReady }
            val sendPacket = createHeloDdmsPacket(jdwpSession)
            jdwpSession.sendPacket(sendPacket)
            sendPacket
        }

        val reply = jdwpSession.newPacketReceiver()
            .onActivation { ready.value = true }.flow()
            .filter { it.id == packet.await().id }
            .map { it.clone() /* clone needed to ensure packet is ours to keep */ }
            .first()

        // Assert
        assertEquals(0, reply.errorCode)
        assertEquals(122, reply.length)
        assertEquals(111, reply.payload.countBytes())
    }

    @Test
    fun receiverFlowIsTransparentToExceptions(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)

        // Act
        exceptionRule.expect(Exception::class.java)
        exceptionRule.expectMessage("My Message")
        jdwpSession.newPacketReceiver()
            .withName("Unit Test Receiver")
            .onActivation {
                val sendPacket = createHeloDdmsPacket(jdwpSession)
                jdwpSession.sendPacket(sendPacket)
            }.collect {
                throw Exception("My Message")
            }

        // Assert
        fail("Should not reach")
    }

    @Test
    fun receiverFlowCanBeCancelled(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)

        // Act
        exceptionRule.expect(CancellationException::class.java)
        exceptionRule.expectMessage("My Message")
        jdwpSession.newPacketReceiver()
            .withName("Unit Test Receiver")
            .onActivation {
                val sendPacket = createHeloDdmsPacket(jdwpSession)
                jdwpSession.sendPacket(sendPacket)
            }.collect {
                cancel("My Message")
            }

        // Assert
        fail("Should not reach")
    }


    @Test
    fun receiversCollectionIsSerialized(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val receiverCount = 10
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)

        // Act
        val readyDef = mutableListOf<CompletableDeferred<Unit>>()
        val def = (0 until receiverCount).map { index ->
            readyDef.add(CompletableDeferred())
            async {
                var timeSpan: FlowTimeSpan? = null
                jdwpSession.newPacketReceiver()
                    .onActivation { readyDef[index].complete(Unit) }
                    .flow()
                    .map {
                        val start = System.nanoTime()
                        delay(5)
                        val end = System.nanoTime()
                        timeSpan = FlowTimeSpan(start, end)
                    }.first()
                timeSpan!!
            }
        }

        // wait for all receivers to be active
        readyDef.awaitAll()

        // Send packet, then wait for receivers to collect it
        val sendPacket = createHeloDdmsPacket(jdwpSession)
        jdwpSession.sendPacket(sendPacket)

        // Wait for all receivers to process the packet
        def.awaitAll()

        // Assert
        assertFlowTimeSpansAreSorted(def, receiverCount)
    }

    @Test
    fun receiversActivationsAreNotSerialized(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val receiverCount = 10
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)

        // Act
        val activationCount = AtomicInteger(0)
        val packets = (0 until receiverCount).map {
            async {
                jdwpSession.newPacketReceiver()
                    .onActivation {
                        activationCount.incrementAndGet()
                        // If activations were serialized, this condition would never be satisfied
                        yieldUntil { activationCount.get() == receiverCount }
                    }.flow().first()
            }
        }

        // Wait for all receivers activations to execute
        yieldUntil { activationCount.get() == receiverCount }

        // Send one packet to end the flows
        val packetId = async {
            val sendPacket = createHeloDdmsPacket(jdwpSession)
            jdwpSession.sendPacket(sendPacket)
            sendPacket.id
        }
        packets.awaitAll()

        // Assert
        assertTrue(packets.all { it.await().id ==  packetId.await() })
    }

    @Test
    fun receiverActivationIsNotExecutedIfNoCollection(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val receiverCount = 10
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)

        // Act
        val activationCount = AtomicInteger(0)
        val def = (0 until receiverCount).map {
            async {
                jdwpSession.newPacketReceiver()
                    .onActivation {
                        activationCount.incrementAndGet()
                    }
            }
        }

        jdwpSession.newPacketReceiver()
            .onActivation {
                val sendPacket = createHeloDdmsPacket(jdwpSession)
                jdwpSession.sendPacket(sendPacket)
            }.flow().first()

        // Wait for all receivers activations to execute
        def.awaitAll()

        // Assert
        assertEquals(0, activationCount.get())
    }


    @Test
    fun receivePacketsFlowEndsOnClientTerminate() = runBlockingWithTimeout {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
        val packets = jdwpSession.newPacketReceiver()
            .onActivation {
                val sendPacket = createHeloDdmsPacket(jdwpSession)
                jdwpSession.sendPacket(sendPacket)
            }.flow()
            .map {
                it.clone()
                if (it.isApnmCommand()) {
                    fakeDevice.stopClient(10)
                }
            }
            .toList()

        // Assert: We received `HELO` and `APNM`
        assertTrue(packets.count() == 2)
    }

    @Test
    fun receivePacketsFlowEndsConsistentlyOnClientTerminate() = runBlockingWithTimeout {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
        val packets = jdwpSession.newPacketReceiver()
            .withName("test1")
            .onActivation {
                val sendPacket = createHeloDdmsPacket(jdwpSession)
                jdwpSession.sendPacket(sendPacket)
            }.flow()
            .map {
                it.clone()
                if (it.isApnmCommand()) {
                    fakeDevice.stopClient(10)
                }
            }
            .toList()
        val packets2 = jdwpSession.newPacketReceiver().withName("test2").flow().toList()
        val packets3 = jdwpSession.newPacketReceiver().withName("test3").flow().toList()

        // Assert
        // Assert: We received `HELO` and `APNM`
        assertTrue(packets.count() == 2)
        assertTrue(packets2.isEmpty())
        assertTrue(packets3.isEmpty())
    }

    @Test
    fun sendPacketThrowExceptionAfterClose() = runBlockingWithTimeout {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
        val packet = createHeloDdmsPacket(jdwpSession)
        jdwpSession.sendPacket(packet)

        // Act
        jdwpSession.close()
        exceptionRule.expect(Exception::class.java)
        jdwpSession.sendPacket(packet)

        // Assert
        fail("Should not reach")
    }

    @Test
    fun receivePacketThrowExceptionAfterClose() = runBlockingWithTimeout {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
        val packet = createHeloDdmsPacket(jdwpSession)
        jdwpSession.sendPacket(packet)

        // Act
        jdwpSession.close()

        // Depending on how fast `close` cancels underlying coroutines, we may get
        // an "IOException" from a close channel, or a "CancellationException" from the
        // coroutine cancellation.
        exceptionRule.expect(Exception::class.java)
        jdwpSession.newPacketReceiver()
            .withName("Test")
            .flow()
            .toList()

        // Assert
        fail("Should not reach")
    }

    @Test
    fun receivePacketFlowContainsReplayPackets() = runBlockingWithTimeout {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
        val sendPacket3 = createHeloDdmsPacket(jdwpSession)
        val sendPacket1 = sendPacket3.clone().also { it.id = jdwpSession.nextPacketId() }
        val sendPacket2 = sendPacket3.clone().also { it.id = jdwpSession.nextPacketId() }
        jdwpSession.addReplayPacket(sendPacket1)
        jdwpSession.addReplayPacket(sendPacket2)

        val packets = jdwpSession.newPacketReceiver()
            .withName("Test")
            .onActivation {
                jdwpSession.sendPacket(sendPacket3)
            }
            .flow()
            .map { it.clone() }
            .take(3)
            .toList()


        // Assert
        assertEquals(3, packets.size)
        val cmd1 = packets[0]
        val cmd2 = packets[1]
        val reply1 = packets[2]

        assertEquals(sendPacket1.id, cmd1.id)
        assertEquals(DdmsPacketConstants.DDMS_CMD_SET, cmd1.cmdSet)

        assertEquals(sendPacket2.id, cmd2.id)
        assertEquals(DdmsPacketConstants.DDMS_CMD_SET, cmd1.cmdSet)

        assertEquals(0, reply1.errorCode)
        assertEquals(122, reply1.length)
        assertEquals(111, reply1.payload.countBytes())
    }

    @Test
    fun addReplayPacketDoesCloneJdwpPacket() = runBlockingWithTimeout {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
        val packetToReplay = createHeloDdmsPacket(jdwpSession)
        jdwpSession.addReplayPacket(packetToReplay)

        // Change packet ID to see if replay packet was cloned
        packetToReplay.id++

        val replayPacket = jdwpSession.newPacketReceiver().flow().first()

        // Assert
        assertEquals(packetToReplay.id - 1, replayPacket.id)
    }

    @Test
    fun sendVmExitPacketWorks() = runBlockingWithTimeout {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
        jdwpSession.sendVmExit(1)

        // Assert: Wait until client process is gone
        yieldUntil { fakeDevice.getClient(10) == null }
    }

    @Test
    fun sendDdmsExitPacketWorks() = runBlockingWithTimeout {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        openSharedJdwpSession(session, fakeDevice.deviceId, 10).use { jdwpSession ->
            jdwpSession.sendDdmsExit(1)
        }

        // Assert: Wait until client process is gone
        yieldUntil { fakeDevice.getClient(10) == null }
    }

    @Test
    fun sendDdmsHpgcPacketWorks() = runBlockingWithTimeout {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val client = fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
        val jdwpCommandProgress = FakeJdwpCommandProgress()
        jdwpSession.handleDdmsHPGC(jdwpCommandProgress)

        assertEquals(DdmsProtocolKind.EmptyRepliesDiscarded, jdwpSession.device.ddmsProtocolKind())
        assertEquals(1, client.getHgpcRequestsCount())
        assertTrue(jdwpCommandProgress.beforeSendIsCalled)
        assertTrue(jdwpCommandProgress.afterSendIsCalled)
        assertTrue(jdwpCommandProgress.onReplyTimeoutIsCalled)
        assertFalse(jdwpCommandProgress.onReplyIsCalled)
    }

    @Test
    fun sendDdmsHpgcPacketOnPreApi28DeviceWorks() = runBlockingWithTimeout {
        val fakeDevice = addFakeDevice(fakeAdb, 27)
        val client = fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
        val jdwpCommandProgress = FakeJdwpCommandProgress()
        jdwpSession.handleDdmsHPGC(jdwpCommandProgress)

        assertEquals(DdmsProtocolKind.EmptyRepliesAllowed, jdwpSession.device.ddmsProtocolKind())
        assertEquals(1, client.getHgpcRequestsCount())
        assertTrue(jdwpCommandProgress.beforeSendIsCalled)
        assertTrue(jdwpCommandProgress.afterSendIsCalled)
        assertFalse(jdwpCommandProgress.onReplyTimeoutIsCalled)
        assertTrue(jdwpCommandProgress.onReplyIsCalled)
    }

    @Test
    fun handleInvalidDdmsCommandThrows() = runBlockingWithTimeout {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)

        exceptionRule.expect(DdmsCommandException::class.java)
        jdwpSession.handleDdmsCaptureView("foo", "bar") {
            // Never called
        }

        // Assert: Wait until client process is gone
        fail("Should not reach")
    }

    @Test
    fun sharedJdwpSessionMonitorAreInvokedIfRegistered(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val testJdwpSessionMonitorFactory = TestJdwpSessionMonitorFactory()
        session.addSharedJdwpSessionMonitorFactory(testJdwpSessionMonitorFactory)
        val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)

        // Act
        jdwpSession.newPacketReceiver()
            .withName("Unit Test Receiver")
            .onActivation {
                val sendPacket = createHeloDdmsPacket(jdwpSession)
                jdwpSession.sendPacket(sendPacket)
            }.collect {
                // We got our reply packet, terminate the process so this collector terminates.
                jdwpSession.sendVmExit(5)
            }

        // Assert
        assertEquals(1, testJdwpSessionMonitorFactory.createdMonitors.count())
        val testMonitor = testJdwpSessionMonitorFactory.createdMonitors.first()
        assertTrue(testMonitor.sentPackets.isNotEmpty())
        assertTrue(testMonitor.receivedPackets.isNotEmpty())
    }

    private suspend fun DdmsChunkView.toBufferedInputChannel(): AdbBufferedInputChannel {
        val workBuffer = ResizableBuffer()
        val outputChannel = ByteBufferAdbOutputChannel(workBuffer)
        this.writeToChannel(outputChannel)
        val serializedChunk = workBuffer.forChannelWrite()
        return AdbBufferedInputChannel.forByteBuffer(serializedChunk)
    }

    private suspend fun createHeloDdmsPacket(jdwpSession: SharedJdwpSession): MutableJdwpPacket {
        val heloChunk = MutableDdmsChunk()
        heloChunk.type = DdmsChunkTypes.HELO
        heloChunk.length = 0
        heloChunk.payload = AdbBufferedInputChannel.empty()

        val packet = MutableJdwpPacket()
        packet.id = jdwpSession.nextPacketId()
        packet.length = 11 + 8
        packet.isCommand = true
        packet.cmdSet = DdmsPacketConstants.DDMS_CMD_SET
        packet.cmd = DdmsPacketConstants.DDMS_CMD
        packet.payload = heloChunk.toBufferedInputChannel()
        return packet
    }

    private suspend fun AdbBufferedInputChannel.countBytes(): Int {
        return skipRemaining().also {
            rewind()
        }
    }

    private suspend fun openSharedJdwpSession(
        session: AdbSession,
        deviceSerial: String,
        pid: Int
    ): SharedJdwpSession {
        val connectedDevice = waitForOnlineConnectedDevice(session, deviceSerial)
        val jdwpSession = JdwpSession.openJdwpSession(connectedDevice, pid, 100)
        return registerCloseable(SharedJdwpSession.create(jdwpSession, pid))
    }

    private suspend fun assertFlowTimeSpansAreSorted(
        timeSpans: List<Deferred<FlowTimeSpan>>,
        expectedCount: Int
    ) {
        // Assert expected number of time spans
        assertEquals(expectedCount, timeSpans.size)

        // Assert that the end nano of each entry is <= the start nano of the next entry,
        // which verifies that all "emit" calls to consumer flows were never made
        // concurrently
        val sortedSpans = timeSpans.map { it.await() }.sortedBy { it.startNano }
        sortedSpans.fold(FlowTimeSpan(Long.MIN_VALUE, Long.MIN_VALUE)) { prev, cur ->
            assertTrue(
                "'${prev.endNano} <= ${cur.startNano}' is expected to be true (timespans overlap)",
                prev.endNano <= cur.startNano
            )
            cur
        }
    }

    private suspend fun JdwpPacketView.isApnmCommand(): Boolean {
        return isDdmsCommand &&
                clone().ddmsChunks().firstOrNull { it.type == DdmsChunkTypes.APNM } != null
    }

    class TestJdwpSessionMonitorFactory : SharedJdwpSessionMonitorFactory {
        val createdMonitors = CopyOnWriteArrayList<TestJdwpSessionMonitor>()

        override fun create(session: SharedJdwpSession): SharedJdwpSessionMonitor {
            return TestJdwpSessionMonitor().also {
                createdMonitors.add(it)
            }
        }

        class TestJdwpSessionMonitor : SharedJdwpSessionMonitor {
            val sentPackets = mutableListOf<JdwpPacketView>()
            val receivedPackets = mutableListOf<JdwpPacketView>()
            var closed: Boolean = false

            override suspend fun onSendPacket(packet: JdwpPacketView) {
                sentPackets.add(packet.clone())
            }

            override suspend fun onReceivePacket(packet: JdwpPacketView) {
                receivedPackets.add(packet.clone())
            }

            override fun close() {
                closed = true
            }
        }
    }

    data class FlowTimeSpan(val startNano: Long, val endNano: Long)
}
