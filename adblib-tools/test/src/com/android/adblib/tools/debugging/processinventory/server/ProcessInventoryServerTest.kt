/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adblib.tools.debugging.processinventory.server

import com.android.adblib.AdbLogger
import com.android.adblib.AdbServerSocket
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.CoroutineTestUtils
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.tools.debugging.processinventory.impl.ProcessInventoryServerSocketProtocol
import com.android.adblib.tools.debugging.processinventory.protos.ProcessInventoryServerProto
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class ProcessInventoryServerTest {
    @JvmField
    @Rule
    val closeables = CloseablesRule()

    @Test
    fun testServerWorksWithInitialState(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val session = createAdbSession()
        val localServerSocket = startServer(session)

        // Act
        val protocol = connectToServer(session, localServerSocket)
        val response = protocol.forClient("foo").trackDevice("123").first()

        // Assert
        assertTrue(response.ok)
        assertTrue(response.hasTrackDeviceResponsePayload())
        assertTrue(response.trackDeviceResponsePayload.hasProcessUpdates())
        assertEquals(0, response.trackDeviceResponsePayload.processUpdates.processUpdateCount)
    }

    @Test
    fun testServerSendsUpdatesWhenProcessIsAdded(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val deviceSerial = "123"
        val session = createAdbSession()
        val localServerSocket = startServer(session)

        // Act
        val trackerResponses = mutableListOf<ProcessInventoryServerProto.Response>()
        val deferred = async {
            connectToServer(session, localServerSocket).forClient("foo").trackDevice(deviceSerial).collect {
                trackerResponses.add(it)
            }
        }

        yieldUntil {
            trackerResponses.size == 1
        }

        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 124)

        yieldUntil {
            trackerResponses.size == 2
        }
        deferred.cancel("Test is finished")

        // Assert
        assertEquals(2, trackerResponses.size)
        trackerResponses[1].also { response ->
            assertTrue(response.ok)
            assertTrue(response.hasTrackDeviceResponsePayload())
            assertTrue(response.trackDeviceResponsePayload.hasProcessUpdates())
            assertEquals(1, response.trackDeviceResponsePayload.processUpdates.processUpdateCount)
            assertTrue(response.trackDeviceResponsePayload.processUpdates.getProcessUpdate(0).hasProcessUpdated())
            assertEquals(124, response.trackDeviceResponsePayload.processUpdates.getProcessUpdate(0).processUpdated.pid)
        }
    }

    @Test
    fun testServerDoesNotSendUpdatesWhenNoChanges(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val deviceSerial = "123"
        val session = createAdbSession()
        val localServerSocket = startServer(session)

        // Act
        val trackerResponses = mutableListOf<ProcessInventoryServerProto.Response>()
        val deferred = async {
            connectToServer(session, localServerSocket).forClient("foo").trackDevice(deviceSerial).collect {
                trackerResponses.add(it)
            }
        }
        yieldUntil { trackerResponses.size == 1 }

        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 124)
        yieldUntil { trackerResponses.size == 2 }

        // Send the same update process info a 2nd time, and check the server
        // does not send a new tracking event.
        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 124)

        delay(500)
        deferred.cancel("Test is finished")

        // Assert
        assertEquals(2, trackerResponses.size)
        trackerResponses[1].also { response ->
            assertTrue(response.ok)
            assertTrue(response.hasTrackDeviceResponsePayload())
            assertTrue(response.trackDeviceResponsePayload.hasProcessUpdates())
            assertEquals(1, response.trackDeviceResponsePayload.processUpdates.processUpdateCount)
            assertTrue(response.trackDeviceResponsePayload.processUpdates.getProcessUpdate(0).hasProcessUpdated())
            assertEquals(124, response.trackDeviceResponsePayload.processUpdates.getProcessUpdate(0).processUpdated.pid)
        }
    }

    @Test
    fun testServerSendsUpdateWhenProcessInfoIsUpdated(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val deviceSerial = "123"
        val session = createAdbSession()
        val localServerSocket = startServer(session)

        // Act
        val trackerResponses = mutableListOf<ProcessInventoryServerProto.Response>()
        val deferred = async {
            connectToServer(session, localServerSocket).forClient("foo").trackDevice(deviceSerial).collect {
                trackerResponses.add(it)
            }
        }
        yieldUntil { trackerResponses.size == 1 }

        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 124)
        yieldUntil { trackerResponses.size == 2 }

        // Send the same update process info a 2nd time, and check the server
        // does not send a new tracking event.
        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 124, processName = "foo", packageName = "bar")
        yieldUntil { trackerResponses.size == 3 }

        deferred.cancel("Test is finished")

        // Assert
        assertEquals(3, trackerResponses.size)
        trackerResponses[1].also { response ->
            assertTrue(response.ok)
            assertTrue(response.hasTrackDeviceResponsePayload())
            assertTrue(response.trackDeviceResponsePayload.hasProcessUpdates())
            assertEquals(1, response.trackDeviceResponsePayload.processUpdates.processUpdateCount)
            assertEquals(124, response.trackDeviceResponsePayload.processUpdates.getProcessUpdate(0).processUpdated.pid)
        }
        trackerResponses[2].also { response ->
            assertTrue(response.ok)
            assertTrue(response.hasTrackDeviceResponsePayload())
            assertTrue(response.trackDeviceResponsePayload.hasProcessUpdates())
            assertEquals(1, response.trackDeviceResponsePayload.processUpdates.processUpdateCount)
            assertTrue(response.trackDeviceResponsePayload.processUpdates.getProcessUpdate(0).hasProcessUpdated())
            assertEquals(124, response.trackDeviceResponsePayload.processUpdates.getProcessUpdate(0).processUpdated.pid)
            assertEquals("foo", response.trackDeviceResponsePayload.processUpdates.getProcessUpdate(0).processUpdated.processName)
            assertEquals("bar", response.trackDeviceResponsePayload.processUpdates.getProcessUpdate(0).processUpdated.packageName)
        }
    }

    @Test
    fun testServerSendsUpdateWhenProcessIsTerminated(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val deviceSerial = "123"
        val session = createAdbSession()
        val localServerSocket = startServer(session)

        // Act
        val trackerResponses = mutableListOf<ProcessInventoryServerProto.Response>()
        val deferred = async {
            connectToServer(session, localServerSocket)
                .forClient("foo")
                .trackDevice(deviceSerial).collect {
                    trackerResponses.add(it)
                }
        }
        yieldUntil { trackerResponses.size == 1 }

        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 124)
        yieldUntil { trackerResponses.size == 2 }

        sendDeviceProcessTermination(session, localServerSocket, deviceSerial, pid = 124)
        yieldUntil { trackerResponses.size == 3 }

        // Send the same update process info a 2nd time, and check the server
        // does not send a new tracking event.
        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 124, processName = "foo", packageName = "bar")
        yieldUntil { trackerResponses.size == 3 }

        deferred.cancel("Test is finished")

        // Assert
        assertEquals(3, trackerResponses.size)
        trackerResponses[1].also { response ->
            assertTrue(response.ok)
            assertTrue(response.hasTrackDeviceResponsePayload())
            assertTrue(response.trackDeviceResponsePayload.hasProcessUpdates())
            assertEquals(1, response.trackDeviceResponsePayload.processUpdates.processUpdateCount)
            assertEquals(124, response.trackDeviceResponsePayload.processUpdates.getProcessUpdate(0).processUpdated.pid)
        }
        trackerResponses[2].also { response ->
            assertTrue(response.ok)
            assertTrue(response.hasTrackDeviceResponsePayload())
            assertTrue(response.trackDeviceResponsePayload.hasProcessUpdates())
            assertEquals(1, response.trackDeviceResponsePayload.processUpdates.processUpdateCount)
            assertTrue(response.trackDeviceResponsePayload.processUpdates.getProcessUpdate(0).hasProcessTerminatedPid())
            assertEquals(124, response.trackDeviceResponsePayload.processUpdates.getProcessUpdate(0).processTerminatedPid)
        }
    }

    @Test
    fun testServerCanTrackManyProcesses(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val deviceSerial = "123"
        val session = createAdbSession()
        val localServerSocket = startServer(session)

        // Act
        val trackerResponses = CopyOnWriteArrayList<ProcessInventoryServerProto.Response>()
        val deferred = async {
            connectToServer(session, localServerSocket).forClient("foo").trackDevice(deviceSerial).collect {
                trackerResponses.add(it)
            }
        }
        var expectedSize = 1
        yieldUntil { trackerResponses.size == expectedSize }

        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 124)
        expectedSize++
        yieldUntil { trackerResponses.size == expectedSize }

        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 125, processName = "p1")
        expectedSize++
        yieldUntil { trackerResponses.size == expectedSize }

        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 126, packageName = "pn2")
        expectedSize++
        yieldUntil { trackerResponses.size == expectedSize }

        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 127, processName = "foo", packageName = "bar")
        expectedSize++
        yieldUntil { trackerResponses.size == expectedSize }

        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 128)
        expectedSize++
        yieldUntil { trackerResponses.size == expectedSize }

        deferred.cancel("Test is finished")

        // Assert
        trackerResponses.last().also { response ->
            assertTrue(response.ok)
            assertTrue(response.hasTrackDeviceResponsePayload())
            assertTrue(response.trackDeviceResponsePayload.hasProcessUpdates())
            assertEquals(1, response.trackDeviceResponsePayload.processUpdates.processUpdateCount)
        }
    }

    @Test
    fun testServerInitialListIsPopulated(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val deviceSerial = "123"
        val session = createAdbSession()
        val localServerSocket = startServer(session)

        // Act
        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 124)
        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 125, processName = "p1")
        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 126, packageName = "pn2")
        sendDeviceProcess(session, localServerSocket, deviceSerial, pid = 127, processName = "foo", packageName = "bar")

        val trackerResponses = CopyOnWriteArrayList<ProcessInventoryServerProto.Response>()
        val deferred = async {
            connectToServer(session, localServerSocket).forClient("foo").trackDevice(deviceSerial).collect {
                trackerResponses.add(it)
            }
        }
        yieldUntil { trackerResponses.size == 1 }
        deferred.cancel("Test is finished")

        // Assert
        trackerResponses.last().also { response ->
            assertTrue(response.ok)
            assertTrue(response.hasTrackDeviceResponsePayload())
            assertTrue(response.trackDeviceResponsePayload.hasProcessUpdates())
            assertEquals(4, response.trackDeviceResponsePayload.processUpdates.processUpdateCount)
        }
    }

    private fun createAdbSession(): FakeAdbSession {
        return FakeAdbSession().also {
            it.host.loggerFactory.minLevel = AdbLogger.Level.INFO
            registerCloseable(it)
        }
    }

    private suspend fun startServer(session: FakeAdbSession): AdbServerSocket {
        val config = object : ProcessInventoryServerConfiguration {
            override val clientDescription: String = "foo"
            override val serverDescription: String = "bar"
        }
        val server = registerCloseable(ProcessInventoryServer(session, config))
        val localServerSocket = createLocalServerSocket(session)
        server.launch(localServerSocket)
        return localServerSocket
    }

    private suspend fun connectToServer(
        session: FakeAdbSession,
        localServerSocket: AdbServerSocket
    ): ProcessInventoryServerSocketProtocol {
        val clientSocket = registerCloseable(session.channelFactory.connectSocket(localServerSocket.localAddress()!!))
        val protocol = ProcessInventoryServerSocketProtocol(session, clientSocket)
        return protocol
    }

    private suspend fun createLocalServerSocket(session: FakeAdbSession): AdbServerSocket {
        return registerCloseable(session.channelFactory.createServerSocket().also { it.bind() })
    }

    private suspend fun sendDeviceProcess(
        session: FakeAdbSession,
        localServerSocket: AdbServerSocket,
        deviceSerial: String,
        pid: Int,
        processName: String? = null,
        packageName: String? = null,
    ) {
        val protocol = connectToServer(session, localServerSocket)
        val deviceUpdateResponse = protocol.forClient("foo").sendDeviceProcessInfo(
            deviceSerial,
            ProcessInventoryServerProto.JdwpProcessInfo
                .newBuilder()
                .setPid(pid).also { proto ->
                    processName?.also { proto.setProcessName(it) }
                    packageName?.also { proto.setPackageName(it) }
                }
                .build()
        )
        assertTrue(
            "Response is not ok: ${deviceUpdateResponse.errorMessage}",
            deviceUpdateResponse.ok
        )
    }

    private suspend fun sendDeviceProcessTermination(
        session: FakeAdbSession,
        localServerSocket: AdbServerSocket,
        deviceSerial: String,
        pid: Int
    ) {
        val protocol = connectToServer(session, localServerSocket)
        val deviceUpdateResponse = protocol.forClient("foo").sendDeviceProcessRemoval(
            deviceSerial,
            pid
        )
        assertTrue(
            "Response is not ok: ${deviceUpdateResponse.errorMessage}",
            deviceUpdateResponse.ok
        )
    }

    private fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }
}
