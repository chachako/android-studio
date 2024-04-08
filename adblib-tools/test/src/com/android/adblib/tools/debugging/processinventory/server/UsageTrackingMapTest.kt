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
import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.CoroutineTestUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.toKotlinDuration

class UsageTrackingMapTest {

    @JvmField
    @Rule
    val closeables = CloseablesRule()

    @Test
    fun testValueIsReused(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val fakeClock = FakeClock()
        val removalDelay = Duration.ofMillis(10)
        val map = createMap(removalDelay, fakeClock)

        // Act
        val value1 = map.withValue(10) { it }
        val value2 = map.withValue(10) { it }

        // Assert
        Assert.assertSame(value1, value2)
    }

    @Test
    fun testValueIsRemoved(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val fakeClock = FakeClock()
        val removalDelay = Duration.ofMillis(10)
        val map = createMap(removalDelay, fakeClock)

        // Act
        val value1 = map.withValue(10) { it }
        fakeClock.nowValue = fakeClock.nowValue.plus(removalDelay.multipliedBy(2))
        delay(removalDelay.multipliedBy(5).toKotlinDuration())
        val value2 = map.withValue(10) { it }

        // Assert
        Assert.assertNotSame(value1, value2)
    }

    @Test
    fun testValueIsKeptAfterReuse(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val fakeClock = FakeClock()
        val removalDelay = Duration.ofMillis(10)
        val map = createMap(removalDelay, fakeClock)

        // Act
        val value1 = map.withValue(10) { it }
        fakeClock.nowValue = fakeClock.nowValue.plus(removalDelay.dividedBy(2))
        map.removeAllUnused()
        val value2 = map.withValue(10) { it }
        fakeClock.nowValue = fakeClock.nowValue.plus(removalDelay)
        delay(removalDelay.multipliedBy(5).toKotlinDuration())
        val value3 = map.withValue(10) { it }

        // Assert
        Assert.assertSame(value1, value2)
        Assert.assertNotSame(value2, value3)
    }

    private fun createMap(
        removalDelay: Duration,
        fakeClock: FakeClock
    ): UsageTrackingMap<Int, MyValue> {
        val logger = FakeAdbLoggerFactory().also {
            it.minLevel = AdbLogger.Level.INFO
        }
        val scope = registerCloseable(CloseableScope()).scope
        val factory: (Int) -> MyValue = { key -> MyValue(key) }
        val map = UsageTrackingMap(logger.logger, scope, removalDelay, fakeClock, factory)
        return map
    }

    inner class FakeClock : Clock() {
        private val zoneId: ZoneId = ZoneOffset.UTC

        var nowValue: Instant = Instant.ofEpochMilli(1_000_000)

        override fun instant() = nowValue

        override fun withZone(zone: ZoneId?): Clock {
            throw UnsupportedOperationException("FakeClock does not support custom zones")
        }

        override fun getZone(): ZoneId {
            return zoneId
        }
    }

    private class MyValue(val id: Int) {

        override fun toString(): String {
            return "MyValue($id)"
        }
    }

    private class CloseableScope : AutoCloseable {

        val scope = CoroutineScope(SupervisorJob())

        override fun close() {
            scope.cancel("Scope has been closed")
        }
    }

    private fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }
}
