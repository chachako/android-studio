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
package com.android.adblib.tools.debugging.utils

import com.android.adblib.AutoShutdown
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.yield
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.cancellation.CancellationException

class ReferenceCountedFactoryTest : AdbLibToolsTestBase() {

    @Test
    fun testRetainWorks() = runBlockingWithTimeout {
        // Prepare
        var callCount = 0
        val factory = ReferenceCountedFactory {
            callCount++
            MutableIntWrapper(5)
        }

        // Act
        val value = factory.retain()

        // Assert
        assertEquals(5, value.mutableValue)
        assertEquals(1, callCount)
    }

    @Test
    fun testRetainTwiceReturnsSameInstance() = runBlockingWithTimeout {
        // Prepare
        val autoShutdownThingTestFactory = AutoShutdownThingTestFactory()
        val factory = ReferenceCountedFactory(autoShutdownThingTestFactory::create)

        // Act
        val value1 = factory.retain()
        val value2 = factory.retain()

        // Assert
        assertEquals(1, autoShutdownThingTestFactory.instances.size)
        assertSame(value1, value2)
    }

    @Test
    fun testRetainAfterReleaseReturnsNewInstance() = runBlockingWithTimeout {
        // Prepare
        val autoShutdownThingTestFactory = AutoShutdownThingTestFactory()
        val factory = ReferenceCountedFactory(autoShutdownThingTestFactory::create)

        // Act
        val value1 = factory.retain()
        factory.release(value1)
        val value2 = factory.retain()

        // Assert
        assertEquals(2, autoShutdownThingTestFactory.instances.size)
        assertNotSame(value1, value2)
    }

    @Test
    fun testReleaseCallsClose() = runBlockingWithTimeout {
        // Prepare
        val factory = ReferenceCountedFactory {
            CloseableThing()
        }

        // Act
        val value = factory.retain()
        factory.release(value)

        // Assert
        assertEquals(1, value.closedCallCount)
    }

    @Test
    fun testReleaseCallsShutdownAndClose() = runBlockingWithTimeout {
        // Prepare
        val autoShutdownThingTestFactory = AutoShutdownThingTestFactory()
        val factory = ReferenceCountedFactory(autoShutdownThingTestFactory::create)

        // Act
        val value = factory.retain()
        factory.release(value)

        // Assert
        assertEquals(1, value.closedCallCount)
        assertEquals(1, value.shutdownCallCount)
    }

    @Test
    fun testRetainIsTransparentToException() = runBlockingWithTimeout {
        // Prepare
        val factory = ReferenceCountedFactory {
            throw MyException("foo")
        }

        // Act
        exceptionRule.expect(MyException::class.java)
        exceptionRule.expectMessage("foo")
        factory.retain()

        // Assert
        fail("Should not reach")
    }

    @Test
    fun testWithResourceIsTransparentToException(): Unit = runBlockingWithTimeout {
        // Prepare
        val autoShutdownThingTestFactory = AutoShutdownThingTestFactory()
        val factory = ReferenceCountedFactory(autoShutdownThingTestFactory::create)

        // Act
        var actionCalled = false
        val r = kotlin.runCatching {
            factory.withResource<Unit> {
                actionCalled = true
                throw MyException("foo")
            }
        }

        // Assert
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull() is MyException)
        assertEquals(1, autoShutdownThingTestFactory.instances.size)
        assertEquals(true, actionCalled)

    }

    @Test
    fun testWithResourceIsTransparentToCancellationException(): Unit = runBlockingWithTimeout {
        // Prepare
        val autoShutdownThingTestFactory = AutoShutdownThingTestFactory()
        val factory = ReferenceCountedFactory(autoShutdownThingTestFactory::create)

        // Act
        var actionCalled = false
        val r = kotlin.runCatching {
            factory.withResource<Unit> {
                actionCalled = true
                throw CancellationException("foo")
            }
        }

        // Assert
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull() is CancellationException)
        assertEquals(1, autoShutdownThingTestFactory.instances.size)
        assertEquals(0, autoShutdownThingTestFactory.instances.first.shutdownCallCount)
        assertEquals(1, autoShutdownThingTestFactory.instances.first.closedCallCount)
        assertEquals(true, actionCalled)
    }

    @Test
    fun testWithResourceIsTransparentToJobCancellationException(): Unit = runBlockingWithTimeout {
        // Prepare
        val autoShutdownThingTestFactory = AutoShutdownThingTestFactory()
        val factory = ReferenceCountedFactory(autoShutdownThingTestFactory::create)

        // Act
        var actionCalled = false
        val started = CompletableDeferred<AutoShutdownThing>()
        val job = async {
            factory.withResource {
                actionCalled = true
                started.complete(it)
                delay(500_000L)
            }
        }
        started.await()
        job.cancel("My Cancellation")
        val r = kotlin.runCatching { job.await() }

        // Assert
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull() is CancellationException)
        assertEquals(1, autoShutdownThingTestFactory.instances.size)
        assertEquals(0, autoShutdownThingTestFactory.instances.first.shutdownCallCount)
        assertEquals(1, autoShutdownThingTestFactory.instances.first.closedCallCount)
        assertEquals(true, actionCalled)

    }

    @Test
    fun testReleaseIsTransparentToShutdownException() = runBlockingWithTimeout {
        // Prepare
        val autoShutdownThingTestFactory = AutoShutdownThingTestFactory()
        val factory = ReferenceCountedFactory {
            autoShutdownThingTestFactory.create(shutdownAction = {
                throw MyException("bar")
            })
        }

        // Act
        val value = factory.retain()
        exceptionRule.expect(MyException::class.java)
        exceptionRule.expectMessage("bar")
        factory.release(value)

        // Assert
        fail("Should not reach")
    }

    @Test
    fun testReleaseDoesNotCacheShutdownException() = runBlockingWithTimeout {
        // Prepare
        var callCount = 0
        val autoShutdownThingTestFactory = AutoShutdownThingTestFactory()
        val factory = ReferenceCountedFactory {
            val myCallCount = callCount
            callCount++
            autoShutdownThingTestFactory.create(shutdownAction =  {
                if (myCallCount == 0) {
                    throw MyException("bar")
                } else {
                    // Nothing to do
                }
            })
        }

        // Act
        val value1 = factory.retain()
        val r1 = runCatching { factory.release(value1) }
        val value2 = factory.retain()
        factory.release(value2)

        // Assert
        assertEquals(2, callCount)
        assertTrue(r1.isFailure)
        assertTrue(r1.exceptionOrNull() is MyException)
    }

    @Test
    fun testRetainDoesNotCacheException() = runBlockingWithTimeout {
        // Prepare
        var callCount = 0
        val factory = ReferenceCountedFactory {
            callCount++
            throw MyException("foo")
        }

        // Act
        val r1 = runCatching { factory.retain() }
        val r2 = runCatching { factory.retain() }

        // Assert
        assertTrue(r1.isFailure)
        assertTrue(r1.exceptionOrNull() is MyException)
        assertTrue(r2.isFailure)
        assertTrue(r2.exceptionOrNull() is MyException)
        assertEquals(2, callCount)
    }

    @Test
    fun testCloseCancelsPendingRetain(): Unit = runBlockingWithTimeout {
        // Prepare
        val deferredStart = CompletableDeferred<Unit>()
        val autoShutdownThingTestFactory = AutoShutdownThingTestFactory()
        val factory = ReferenceCountedFactory {
            deferredStart.complete(Unit)
            delay(100_000L)
            autoShutdownThingTestFactory.create()
        }

        // Act
        val deferredRetain = async {
            factory.retain()
        }
        deferredStart.await()
        factory.close()
        val r1 = kotlin.runCatching { deferredRetain.await() }

        // Assert
        assertTrue(r1.isFailure)
        assertTrue(r1.exceptionOrNull() is CancellationException)
        assertTrue(autoShutdownThingTestFactory.instances.isEmpty())
    }

    @Test
    fun testCloseCancelsPendingReleaseAndCallsClose(): Unit = runBlockingWithTimeout {
        // Prepare
        val deferredStart = CompletableDeferred<AutoShutdownThing>()
        val deferredShutdown = CompletableDeferred<Unit>()
        val factory = ReferenceCountedFactory {
            AutoShutdownThing(shutdownAction = {
                deferredShutdown.complete(Unit)
                delay(100_000L)
            }).also {
                deferredStart.complete(it)
            }
        }

        // Act
        val deferredRetain = async {
            val value = factory.retain()
            factory.release(value)
        }
        val autoShutdownThing = deferredStart.await()
        deferredShutdown.await()
        factory.close()
        val r1 = kotlin.runCatching { deferredRetain.await() }

        // Assert
        assertTrue(r1.isFailure)
        assertTrue(r1.exceptionOrNull() is CancellationException)
        assertEquals(1, autoShutdownThing.shutdownCallCount)
        assertEquals(1, autoShutdownThing.closedCallCount)
    }

    @Test
    fun testRetainAfterCloseIsCancelled(): Unit = runBlockingWithTimeout {
        // Prepare
        val factory = ReferenceCountedFactory {
            AutoShutdownThing()
        }

        // Act
        factory.close()

        exceptionRule.expect(CancellationException::class.java)
        factory.retain()

        // Assert
        fail("Should not reach")
    }

    @Test
    fun testReleaseAfterCloseIsCancelled(): Unit = runBlockingWithTimeout {
        // Prepare
        val factory = ReferenceCountedFactory {
            AutoShutdownThing()
        }

        // Act
        val value = factory.retain()
        factory.close()

        exceptionRule.expect(CancellationException::class.java)
        factory.release(value)

        // Assert
        fail("Should not reach")
    }

    @Test
    fun testRetainReleaseIsThreadSafe() = runBlockingWithTimeout {
        // Prepare
        val autoShutdownThingTestFactory = AutoShutdownThingTestFactory()
        val factory = ReferenceCountedFactory {
            autoShutdownThingTestFactory.create()
        }

        // Act: Call `retain` 500 times concurrently, then call `release` 500 times concurrently
        val asyncValues = (1..500).map {
            async(Dispatchers.Default) {
                factory.retain()
            }
        }
        val asyncReleases = asyncValues.awaitAll().map { value ->
            async(Dispatchers.Default) {
                factory.release(value)
            }
        }
        asyncReleases.awaitAll()

        // Assert
        assertEquals(1, autoShutdownThingTestFactory.instances.size)
        assertEquals(1, autoShutdownThingTestFactory.instances.first.shutdownCallCount)
        assertEquals(1, autoShutdownThingTestFactory.instances.first.closedCallCount)
    }

    private class MutableIntWrapper(var mutableValue: Int = 0)

    private class CloseableThing : AutoCloseable {

        var closedCallCount = 0

        override fun close() {
            closedCallCount++
        }
    }

    private class AutoShutdownThing(private val shutdownAction: suspend () -> Unit = {}) : AutoShutdown {

        var closedCallCount = 0
        var shutdownCallCount = 0

        override suspend fun shutdown() {
            shutdownCallCount++
            shutdownAction()
        }

        override fun close() {
            closedCallCount++
        }
    }

    private class AutoShutdownThingTestFactory {
        val instances = ConcurrentLinkedDeque<AutoShutdownThing>()

        fun create(shutdownAction: suspend () -> Unit = {}): AutoShutdownThing {
            return AutoShutdownThing(shutdownAction).also {
                instances.addLast(it)
            }
        }
    }

    private class MyException(override val message: String) : Exception()
}
