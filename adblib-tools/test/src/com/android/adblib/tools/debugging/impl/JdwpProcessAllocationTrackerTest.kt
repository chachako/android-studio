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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.testingutils.CoroutineTestUtils
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.tools.debugging.JdwpProcessAllocationTracker
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants
import com.android.adblib.tools.debugging.toByteBuffer
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.FakeJdwpCommandProgress
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import com.android.fakeadbserver.DeviceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JdwpProcessAllocationTrackerTest : AdbLibToolsTestBase() {

    @Test
    fun getEnabledStatus_returnsResult() =
        CoroutineTestUtils.runBlockingWithTimeout {
            val processAllocationTracker = createJdwpProcessAllocationTracker(fakeAdb)
            fakeAdb.device("1234").getClient(10)?.isAllocationTrackerEnabled = true

            // Act
            val jdwpCommandProgress = FakeJdwpCommandProgress()
            val result = processAllocationTracker.isEnabled(jdwpCommandProgress)

            // Assert
            assertEquals(true, result)
            assertTrue(jdwpCommandProgress.beforeSendIsCalled)
            assertTrue(jdwpCommandProgress.afterSendIsCalled)
            assertTrue(jdwpCommandProgress.onReplyIsCalled)
        }

    @Test
    fun setEnabledWorks() =
        CoroutineTestUtils.runBlockingWithTimeout {
            val processAllocationTracker = createJdwpProcessAllocationTracker(fakeAdb)

            // Act
            val jdwpCommandProgress = FakeJdwpCommandProgress()
            processAllocationTracker.enable(true, jdwpCommandProgress)

            // Assert
            assertEquals(true, fakeAdb.device("1234").getClient(10)?.isAllocationTrackerEnabled)
            assertTrue(jdwpCommandProgress.beforeSendIsCalled)
            assertTrue(jdwpCommandProgress.afterSendIsCalled)
            assertTrue(jdwpCommandProgress.onReplyTimeoutIsCalled) // Empty reply and API > 27
        }

    @Test
    fun fetchAllocationDetailsReturnsResult() =
        CoroutineTestUtils.runBlockingWithTimeout {
            val processAllocationTracker = createJdwpProcessAllocationTracker(fakeAdb)
            val allocationDetails = "some data"
            fakeAdb.device("1234").getClient(10)?.allocationTrackerDetails = allocationDetails

            // Act
            val jdwpCommandProgress = FakeJdwpCommandProgress()
            val allocationDetailsResponse =
                processAllocationTracker.fetchAllocationDetails(jdwpCommandProgress) { data, length ->
                    val dataBuffer =
                        data.toByteBuffer(length).order(DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER)
                    val resultLength = dataBuffer.int
                    val result = CharArray(resultLength)
                    for (i in 0 until resultLength) {
                        result[i] = dataBuffer.char
                    }
                    String(result)
                }

            // Assert
            assertEquals(allocationDetails, allocationDetailsResponse)
            assertTrue(jdwpCommandProgress.beforeSendIsCalled)
            assertTrue(jdwpCommandProgress.afterSendIsCalled)
            assertTrue(jdwpCommandProgress.onReplyIsCalled)
        }

    private suspend fun createJdwpProcessAllocationTracker(fakeAdb: FakeAdbServerProvider): JdwpProcessAllocationTracker {
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
        val process = registerCloseable(JdwpProcessFactory.create(connectedDevice, 10))
        // Note: We don't currently need to collect process properties for the profiler API to work
        return JdwpProcessAllocationTrackerImpl(process)
    }
}
