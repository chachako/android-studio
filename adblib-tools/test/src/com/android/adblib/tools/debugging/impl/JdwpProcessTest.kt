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

import com.android.adblib.CoroutineScopeCache
import com.android.adblib.DeviceSelector
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.device
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JdwpProcessTest : AdbLibToolsTestBase() {

    @Test
    fun closeClearsCacheAndCancelsScope() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createSession(fakeAdb)
        // Wait until device shows up as a connected device
        yieldUntil { session.connectedDevicesTracker.connectedDevices.value.isNotEmpty() }
        val device = session.connectedDevicesTracker.device(deviceSelector)
        val process = JdwpProcessImpl(session, device, 10)

        // Launch a long job
        val job = process.scope.launch { delay(50_000) }
        // Add cache entries
        val key1 = CoroutineScopeCache.Key<Int>("key1")
        process.cache.getOrPut(key1) { 5 }
        val key2 = CoroutineScopeCache.Key<Int>("key2")
        process.cache.getOrPut(key2) { 10 }

        // Act
        process.close()

        // Assert
        assertTrue(job.isCancelled)
        assertEquals(1_000, process.cache.getOrPut(key1) { 1_000 })
        assertEquals(2_000, process.cache.getOrPut(key2) { 2_000 })
    }
}
