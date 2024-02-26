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
package com.android.utils.cache

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ChangeTrackerCachedValueTest {
  @Test
  fun testChangeTracker() = runBlocking {
    val counter = AtomicInteger(0)
    val tracker = AtomicLong(0)
    val value = ChangeTrackerCachedValue.strongReference<String>()
    val provider = {
      counter.getAndIncrement().toString()
    }
    val trackerProvider = { tracker.get() }
    assertEquals("0", ChangeTrackerCachedValue.get(value, provider, trackerProvider))
    assertEquals("0", ChangeTrackerCachedValue.get(value, provider, trackerProvider))
    tracker.incrementAndGet() // Invalidate the value
    assertEquals("1", ChangeTrackerCachedValue.get(value, provider, trackerProvider))
  }

  @Test
  fun testNeverChanging() = runBlocking {
    val counter = AtomicInteger(0)
    val value = ChangeTrackerCachedValue.strongReference<String>()
    val provider = {
      counter.getAndIncrement().toString()
    }
    assertEquals("0", ChangeTrackerCachedValue.get(value, provider, ChangeTracker.NEVER_CHANGE))
    assertEquals("0", ChangeTrackerCachedValue.get(value, provider, ChangeTracker.NEVER_CHANGE))
    assertEquals("0", ChangeTrackerCachedValue.get(value, provider, ChangeTracker.NEVER_CHANGE))
  }

  @Test
  fun testEverChanging() = runBlocking {
    val counter = AtomicInteger(0)
    val value = ChangeTrackerCachedValue.strongReference<String>()
    val provider = suspend {
      delay(350) // Simulate a low provider
      counter.getAndIncrement().toString()
    }
    assertEquals("0", ChangeTrackerCachedValue.get(value, provider, ChangeTracker.EVER_CHANGING))
    assertEquals("1", ChangeTrackerCachedValue.get(value, provider, ChangeTracker.EVER_CHANGING))
    assertEquals("2", ChangeTrackerCachedValue.get(value, provider, ChangeTracker.EVER_CHANGING))
  }
}
