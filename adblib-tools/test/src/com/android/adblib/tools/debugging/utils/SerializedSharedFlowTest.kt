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

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert
import org.junit.Test

class SerializedSharedFlowTest : AdbLibToolsTestBase() {

    @Test
    fun testEmitWorksForOneCollector() = runBlockingWithTimeout {
        // Prepare
        val flow = MutableSerializedSharedFlow<Int>()

        // Act
        val asyncValues = async(Dispatchers.Default) {
            flow.take(2).toList()
        }
        val asyncEmit = async(Dispatchers.Default) {
            // Wait until collector has started, then emit values
            flow.subscriptionCount.first { it == 1}
            flow.emit(5)
            flow.emit(6)
        }
        val values = asyncValues.await()
        asyncEmit.await()

        // Assert
        Assert.assertEquals(listOf(5, 6), values)
    }

    @Test
    fun testEmitWorksForManyCollectors() = runBlockingWithTimeout {
        // Prepare
        val flow = MutableSerializedSharedFlow<Int>()

        // Act
        val asyncValues = (1..500).map {
            async(Dispatchers.Default) {
                flow.take(2).toList()
            }
        }
        val asyncEmit = async(Dispatchers.Default) {
            // Wait until all collectors have started, then emit values
            flow.subscriptionCount.first { it == 500 }
            flow.emit(5)
            flow.emit(6)
        }
        val listOfValueList = asyncValues.awaitAll()
        asyncEmit.await()

        // Assert
        Assert.assertEquals(500, listOfValueList.size)
        listOfValueList.forEach { values ->
            Assert.assertEquals(listOf(5, 6), values)
        }
    }

    @Test
    fun testFlowEmitIsNoOpIfNoActiveCollector() = runBlockingWithTimeout {
        // Prepare
        val flow = MutableSerializedSharedFlow<Int>()

        // Act: emit complete right away since there are no collectors
        flow.emit(5)
        flow.emit(6)
    }

    @Test
    fun testCloseCancelsActiveCollectors() = runBlockingWithTimeout {
        // Prepare
        val flow = MutableSerializedSharedFlow<Int>()
        val jobs = (1..10).map {
            launch(Dispatchers.Default) {
                flow.collect {
                    // Nothing to do
                }
            }
        }

        // Act: "close" should cancel all running jobs
        flow.close()
        jobs.joinAll()
    }

    @Test
    fun testEmitWaitsForCollectorsToComplete() = runBlockingWithTimeout {
        // Prepare
        val flow = MutableSerializedSharedFlow<MixedIntWrapper>()
        val emitCount = 500
        val collectorCount = 50

        // Act
        val asyncEmit = async(Dispatchers.Default) {
            // Wait until all collectors have started
            flow.subscriptionCount.first { it == collectorCount }

            // We emit an object with an immutable "Int" field and a mutable one.
            // If "flow.emit" calls were not serialized correctly, it is very likely
            // one of the collector would receive non-matching values.
            val mutableInt = MutableIntWrapper()
            (1..emitCount).forEach { index ->
                mutableInt.mutableValue = index

                flow.emit(MixedIntWrapper(index, mutableInt))
            }
        }
        val asyncCollects = async(Dispatchers.Default) {
            (1..collectorCount).map {
                async(Dispatchers.Default) {
                    flow.take(emitCount).map {
                        Pair(it.value, it.mutableInt.mutableValue)
                    }.toList()
                }
            }.toList().awaitAll()
        }
        awaitAll(asyncEmit, asyncCollects)

        val listOfListOfCollectedPairs = asyncCollects.await()

        // Assert
        Assert.assertEquals(collectorCount, listOfListOfCollectedPairs.size)
        listOfListOfCollectedPairs.forEach { listOfPairs ->
            Assert.assertEquals(emitCount, listOfPairs.size)
            listOfPairs.forEach {
                Assert.assertEquals(
                    "A collector has received a mutable object too early or too late," +
                            "and the mutable value did not match the immutable value",
                    it.first , it.second)
            }
        }
    }

    @Test
    fun testReplayCacheWorks() = runBlockingWithTimeout {
        // Prepare
        val flow = MutableSerializedSharedFlow<Int>(2)

        // Act
        val asyncValues = async(Dispatchers.Default) {
            flow.take(2).toList()
        }
        val asyncEmit = async(Dispatchers.Default) {
            // Wait until collector has started, then emit values
            flow.subscriptionCount.first { it == 1}
            flow.emit(5)
            flow.emit(6)
        }
        asyncValues.await()
        asyncEmit.await()

        val replayCache = flow.take(2).toList()

        // Assert
        Assert.assertEquals(listOf(5, 6), replayCache)
    }

    @Test
    fun testSendIsAtomic(): Unit = runBlockingWithTimeout {
        // Prepare
        val flow = MutableSerializedSharedFlowForTesting<ValueToken>(onEmit = {
            // Add a delay between "emit" and "emit(SKIP)" so that we can reliably
            // interleave "emitterJob" and "concurrentEmitterJob"
            delay(200)
        })

        // Act
        // Note: The whole point of the test below is to interleave "emit" calls from
        // two concurrent jobs, so that code emitting and collecting a shared flow
        // concurrently can make assumptions about the serialization of calls (i.e.
        // A call to "emit" with a given value is guaranteed to terminate if a "collector"
        // receives the value emitted)
        val firstEmittedValue = ValueToken("'first value'")
        val secondEmittedValue = ValueToken("'second value'")
        val firstEmittedValueReceived = CompletableDeferred<Unit>()
        val firstValueEmitted = CompletableDeferred<Unit>()

        // Ensures "second value" is emitted after "first value" *and* collected by the
        // collector
        val concurrentEmitterJob = async(Dispatchers.Default) {
            // Delay a little so that "emitterJob" starts first
            delay(200)

            // Emit a non-interesting value
            flow.emit(secondEmittedValue)

            Assert.assertTrue("Value should have been received by collector", secondEmittedValue.wasCollected)
        }

        // Ensures "first value" is emitted first *and* collected by the collector
        val emitterJob = async(Dispatchers.Default) {
            // Delay a little bit to ensure "receiverJob" has started collecting
            // the shared flow (reminder: "emit" calls do nothing if there is no
            // active collected on a shared flow).
            delay(100)
            while (true) {
                // Send the value the receiver wants until the receiver
                // acknowledges it received it
                flow.emit(firstEmittedValue)
                Assert.assertTrue("Value should have been received by collector", firstEmittedValue.wasCollected)

                // If the shared flow is working as intended, "expectedValueReceived.isCompleted"
                // is guaranteed to be true if "receiverJob" observed the value (because the
                // shared flow "emit" calls are supposed to terminate only when the receivers
                // are done processing each value).
                if (firstEmittedValueReceived.isCompleted) {
                    firstValueEmitted.complete(Unit)
                    break
                }
            }
        }

        // Collect the share flow: the collector should see "first value" then "second value"
        // (in that order).
        val receiverJob = async(Dispatchers.Default) {
            flow.first { value ->
                value.wasCollected = true
                when (value) {
                    firstEmittedValue -> {
                        // Tell sender we got the value and keep going
                        firstEmittedValueReceived.complete(Unit)
                        false
                    }
                    secondEmittedValue -> {
                        // Wait for sender `emit` call to terminate and complete the deferred.
                        // If the `emit` call was not atomic, the sender could be "stuck" in
                        // its `emit` call and this wait would never terminate.
                        firstValueEmitted.await()
                        true
                    }
                    else -> {
                        Assert.fail("Unexpected value '$value' received from shared flow")
                        false
                    }
                }
            }
        }

        // Assert
        withTimeoutOrNull(5_000) {
            // Note: Using "await" ensures all jobs terminate, but also any
            // assertion failure/exception/cancellation is rethrown and reported as a test failure.
            awaitAll(emitterJob, receiverJob, concurrentEmitterJob)
        } ?: run {
            val message =  listOf(
                "All jobs did not terminate within a reasonable time, meaning ",
                "there is probably a deadlock exercised by this test code.\n",
                "  concurrentEmitterJob.isCompleted=${concurrentEmitterJob.isCompleted}\n",
                "  receiverJob.isCompleted=${receiverJob.isCompleted}\n",
                "  emitterJob.isCompleted=${emitterJob.isCompleted}\n"
            ).joinToString("")
            Assert.fail(message)
        }
    }

    class ValueToken(val text: String) {
        var wasCollected: Boolean = false

        override fun toString(): String {
            return "ValueToken(text=$text, wasCollected=$wasCollected)"
        }
    }

    private class MixedIntWrapper(val value: Int, val mutableInt: MutableIntWrapper)

    private class MutableIntWrapper(var mutableValue: Int = 0)
}
