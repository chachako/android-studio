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
package com.android.processmonitor.monitor

import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessTracker
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for [SafeProcessTracker]
 */
class SafeProcessTrackerTest {

    @Test
    fun trackerThrows_collectionSucceeds(): Unit = runBlocking {
        val tracker = TestProcessTracker(throws = true, ProcessAdded(1, "id", "name"))
        val safeProcessTracker = SafeProcessTracker(tracker, "Error", FakeAdbLoggerFactory().logger)

        assertThat(safeProcessTracker.trackProcesses().toList())
            .containsExactly(ProcessAdded(1, "id", "name"))
    }

    /**
     * Verifies that a merged throwing tracker does not terminate the merged flow.
     *
     * The test is not strictly needed because the contract of [merge] combined with the behavior
     * in [trackerThrows_collectionSucceeds] already guarantees this, but it serves to demonstrate
     * our use case.
     *
     */
    @Suppress("OPT_IN_USAGE") // runTest is experimental
    @Test
    fun mergedTrackers_oneThrowsOtherSucceeds_collectionSucceeds(): Unit = runTest {
        val tracker = TestProcessTracker(
            throws = false,
            ProcessAdded(1, "id1", "name1"),
            ProcessAdded(2, "id2", "name2")
        )
        val throwingTracker = TestProcessTracker(
            throws = true,
            ProcessAdded(3, "id3", "name3")
        )
        val safeTracker =
            SafeProcessTracker(throwingTracker, "Error", FakeAdbLoggerFactory().logger)

        val flow = merge(safeTracker.trackProcesses(), tracker.trackProcesses())

        assertThat(flow.toList()).containsExactly(
            ProcessAdded(1, "id1", "name1"),
            ProcessAdded(2, "id2", "name2"),
            ProcessAdded(3, "id3", "name3"),
        )
    }

    private class TestProcessTracker(
        private val throws: Boolean,
        vararg val events: ProcessEvent
    ) : ProcessTracker {

        override suspend fun trackProcesses(): Flow<ProcessEvent> {
            return flow {
                events.forEach {
                    emit(it)
                    delay(1000)
                }
                if (throws) {
                    throw RuntimeException()
                }
            }
        }
    }
}
