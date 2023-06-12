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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

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

    private class MixedIntWrapper(val value: Int, val mutableInt: MutableIntWrapper)

    private class MutableIntWrapper(var mutableValue: Int = 0)
}
